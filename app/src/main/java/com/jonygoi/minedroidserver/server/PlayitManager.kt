package com.jonygoi.minedroidserver.server

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Random

data class PlayitClaimSession(
    val code: String,
    val claimUrl: String
)

enum class PlayitClaimStatus {
    WAITING_FOR_BROWSER,
    WAITING_FOR_USER_CLICK,
    ACCEPTED,
    REJECTED,
    ERROR
}

object PlayitManager {

    private const val PREFS_NAME = "minedroid_playit"
    private const val KEY_SECRET = "playit_secret_key"
    private const val KEY_LAST_DOMAIN = "playit_last_domain"

    private val pollerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activePollerJob: Job? = null

    val liveClaimUrl = MutableStateFlow<String?>(null)
    val agentUrl = MutableStateFlow<String?>(null)

    fun setLiveClaimUrl(url: String?) {
        liveClaimUrl.value = url
    }

    fun setAgentUrl(url: String?) {
        agentUrl.value = url
    }

    fun getAgentUrl(): String? = agentUrl.value

    fun resetSecret(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SECRET).remove(KEY_LAST_DOMAIN).apply()
        liveClaimUrl.value = null

        try {
            val playitDir = File(context.filesDir, "playit")
            val configDirs = listOf(
                playitDir,
                File(playitDir, ".config/playit_gg"),
                File(playitDir, ".config/playit")
            )
            configDirs.forEach { dir ->
                val toml = File(dir, "playit.toml")
                if (toml.exists()) {
                    toml.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isPlayitBinaryInstalled(context: Context): Boolean {
        val playitBin = File(context.applicationInfo.nativeLibraryDir, "libplayit.so")
        return playitBin.exists() && playitBin.length() > 0L
    }

    fun getSavedSecretKey(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SECRET, null)
    }

    fun saveSecretKey(context: Context, secretKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SECRET, secretKey).apply()

        // Sincroniza também nos arquivos playit.toml nativos
        try {
            val playitDir = File(context.filesDir, "playit").apply { mkdirs() }
            val configDirs = listOf(
                playitDir,
                File(playitDir, ".config/playit_gg").apply { mkdirs() },
                File(playitDir, ".config/playit").apply { mkdirs() }
            )
            val tomlText = buildString {
                appendLine("secret = \"$secretKey\"")
                appendLine("secret_key = \"$secretKey\"")
            }
            configDirs.forEach { dir ->
                File(dir, "playit.toml").writeText(tomlText)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun findSecretInConfigFiles(context: Context): String? {
        try {
            val playitDir = File(context.filesDir, "playit")
            val configDirs = listOf(
                playitDir,
                File(playitDir, ".config/playit_gg"),
                File(playitDir, ".config/playit")
            )
            for (dir in configDirs) {
                val toml = File(dir, "playit.toml")
                if (toml.exists()) {
                    val content = toml.readText()
                    val match = Regex("""(?:secret|secret_key)\s*=\s*\"([^\"]+)\"""").find(content)
                    if (match != null) {
                        val secret = match.groupValues[1]
                        if (secret.isNotBlank()) {
                            saveSecretKey(context, secret)
                            return secret
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getLastKnownDomain(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_DOMAIN, null)
    }

    fun saveLastKnownDomain(context: Context, domain: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_DOMAIN, domain).apply()
    }

    /**
     * Inicia uma sessão de reivindicação / login no Playit.gg
     * Usa o protocolo oficial do Playit.gg com código hexadecimal de 8 bytes (16 caracteres)
     */
    suspend fun startClaimSession(): PlayitClaimSession? = withContext(Dispatchers.IO) {
        try {
            val bytes = ByteArray(8)
            Random().nextBytes(bytes)
            val hexCode = bytes.joinToString("") { "%02x".format(it) }

            val url = URL("https://api.playit.gg/claim/setup")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "playit-cli/0.15.26")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true

            val body = JSONObject().apply {
                put("code", hexCode)
                put("agent_type", "self-managed")
                put("version", "0.15.26")
            }.toString()

            conn.outputStream.use { it.write(body.toByteArray()) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                if (json.optString("status") == "success") {
                    val claimUrl = "https://playit.gg/claim/$hexCode"
                    return@withContext PlayitClaimSession(code = hexCode, claimUrl = claimUrl)
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Envia o sinal de heartbeat (ping) para a API do Playit.gg
     * Isso avisa a página no navegador que o agente está ativo e pronto para ser vinculado!
     */
    suspend fun checkClaimStatus(code: String): PlayitClaimStatus = withContext(Dispatchers.IO) {
        try {
            val setupUrl = URL("https://api.playit.gg/claim/setup")
            val setupConn = setupUrl.openConnection() as HttpURLConnection
            setupConn.requestMethod = "POST"
            setupConn.connectTimeout = 8000
            setupConn.readTimeout = 8000
            setupConn.setRequestProperty("User-Agent", "playit-cli/0.15.26")
            setupConn.setRequestProperty("Content-Type", "application/json")
            setupConn.setRequestProperty("Accept", "application/json")
            setupConn.doOutput = true

            val setupBody = JSONObject().apply {
                put("code", code)
                put("agent_type", "self-managed")
                put("version", "0.15.26")
            }.toString()

            setupConn.outputStream.use { it.write(setupBody.toByteArray()) }

            if (setupConn.responseCode in 200..299) {
                val setupResStr = setupConn.inputStream.bufferedReader().use { it.readText() }
                val setupJson = JSONObject(setupResStr)
                val data = setupJson.optString("data", "")
                return@withContext when (data) {
                    "UserAccepted" -> PlayitClaimStatus.ACCEPTED
                    "WaitingForUser" -> PlayitClaimStatus.WAITING_FOR_USER_CLICK
                    "WaitingForUserVisit" -> PlayitClaimStatus.WAITING_FOR_BROWSER
                    "UserRejected" -> PlayitClaimStatus.REJECTED
                    else -> PlayitClaimStatus.WAITING_FOR_USER_CLICK
                }
            }
            PlayitClaimStatus.ERROR
        } catch (e: Exception) {
            PlayitClaimStatus.ERROR
        }
    }

    /**
     * Inicia o poller contínuo em segundo plano que mantém o sinal ativo
     * mesmo que o aplicativo vá para segundo plano enquanto o usuário usa o Chrome.
     */
    fun startBackgroundClaimPoller(
        code: String,
        context: Context,
        onStatusChange: (PlayitClaimStatus, String?) -> Unit
    ) {
        activePollerJob?.cancel()
        activePollerJob = pollerScope.launch {
            while (isActive) {
                val status = checkClaimStatus(code)
                when (status) {
                    PlayitClaimStatus.ACCEPTED -> {
                        val secret = exchangeClaim(code)
                        if (!secret.isNullOrBlank()) {
                            saveSecretKey(context, secret)

                            // Conecta imediatamente o agente nativo libplayit para responder à tela "Looking for your agent..." do Playit.gg
                            try {
                                PlayitTunnelProcess.start(
                                    context = context,
                                    onLog = { msg -> ServerManager.addLog(msg) },
                                    onDomainFound = { domain -> ServerManager.updateActiveServerDomain(domain) },
                                    onBedrockDomainFound = { bDomain -> ServerManager.updateActiveServerBedrockDomain(bDomain) }
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            withContext(Dispatchers.Main) {
                                try {
                                    onStatusChange(PlayitClaimStatus.ACCEPTED, secret)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            break
                        }
                    }
                    PlayitClaimStatus.REJECTED -> {
                        withContext(Dispatchers.Main) {
                            try {
                                onStatusChange(PlayitClaimStatus.REJECTED, null)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        break
                    }
                    else -> {
                        withContext(Dispatchers.Main) {
                            try {
                                onStatusChange(status, null)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                delay(1500)
            }
        }
    }

    fun stopBackgroundClaimPoller() {
        activePollerJob?.cancel()
        activePollerJob = null
    }

    /**
     * Troca o código autorizado pelo Secret Key permanente do Playit.gg
     */
    suspend fun exchangeClaim(code: String): String? = withContext(Dispatchers.IO) {
        try {
            val exchUrl = URL("https://api.playit.gg/claim/exchange")
            val exchConn = exchUrl.openConnection() as HttpURLConnection
            exchConn.requestMethod = "POST"
            exchConn.connectTimeout = 12000
            exchConn.readTimeout = 15000
            exchConn.setRequestProperty("User-Agent", "playit-cli/0.15.26")
            exchConn.setRequestProperty("Content-Type", "application/json")
            exchConn.setRequestProperty("Accept", "application/json")
            exchConn.doOutput = true

            val exchBody = JSONObject().put("code", code).toString()
            exchConn.outputStream.use { it.write(exchBody.toByteArray()) }

            if (exchConn.responseCode in 200..299) {
                val exchResStr = exchConn.inputStream.bufferedReader().use { it.readText() }
                val exchJson = JSONObject(exchResStr)
                if (exchJson.optString("status") == "success") {
                    val dataObj = exchJson.optJSONObject("data")
                    if (dataObj != null && dataObj.has("secret_key")) {
                        return@withContext dataObj.getString("secret_key")
                    }
                    if (exchJson.has("secret_key")) {
                        return@withContext exchJson.getString("secret_key")
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Escreve a chave secreta no arquivo de configuração do plugin Playit
     * para que servidores Spigot/Paper também iniciem autenticados
     */
    fun setupPlayitConfig(serverDir: File, secretKey: String) {
        try {
            val playitConfigDir = File(File(serverDir, "plugins"), "playit-minecraft-plugin")
            if (!playitConfigDir.exists()) playitConfigDir.mkdirs()
            val configFile = File(playitConfigDir, "config.yml")
            configFile.writeText("agent-secret: \"$secretKey\"\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Baixa o plugin oficial do Playit.gg para servidores Spigot/Paper/Purpur
     */
    suspend fun downloadPlayitPlugin(pluginsDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!pluginsDir.exists()) pluginsDir.mkdirs()
            val targetFile = File(pluginsDir, "playit-minecraft-plugin.jar")
            if (targetFile.exists() && targetFile.length() > 1024) return@withContext true

            val downloadUrl = "https://github.com/playit-cloud/playit-minecraft-plugin/releases/download/v0.2.0/playit-minecraft-plugin.jar"
            val url = URL(downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "playit-minecraft-plugin/0.2.0")
            conn.connect()

            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Detecta o domínio real do Playit quando emitido no console do servidor ou pelo binário nativo
     */
    fun parsePlayitDomainFromLog(logText: String): String? {
        val stripped = logText.replace(Regex("""https?://[^\s]+"""), "")
        val regex = Regex(
            """\b([a-zA-Z0-9_-]+(?:\.[a-zA-Z0-9_-]+)*\.(?:ply\.gg|joinmc\.link|playit\.gg|craft\.gg)(?::\d{1,5})?)\b""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.find(stripped)
        val candidate = match?.value ?: return null

        if (candidate.equals("playit.gg", ignoreCase = true) ||
            candidate.equals("api.playit.gg", ignoreCase = true) ||
            candidate.equals("ply.gg", ignoreCase = true)) {
            return null
        }

        return candidate
    }

    data class TunnelDetails(
        val domain: String,
        val isBedrock: Boolean,
        val localPort: Int,
        val portType: String
    )

    data class TunnelCreationResult(
        val success: Boolean,
        val errorReason: String? = null
    )

    /**
     * Consulta dados de execução do agente na API do Playit.gg
     * Retorna o agent_id e a lista de túneis cadastrados
     */
    suspend fun fetchAgentRunData(secretKey: String): Pair<String?, List<TunnelDetails>> = withContext(Dispatchers.IO) {
        if (secretKey.isBlank()) return@withContext Pair(null, emptyList())
        val authHeaders = listOf("agent-key $secretKey", "Bearer $secretKey")
        val endpoints = listOf("https://api.playit.gg/v1/agents/rundata")

        for (endpoint in endpoints) {
            for (auth in authHeaders) {
                try {
                    val url = URL(endpoint)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.setRequestProperty("User-Agent", "playit-cli/0.15.26")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.setRequestProperty("Authorization", auth)
                    conn.doOutput = true
                    conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }

                    val code = conn.responseCode
                    if (code in 200..299) {
                        val res = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(res)
                        val data = json.optJSONObject("data") ?: continue
                        val agentId = data.optString("agent_id", "").ifBlank { null }
                        if (agentId != null) {
                            setAgentUrl("https://playit.gg/account/agents/$agentId")
                        }

                        val tunnelsList = mutableListOf<TunnelDetails>()
                        val tunnelsArr = data.optJSONArray("tunnels")
                        if (tunnelsArr != null) {
                            for (i in 0 until tunnelsArr.length()) {
                                val item = tunnelsArr.getJSONObject(i)
                                val displayAddr = item.optString("display_address", "")
                                val tunnelType = item.optString("tunnel_type", "")
                                val portType = item.optString("port_type", "")
                                val isB = tunnelType.contains("bedrock", ignoreCase = true) ||
                                          portType.equals("udp", ignoreCase = true)
                                if (displayAddr.isNotBlank()) {
                                    tunnelsList.add(TunnelDetails(displayAddr, isB, if (isB) 19132 else 25565, portType))
                                }
                            }
                        }
                        return@withContext Pair(agentId, tunnelsList)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        Pair(null, emptyList())
    }

    /**
     * Cria um túnel diretamente na API do Playit.gg usando a chave de autenticação (agent-key)
     */
    suspend fun createTunnelViaApi(
        secretKey: String,
        tunnelType: String = "minecraft-bedrock",
        localPort: Int = 19132
    ): TunnelCreationResult = withContext(Dispatchers.IO) {
        if (secretKey.isBlank()) {
            return@withContext TunnelCreationResult(false, "Chave de autenticação não encontrada")
        }

        // 1. Obter dados de execução e verificar túneis já existentes
        val (retrievedAgentId, existingTunnels) = fetchAgentRunData(secretKey)
        val isBedrock = tunnelType.contains("bedrock", ignoreCase = true)

        // Verifica se já existe um túnel deste tipo ativo na conta!
        val alreadyExisting = existingTunnels.find { it.isBedrock == isBedrock }
        if (alreadyExisting != null) {
            ServerManager.addLog(LogMessage("[Playit.gg] ℹ️ Túnel ${if (isBedrock) "Bedrock" else "Java"} já existente detectado: ${alreadyExisting.domain}", LogType.INFO))
            if (isBedrock) {
                ServerManager.updateActiveServerBedrockDomain(alreadyExisting.domain)
            } else {
                ServerManager.updateActiveServerDomain(alreadyExisting.domain)
            }
            return@withContext TunnelCreationResult(true, "Túnel já existente: ${alreadyExisting.domain}")
        }

        // Verifica se atingiu o limite de túneis da conta Playit (máximo 4 no plano gratuito)
        if (existingTunnels.size >= 4) {
            val limitMsg = "Limite de túneis atingido na conta Playit (${existingTunnels.size}/4). Exclua um túnel no site para criar outro."
            ServerManager.addLog(LogMessage("[Playit.gg] ⚠️ $limitMsg", LogType.WARN))
            return@withContext TunnelCreationResult(false, limitMsg)
        }

        var agentId: String? = retrievedAgentId
        if (agentId == null) {
            val currentAgentUrl = agentUrl.value
            if (!currentAgentUrl.isNullOrBlank()) {
                val match = Regex("""agents/([a-zA-Z0-9\-]+)""").find(currentAgentUrl)
                if (match != null) {
                    agentId = match.groupValues[1]
                }
            }
        }

        if (agentId == null) {
            val msg = "Não foi possível obter o ID do agente no Playit"
            ServerManager.addLog(LogMessage("[Playit.gg] ⚠️ $msg", LogType.ERROR))
            return@withContext TunnelCreationResult(false, msg)
        }

        val tTypeName = if (isBedrock) "minecraft-bedrock" else "minecraft-java"
        val displayName = if (isBedrock) "Minecraft Bedrock" else "Minecraft Java"

        val body = JSONObject().apply {
            put("name", displayName)
            put("protocol", JSONObject().apply {
                put("type", "tunnel-type")
                put("details", tTypeName)
            })
            put("origin", JSONObject().apply {
                put("type", "agent")
                put("data", JSONObject().apply {
                    put("agent_id", agentId)
                    put("config", JSONObject())
                })
            })
            put("endpoint", JSONObject().apply {
                put("type", "region")
                put("details", JSONObject().apply {
                    put("region", "global")
                    put("port", JSONObject.NULL)
                })
            })
            put("enabled", true)
        }.toString()

        val endpoints = listOf(
            "https://api.playit.gg/v1/tunnels/create",
            "https://api.playit.gg/tunnels/create"
        )
        val authHeaders = listOf("agent-key $secretKey", "Bearer $secretKey")

        var lastError: String? = null

        for (endpoint in endpoints) {
            for (auth in authHeaders) {
                try {
                    val url = URL(endpoint)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.setRequestProperty("User-Agent", "playit-cli/0.15.26")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.setRequestProperty("Authorization", auth)
                    conn.doOutput = true

                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                    val code = conn.responseCode
                    val res = if (code in 200..299) {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    }

                    if (code in 200..299) {
                        val json = JSONObject(res)
                        val status = json.optString("status", "")
                        if (status == "success" || json.has("data")) {
                            ServerManager.addLog(LogMessage("[Playit.gg] ✅ Túnel $displayName criado com sucesso via API!", LogType.SYSTEM))
                            return@withContext TunnelCreationResult(true)
                        }
                    } else if (res.isNotBlank()) {
                        try {
                            val errJson = JSONObject(res)
                            val dataObj = errJson.optJSONObject("data")
                            val errType = dataObj?.optString("type") ?: errJson.optString("data")
                            val errMsg = dataObj?.optString("message") ?: errType
                            lastError = errMsg
                            ServerManager.addLog(LogMessage("[Playit.gg] Resposta da API ao criar túnel ($code): $errMsg", LogType.WARN))
                        } catch (_: Exception) {
                            lastError = "HTTP $code"
                        }
                    }
                } catch (e: Exception) {
                    lastError = e.message
                    e.printStackTrace()
                }
            }
        }

        TunnelCreationResult(false, lastError ?: "Falha ao contatar a API do Playit")
    }

    /**
     * Consulta túneis existentes da conta diretamente na API do Playit.gg
     */
    suspend fun fetchTunnelsViaApi(secretKey: String): List<TunnelDetails> = withContext(Dispatchers.IO) {
        if (secretKey.isBlank()) return@withContext emptyList()

        // 1. Tenta via agents/rundata (método mais direto e rápido)
        val rundata = fetchAgentRunData(secretKey)
        if (rundata.second.isNotEmpty()) {
            return@withContext rundata.second
        }

        // 2. Fallback via /v1/tunnels/list
        val result = mutableListOf<TunnelDetails>()
        val endpoints = listOf(
            "https://api.playit.gg/v1/tunnels/list",
            "https://api.playit.gg/tunnels/list"
        )
        val authHeaders = listOf("agent-key $secretKey", "Bearer $secretKey")

        for (endpoint in endpoints) {
            for (auth in authHeaders) {
                try {
                    val url = URL(endpoint)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.setRequestProperty("User-Agent", "playit-cli/0.15.26")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.setRequestProperty("Authorization", auth)
                    conn.doOutput = true
                    conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }

                    val code = conn.responseCode
                    if (code in 200..299) {
                        val res = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(res)
                        val dataObj = json.optJSONObject("data")
                        val arr = dataObj?.optJSONArray("tunnels") ?: json.optJSONArray("data")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val item = arr.getJSONObject(i)
                                val domain = item.optString("display_address", "").ifBlank {
                                    item.optString("assigned_domain", "").ifBlank {
                                        item.optString("custom_domain", "").ifBlank {
                                            val addrs = item.optJSONArray("connect_addresses")
                                            if (addrs != null && addrs.length() > 0) {
                                                val first = addrs.getJSONObject(0)
                                                val v = first.optJSONObject("value")
                                                val addr = v?.optString("address", "") ?: first.optString("address", "")
                                                val port = v?.optInt("default_port", 0) ?: 0
                                                if (addr.isNotBlank()) {
                                                    if (port > 0 && !addr.contains(":")) "$addr:$port" else addr
                                                } else ""
                                            } else ""
                                        }
                                    }
                                }
                                val tType = item.optString("tunnel_type", "")
                                val pType = item.optString("port_type", "")
                                val lPort = item.optInt("local_port", 0)
                                val isB = tType.contains("bedrock", ignoreCase = true) ||
                                          pType.equals("udp", ignoreCase = true) ||
                                          lPort == 19132
                                if (domain.isNotBlank()) {
                                    result.add(TunnelDetails(domain, isB, lPort, pType))
                                }
                            }
                            if (result.isNotEmpty()) return@withContext result
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        result
    }

    /**
     * Cria um túnel no Playit.gg via API ou CLI
     */
    suspend fun createTunnel(context: Context, tunnelType: String): Boolean = withContext(Dispatchers.IO) {
        val savedSecret = getSavedSecretKey(context)
        val isBedrock = tunnelType.contains("bedrock", ignoreCase = true)
        val localPort = if (isBedrock) 19132 else 25565

        if (!savedSecret.isNullOrBlank()) {
            val apiOk = createTunnelViaApi(savedSecret, tunnelType, localPort)
            if (apiOk.success) return@withContext true
        }

        val playitBin = File(context.applicationInfo.nativeLibraryDir, "libplayit.so")
        if (!playitBin.exists() || playitBin.length() == 0L) return@withContext false

        val playitDir = File(context.filesDir, "playit").apply { mkdirs() }
        val name = if (isBedrock) "bedrock" else "java"
        val portType = if (isBedrock) "udp" else "tcp"

        val cmdFull = listOf(playitBin.absolutePath, "tunnels", "create", tunnelType, name, portType, "1")
        if (executePlayitCommand(cmdFull, playitDir, context)) return@withContext true

        val cmdSimple = listOf(playitBin.absolutePath, "tunnels", "create", tunnelType)
        executePlayitCommand(cmdSimple, playitDir, context)
    }

    /**
     * Consulta túneis existentes diretamente no daemon via comando "tunnels list"
     */
    fun queryExistingTunnels(
        context: Context,
        onJavaDomain: (String) -> Unit,
        onBedrockDomain: (String) -> Unit
    ) {
        val playitBin = File(context.applicationInfo.nativeLibraryDir, "libplayit.so")
        if (!playitBin.exists() || playitBin.length() == 0L) return
        val playitDir = File(context.filesDir, "playit")
        val savedSecret = getSavedSecretKey(context)
        if (savedSecret.isNullOrBlank()) return

        try {
            val cmd = listOf(playitBin.absolutePath, "tunnels", "list")
            val pb = ProcessBuilder(cmd)
            pb.directory(playitDir)
            pb.redirectErrorStream(true)
            val env = pb.environment()
            env["HOME"] = playitDir.absolutePath
            val tmpDir = File(context.filesDir, "tmp").apply { mkdirs() }
            env["TMPDIR"] = tmpDir.absolutePath
            env["PATH"] = "/system/bin:/system/xbin"

            val proc = pb.start()
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line: String?
            val discoveredList = mutableListOf<Pair<Boolean, String>>()

            while (reader.readLine().also { line = it } != null) {
                val clean = line?.trim() ?: continue
                if (clean.isBlank()) continue
                val domain = parsePlayitDomainFromLog(clean)
                if (domain != null) {
                    val isBedrock = clean.contains("udp", ignoreCase = true) ||
                                    clean.contains("bedrock", ignoreCase = true) ||
                                    clean.contains("19132")
                    discoveredList.add(isBedrock to domain)
                }
            }
            proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)

            if (discoveredList.isNotEmpty()) {
                val bedrockItem = discoveredList.find { it.first }
                val javaItem = discoveredList.find { !it.first }

                if (javaItem != null) {
                    onJavaDomain(javaItem.second)
                }
                if (bedrockItem != null) {
                    onBedrockDomain(bedrockItem.second)
                }
                // NUNCA atribuir um segundo túnel Java nominal como Bedrock!
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun executePlayitCommand(cmd: List<String>, playitDir: File, context: Context): Boolean {
        return try {
            val pb = ProcessBuilder(cmd)
            pb.directory(playitDir)
            pb.redirectErrorStream(true)

            val env = pb.environment()
            env["HOME"] = playitDir.absolutePath
            val tmpDir = File(context.filesDir, "tmp").apply { mkdirs() }
            env["TMPDIR"] = tmpDir.absolutePath
            env["PATH"] = "/system/bin:/system/xbin"

            val proc = pb.start()
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                // Esvazia buffer do processo
            }
            val completed = proc.waitFor(12, java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) {
                proc.destroy()
                false
            } else {
                proc.exitValue() == 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Sincroniza túneis existentes da conta Playit com a interface do aplicativo.
     * NUNCA cria túneis automaticamente em segundo plano para evitar ultrapassar o limite da conta.
     */
    suspend fun ensureTunnelsExist(
        context: Context,
        needJava: Boolean = true,
        needBedrock: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val savedSecret = getSavedSecretKey(context)
        if (savedSecret.isNullOrBlank()) return@withContext

        try {
            val existing = fetchTunnelsViaApi(savedSecret)
            existing.find { !it.isBedrock }?.let { ServerManager.updateActiveServerDomain(it.domain) }
            existing.find { it.isBedrock }?.let { ServerManager.updateActiveServerBedrockDomain(it.domain) }

            // Fallback caso a API direta não tenha retornado
            if (existing.isEmpty()) {
                queryExistingTunnels(
                    context = context,
                    onJavaDomain = { jDomain -> ServerManager.updateActiveServerDomain(jDomain) },
                    onBedrockDomain = { bDomain -> ServerManager.updateActiveServerBedrockDomain(bDomain) }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
