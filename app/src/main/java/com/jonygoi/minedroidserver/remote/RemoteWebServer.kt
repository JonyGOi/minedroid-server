package com.jonygoi.minedroidserver.remote

import android.content.Context
import android.net.wifi.WifiManager
import com.jonygoi.minedroidserver.server.ServerInstaller
import com.jonygoi.minedroidserver.server.ServerManager
import com.jonygoi.minedroidserver.server.ServerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.*
import java.util.*

object RemoteWebServer {
    private var serverSocket: ServerSocket? = null
    private var serverScope: CoroutineScope? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl = _serverUrl.asStateFlow()

    private var appContext: Context? = null

    fun start(context: Context, defaultPort: Int = 8080) {
        if (_isRunning.value) return
        appContext = context.applicationContext

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        serverScope = scope

        scope.launch {
            var port = defaultPort
            var ss: ServerSocket? = null
            while (port < defaultPort + 10 && ss == null) {
                try {
                    ss = ServerSocket(port)
                } catch (_: Exception) {
                    port++
                }
            }

            if (ss == null) {
                return@launch
            }

            serverSocket = ss
            val localIp = getLocalIpAddress(context)
            val url = "http://$localIp:$port"
            _serverUrl.value = url
            _isRunning.value = true

            try {
                while (isActive) {
                    val client = try {
                        ss.accept()
                    } catch (_: Exception) {
                        break
                    }
                    scope.launch(Dispatchers.IO) {
                        handleClient(client)
                    }
                }
            } finally {
                _isRunning.value = false
                _serverUrl.value = null
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverScope?.cancel()
        serverScope = null
        _isRunning.value = false
        _serverUrl.value = null
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 10000
            val input = client.getInputStream()
            val reader = BufferedReader(InputStreamReader(input))
            val line = reader.readLine() ?: run {
                client.close()
                return
            }

            val parts = line.split(" ")
            if (parts.size < 2) {
                client.close()
                return
            }

            val method = parts[0].uppercase()
            val fullPath = parts[1]
            val path = fullPath.substringBefore("?")
            val queryString = if (fullPath.contains("?")) fullPath.substringAfter("?") else ""

            val headers = mutableMapOf<String, String>()
            var headerLine: String?
            var contentLength = 0
            while (reader.readLine().also { headerLine = it } != null) {
                if (headerLine.isNullOrBlank()) break
                val colonIdx = headerLine!!.indexOf(":")
                if (colonIdx != -1) {
                    val key = headerLine!!.substring(0, colonIdx).trim().lowercase()
                    val value = headerLine!!.substring(colonIdx + 1).trim()
                    headers[key] = value
                    if (key == "content-length") {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
            }

            var body = ""
            if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = reader.read(buf, read, contentLength - read)
                    if (r == -1) break
                    read += r
                }
                body = String(buf, 0, read)
            }

            val output = client.getOutputStream()

            if (method == "OPTIONS") {
                sendResponse(output, 204, "No Content", "text/plain", "")
                client.close()
                return
            }

            when {
                path == "/" || path == "/index.html" -> {
                    val html = renderDashboardHtml()
                    sendResponse(output, 200, "OK", "text/html; charset=utf-8", html)
                }
                path == "/api/status" -> {
                    val json = getStatusJson()
                    sendResponse(output, 200, "OK", "application/json; charset=utf-8", json)
                }
                path == "/api/action" && method == "POST" -> {
                    val action = parseParam(body, "action")
                    val ctx = appContext
                    if (ctx != null) {
                        when (action) {
                            "start" -> ServerManager.startServer(ctx)
                            "stop" -> ServerManager.stopServer(ctx)
                            "restart" -> ServerManager.restartServer(ctx)
                        }
                    }
                    sendResponse(output, 200, "OK", "application/json", """{"success":true,"action":"$action"}""")
                }
                path == "/api/command" && method == "POST" -> {
                    val cmd = parseParam(body, "command")
                    if (cmd.isNotBlank()) {
                        ServerManager.sendCommand(cmd)
                    }
                    sendResponse(output, 200, "OK", "application/json", """{"success":true,"command":"$cmd"}""")
                }
                path == "/api/files" -> {
                    val json = getFilesJson()
                    sendResponse(output, 200, "OK", "application/json; charset=utf-8", json)
                }
                path == "/api/file" && method == "GET" -> {
                    val filePath = parseQueryParam(queryString, "path")
                    val content = readFileContent(filePath)
                    sendResponse(output, 200, "OK", "text/plain; charset=utf-8", content)
                }
                path == "/api/file" && method == "POST" -> {
                    val filePath = parseParam(body, "path")
                    val fileContent = parseParam(body, "content")
                    val success = saveFileContent(filePath, fileContent)
                    sendResponse(output, 200, "OK", "application/json", """{"success":$success}""")
                }
                else -> {
                    sendResponse(output, 404, "Not Found", "text/plain", "404 Not Found")
                }
            }
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun sendResponse(output: OutputStream, code: Int, status: String, contentType: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $code $status\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun parseParam(body: String, key: String): String {
        try {
            if (body.trim().startsWith("{")) {
                val json = JSONObject(body)
                return json.optString(key, "")
            }
        } catch (_: Exception) {}

        // Fallback x-www-form-urlencoded
        val pairs = body.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx != -1 && pair.substring(0, idx).trim() == key) {
                return URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            }
        }
        return ""
    }

    private fun parseQueryParam(query: String, key: String): String {
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx != -1 && pair.substring(0, idx).trim() == key) {
                return URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            }
        }
        return ""
    }

    private fun getStatusJson(): String {
        val cfg = ServerManager.getActiveConfig()
        val state = ServerManager.state.value.name
        val logs = ServerManager.logs.value.takeLast(80)

        val obj = JSONObject()
        obj.put("state", state)
        obj.put("serverName", cfg?.name ?: "Nenhum")
        obj.put("serverType", cfg?.serverType?.displayName ?: "Desconhecido")
        obj.put("version", cfg?.version ?: "-")
        obj.put("ram", cfg?.allocatedRamMb ?: 0)
        obj.put("javaAddress", cfg?.friendlyJavaAddress ?: "Desativado")
        obj.put("bedrockAddress", cfg?.friendlyBedrockAddress ?: "Desativado")
        obj.put("localIp", appContext?.let { getLocalIpAddress(it) } ?: "127.0.0.1")
        obj.put("javaPort", cfg?.serverPort ?: 25565)
        obj.put("bedrockPort", cfg?.geyserPort ?: 19132)

        val logsArr = JSONArray()
        logs.forEach { log ->
            val lObj = JSONObject()
            lObj.put("time", log.formattedTime)
            lObj.put("message", log.text)
            lObj.put("type", log.type.name)
            logsArr.put(lObj)
        }
        obj.put("logs", logsArr)

        return obj.toString()
    }

    private fun getFilesJson(): String {
        val ctx = appContext ?: return """{"files":[]}"""
        val cfg = ServerManager.getActiveConfig() ?: return """{"files":[]}"""
        val dir = ServerInstaller.getServerDirectory(ctx, cfg.id)
        val arr = JSONArray()

        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))?.forEach { f ->
                val fObj = JSONObject()
                fObj.put("name", f.name)
                fObj.put("isDir", f.isDirectory)
                fObj.put("size", if (f.isDirectory) 0L else f.length())
                fObj.put("modified", f.lastModified())
                arr.put(fObj)
            }
        }

        val res = JSONObject()
        res.put("files", arr)
        res.put("serverName", cfg.name)
        return res.toString()
    }

    private fun readFileContent(relPath: String): String {
        val ctx = appContext ?: return ""
        val cfg = ServerManager.getActiveConfig() ?: return ""
        val dir = ServerInstaller.getServerDirectory(ctx, cfg.id)
        val safe = File(dir, relPath.replace("..", ""))
        return if (safe.exists() && safe.isFile && safe.length() < 2 * 1024 * 1024) {
            try { safe.readText() } catch (_: Exception) { "Erro ao ler arquivo." }
        } else {
            "Arquivo não encontrado ou muito grande para exibição."
        }
    }

    private fun saveFileContent(relPath: String, content: String): Boolean {
        val ctx = appContext ?: return false
        val cfg = ServerManager.getActiveConfig() ?: return false
        val dir = ServerInstaller.getServerDirectory(ctx, cfg.id)
        val safe = File(dir, relPath.replace("..", ""))
        return try {
            safe.parentFile?.mkdirs()
            safe.writeText(content)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getLocalIpAddress(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                val ipInt = wifiManager.connectionInfo.ipAddress
                if (ipInt != 0) {
                    return String.format(
                        Locale.US,
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                }
            }
        } catch (_: Exception) {}

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotBlank() && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    private fun renderDashboardHtml(): String {
        return """
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>MineDroid Server - Painel Remoto</title>
    <style>
        :root {
            --bg-dark: #0f141c;
            --card-bg: #18202c;
            --card-border: #263346;
            --emerald: #10b981;
            --red: #ef4444;
            --gold: #f59e0b;
            --blue: #3b82f6;
            --text-main: #f1f5f9;
            --text-muted: #94a3b8;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
        body { background-color: var(--bg-dark); color: var(--text-main); min-height: 100vh; padding: 16px; }
        .container { max-width: 900px; margin: 0 auto; display: flex; flex-direction: column; gap: 16px; }
        header { display: flex; align-items: center; justify-content: space-between; padding: 12px 18px; background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 12px; }
        .logo-area { display: flex; align-items: center; gap: 10px; }
        .logo-icon { font-size: 24px; }
        h1 { font-size: 18px; font-weight: 700; color: #fff; }
        .badge { padding: 4px 10px; border-radius: 20px; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; }
        .badge-running { background: rgba(16, 185, 129, 0.2); color: var(--emerald); border: 1px solid var(--emerald); }
        .badge-stopped { background: rgba(239, 68, 68, 0.2); color: var(--red); border: 1px solid var(--red); }
        .badge-starting { background: rgba(245, 158, 11, 0.2); color: var(--gold); border: 1px solid var(--gold); }
        
        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; }
        .card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 12px; padding: 16px; display: flex; flex-direction: column; gap: 8px; }
        .card-title { font-size: 12px; text-transform: uppercase; font-weight: 600; color: var(--text-muted); }
        .card-value { font-size: 15px; font-weight: 700; color: #fff; word-break: break-all; font-family: monospace; }
        
        .actions-row { display: flex; gap: 10px; flex-wrap: wrap; }
        button { cursor: pointer; border: none; outline: none; padding: 10px 16px; border-radius: 8px; font-weight: 700; font-size: 13px; display: inline-flex; align-items: center; justify-content: center; gap: 6px; transition: opacity 0.2s; }
        button:hover { opacity: 0.9; }
        button:active { transform: scale(0.98); }
        .btn-start { background: var(--emerald); color: #042f2e; }
        .btn-stop { background: var(--red); color: #fff; }
        .btn-restart { background: var(--gold); color: #451a03; }
        .btn-secondary { background: #263346; color: var(--text-main); }

        .terminal-card { background: #070a0e; border: 1px solid var(--card-border); border-radius: 12px; overflow: hidden; display: flex; flex-direction: column; height: 420px; }
        .terminal-header { background: #121822; padding: 8px 14px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--card-border); }
        .terminal-body { flex: 1; padding: 12px; overflow-y: auto; font-family: 'Consolas', 'Courier New', monospace; font-size: 12px; line-height: 1.5; color: #cbd5e1; white-space: pre-wrap; word-break: break-all; }
        .cmd-form { display: flex; gap: 8px; padding: 8px 12px; background: #121822; border-top: 1px solid var(--card-border); }
        .cmd-input { flex: 1; background: #070a0e; border: 1px solid var(--card-border); border-radius: 6px; padding: 8px 12px; color: #fff; font-family: monospace; font-size: 13px; outline: none; }
        .cmd-input:focus { border-color: var(--emerald); }

        .quick-cmds { display: flex; gap: 6px; padding: 8px 12px; background: #0c1017; overflow-x: auto; border-top: 1px solid rgba(255,255,255,0.05); }
        .quick-btn { background: #182230; color: #94a3b8; font-size: 11px; padding: 4px 8px; border-radius: 4px; }
        .quick-btn:hover { color: #fff; background: #223044; }

        .nav-tabs { display: flex; gap: 8px; border-bottom: 1px solid var(--card-border); padding-bottom: 8px; }
        .tab-btn { background: transparent; color: var(--text-muted); padding: 8px 14px; border-radius: 8px; font-weight: 600; }
        .tab-btn.active { background: #223044; color: #fff; }
        .tab-content { display: none; }
        .tab-content.active { display: flex; flex-direction: column; gap: 16px; }

        .file-list { display: flex; flex-direction: column; gap: 4px; max-height: 350px; overflow-y: auto; }
        .file-item { display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; background: #121822; border-radius: 6px; font-size: 13px; font-family: monospace; cursor: pointer; }
        .file-item:hover { background: #1c2635; }
        .file-viewer { display: none; flex-direction: column; gap: 8px; margin-top: 10px; }
        .file-editor { width: 100%; height: 250px; background: #070a0e; border: 1px solid var(--card-border); border-radius: 6px; color: #cbd5e1; padding: 10px; font-family: monospace; font-size: 12px; outline: none; }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <div class="logo-area">
                <span class="logo-icon">⛏️</span>
                <div>
                    <h1 id="srv-name">MineDroid Server</h1>
                    <div style="font-size: 11px; color: var(--text-muted);" id="srv-version">Carregando...</div>
                </div>
            </div>
            <div id="status-badge" class="badge badge-stopped">Offline</div>
        </header>

        <div class="nav-tabs">
            <button class="tab-btn active" onclick="switchTab('dashboard')">📊 Painel Principal</button>
            <button class="tab-btn" onclick="switchTab('files')">📁 Arquivos do Servidor</button>
        </div>

        <div id="tab-dashboard" class="tab-content active">
            <div class="actions-row">
                <button class="btn-start" onclick="execAction('start')">▶️ Iniciar Servidor</button>
                <button class="btn-stop" onclick="execAction('stop')">⏹️ Parar Servidor</button>
                <button class="btn-restart" onclick="execAction('restart')">🔄 Reiniciar</button>
            </div>

            <div class="grid">
                <div class="card">
                    <span class="card-title">Endereço Java (PC / Pojav)</span>
                    <span class="card-value" id="addr-java">-</span>
                </div>
                <div class="card">
                    <span class="card-title">Endereço Bedrock (Celular / Console)</span>
                    <span class="card-value" id="addr-bedrock">-</span>
                </div>
                <div class="card">
                    <span class="card-title">Rede Local (Wi-Fi)</span>
                    <span class="card-value" id="addr-local">-</span>
                </div>
                <div class="card">
                    <span class="card-title">Memória RAM</span>
                    <span class="card-value" id="stat-ram">-</span>
                </div>
            </div>

            <div class="terminal-card">
                <div class="terminal-header">
                    <span style="font-size: 12px; font-weight: 700; color: #94a3b8;">TERMINAL DO CONSOLE</span>
                    <button class="btn-secondary" style="padding: 4px 8px; font-size: 11px;" onclick="fetchStatus()">🔄 Atualizar</button>
                </div>
                <div class="terminal-body" id="console-logs">Aguardando dados do servidor...</div>
                <div class="quick-cmds">
                    <span style="font-size: 10px; color: #64748b; align-self: center;">Atalhos:</span>
                    <button class="quick-btn" onclick="sendQuickCmd('list')">/list</button>
                    <button class="quick-btn" onclick="sendQuickCmd('tps')">/tps</button>
                    <button class="quick-btn" onclick="sendQuickCmd('time set day')">/time set day</button>
                    <button class="quick-btn" onclick="sendQuickCmd('weather clear')">/weather clear</button>
                    <button class="quick-btn" onclick="sendQuickCmd('whitelist off')">/whitelist off</button>
                </div>
                <form class="cmd-form" onsubmit="event.preventDefault(); sendConsoleCommand();">
                    <input type="text" id="cmd-input" class="cmd-input" placeholder="Digite um comando do Minecraft (ex: op Steve, gamemode creative...)" autocomplete="off">
                    <button type="submit" class="btn-start" style="padding: 8px 16px;">Enviar</button>
                </form>
            </div>
        </div>

        <div id="tab-files" class="tab-content">
            <div class="card">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
                    <span class="card-title">Arquivos da Pasta do Servidor</span>
                    <button class="btn-secondary" style="padding: 4px 8px; font-size: 11px;" onclick="loadFiles()">🔄 Atualizar Arquivos</button>
                </div>
                <div id="file-list" class="file-list">Carregando lista de arquivos...</div>
                
                <div id="file-viewer" class="file-viewer">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <span id="current-filename" style="font-weight: 700; font-family: monospace; font-size: 13px;"></span>
                        <button class="btn-start" style="padding: 6px 12px; font-size: 12px;" onclick="saveCurrentFile()">💾 Salvar Arquivo</button>
                    </div>
                    <textarea id="file-editor" class="file-editor" spellcheck="false"></textarea>
                </div>
            </div>
        </div>
    </div>

    <script>
        let currentEditingPath = "";

        function switchTab(tab) {
            document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
            document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
            if (tab === 'dashboard') {
                document.querySelectorAll('.tab-btn')[0].classList.add('active');
                document.getElementById('tab-dashboard').classList.add('active');
            } else {
                document.querySelectorAll('.tab-btn')[1].classList.add('active');
                document.getElementById('tab-files').classList.add('active');
                loadFiles();
            }
        }

        async function fetchStatus() {
            try {
                const res = await fetch('/api/status');
                const data = await res.json();
                
                document.getElementById('srv-name').textContent = data.serverName || 'MineDroid Server';
                document.getElementById('srv-version').textContent = (data.serverType || '') + ' ' + (data.version || '');
                
                const badge = document.getElementById('status-badge');
                badge.className = 'badge';
                if (data.state === 'RUNNING') {
                    badge.classList.add('badge-running');
                    badge.textContent = 'Em Execução';
                } else if (data.state === 'STARTING') {
                    badge.classList.add('badge-starting');
                    badge.textContent = 'Iniciando...';
                } else {
                    badge.classList.add('badge-stopped');
                    badge.textContent = 'Parado';
                }

                document.getElementById('addr-java').textContent = data.javaAddress || '-';
                document.getElementById('addr-bedrock').textContent = data.bedrockAddress || '-';
                document.getElementById('addr-local').textContent = (data.localIp || '') + ':' + (data.bedrockPort || 19132) + ' (Bedrock) / ' + (data.javaPort || 25565) + ' (Java)';
                document.getElementById('stat-ram').textContent = (data.ram || 0) + ' MB';

                const term = document.getElementById('console-logs');
                if (data.logs && data.logs.length > 0) {
                    const atBottom = (term.scrollHeight - term.scrollTop - term.clientHeight) < 60;
                    term.innerHTML = data.logs.map(l => {
                        let color = '#cbd5e1';
                        if (l.type === 'ERROR') color = '#f87171';
                        else if (l.type === 'WARN') color = '#fbbf24';
                        else if (l.type === 'SYSTEM') color = '#34d399';
                        return '<span style="color:' + color + '">[' + l.time + '] ' + escapeHtml(l.message) + '</span>';
                    }).join('\n');
                    if (atBottom) {
                        term.scrollTop = term.scrollHeight;
                    }
                } else {
                    term.textContent = 'Nenhum log registrado ainda.';
                }
            } catch (e) {
                console.error(e);
            }
        }

        function escapeHtml(text) {
            return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
        }

        async function execAction(action) {
            if (!confirm('Deseja realmente executar a ação: ' + action + '?')) return;
            try {
                await fetch('/api/action', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action })
                });
                fetchStatus();
            } catch (e) { alert('Erro ao executar ação: ' + e); }
        }

        async function sendConsoleCommand() {
            const input = document.getElementById('cmd-input');
            const cmd = input.value.trim();
            if (!cmd) return;
            input.value = '';
            try {
                await fetch('/api/command', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ command: cmd })
                });
                fetchStatus();
            } catch (e) { alert('Erro ao enviar comando: ' + e); }
        }

        function sendQuickCmd(cmd) {
            document.getElementById('cmd-input').value = cmd;
            sendConsoleCommand();
        }

        async function loadFiles() {
            const listEl = document.getElementById('file-list');
            try {
                listEl.textContent = 'Carregando arquivos...';
                const res = await fetch('/api/files');
                const data = await res.json();
                if (!data.files || data.files.length === 0) {
                    listEl.textContent = 'Nenhum arquivo encontrado.';
                    return;
                }
                listEl.innerHTML = data.files.map(f => {
                    const icon = f.isDir ? '📁' : '📄';
                    const sizeStr = f.isDir ? 'pasta' : (f.size > 1024 ? Math.round(f.size/1024) + ' KB' : f.size + ' B');
                    return '<div class="file-item" onclick="openFile(\'' + f.name + '\', ' + f.isDir + ')">' +
                           '<span>' + icon + ' ' + escapeHtml(f.name) + '</span>' +
                           '<span style="color:#64748b; font-size:11px;">' + sizeStr + '</span>' +
                           '</div>';
                }).join('');
            } catch (e) {
                listEl.textContent = 'Erro ao listar arquivos: ' + e;
            }
        }

        async function openFile(name, isDir) {
            if (isDir) {
                alert('Navegação em subpastas em breve disponível.');
                return;
            }
            currentEditingPath = name;
            try {
                const res = await fetch('/api/file?path=' + encodeURIComponent(name));
                const text = await res.text();
                document.getElementById('current-filename').textContent = name;
                document.getElementById('file-editor').value = text;
                document.getElementById('file-viewer').style.display = 'flex';
                document.getElementById('file-viewer').scrollIntoView({ behavior: 'smooth' });
            } catch (e) {
                alert('Erro ao abrir arquivo: ' + e);
            }
        }

        async function saveCurrentFile() {
            if (!currentEditingPath) return;
            const content = document.getElementById('file-editor').value;
            try {
                const res = await fetch('/api/file', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ path: currentEditingPath, content })
                });
                const data = await res.json();
                if (data.success) {
                    alert('Arquivo salvo com sucesso!');
                } else {
                    alert('Falha ao salvar o arquivo.');
                }
            } catch (e) {
                alert('Erro ao salvar: ' + e);
            }
        }

        fetchStatus();
        setInterval(fetchStatus, 2000);
    </script>
</body>
</html>
        """.trimIndent()
    }
}
