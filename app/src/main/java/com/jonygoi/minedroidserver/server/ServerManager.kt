package com.jonygoi.minedroidserver.server

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ServerState {
    STOPPED,
    PREPARING,
    DOWNLOADING,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

enum class LogType {
    INFO,
    WARN,
    ERROR,
    SYSTEM,
    COMMAND,
    JOIN,
    LEAVE
}

data class LogMessage(
    val text: String,
    val type: LogType = LogType.INFO,
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

data class ServerStats(
    val cpuPercent: Float = 0f,
    val usedRamMb: Long = 0L,
    val totalRamMb: Long = 0L,
    val uptimeSec: Long = 0L,
    val onlinePlayers: Int = 0
) {
    val formattedUptime: String
        get() {
            val hours = uptimeSec / 3600
            val minutes = (uptimeSec % 3600) / 60
            val seconds = uptimeSec % 60
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        }
}

object ServerManager {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _servers = MutableStateFlow<List<ServerConfig>>(emptyList())
    val servers: StateFlow<List<ServerConfig>> = _servers.asStateFlow()

    private val _activeServerId = MutableStateFlow<String>("")
    val activeServerId: StateFlow<String> = _activeServerId.asStateFlow()

    private val _state = MutableStateFlow(ServerState.STOPPED)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<LogMessage>>(emptyList())
    val logs: StateFlow<List<LogMessage>> = _logs.asStateFlow()

    private val _stats = MutableStateFlow(ServerStats())
    val stats: StateFlow<ServerStats> = _stats.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Pair<String, Float>>("" to 0f)
    val downloadProgress: StateFlow<Pair<String, Float>> = _downloadProgress.asStateFlow()

    private var processRunner: ProcessRunner? = null
    private var initialized = false
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        initialized = true
        val loaded = ServerStorage.loadServers(context)
        _servers.value = loaded
        if (loaded.isNotEmpty()) {
            val savedActiveId = ServerStorage.getActiveServerId(context)
            if (!savedActiveId.isNullOrBlank() && loaded.any { it.id == savedActiveId }) {
                _activeServerId.value = savedActiveId
            } else {
                _activeServerId.value = loaded.first().id
                ServerStorage.saveActiveServerId(context, loaded.first().id)
            }
        } else {
            _activeServerId.value = ""
        }
    }

    fun getActiveConfig(): ServerConfig? {
        return _servers.value.find { it.id == _activeServerId.value }
            ?: _servers.value.firstOrNull()
    }

    fun selectServer(id: String) {
        if (_state.value == ServerState.STOPPED || _state.value == ServerState.ERROR) {
            _activeServerId.value = id
            appContext?.let { ctx ->
                ServerStorage.saveActiveServerId(ctx, id)
            }
        }
    }

    fun createServer(context: Context, newConfig: ServerConfig) {
        val current = _servers.value.toMutableList()
        current.add(newConfig)
        _servers.value = current
        _activeServerId.value = newConfig.id
        ServerStorage.saveServers(context, current)
        ServerStorage.saveActiveServerId(context, newConfig.id)
    }

    fun updateServer(context: Context, updated: ServerConfig) {
        val current = _servers.value.toMutableList()
        val index = current.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            current[index] = updated
            _servers.value = current
            ServerStorage.saveServers(context, current)
        }
    }

    fun updateActiveServerDomain(domain: String) {
        scope.launch(Dispatchers.Main) {
            val active = getActiveConfig() ?: return@launch
            if (active.customDomain != domain) {
                val updated = active.copy(customDomain = domain)
                val current = _servers.value.toMutableList()
                val index = current.indexOfFirst { it.id == updated.id }
                if (index != -1) {
                    current[index] = updated
                    _servers.value = current
                }
                appContext?.let { ctx ->
                    ServerStorage.saveServers(ctx, current)
                    if (domain.isNotBlank()) {
                        PlayitManager.saveLastKnownDomain(ctx, domain)
                    }
                }
            }
        }
    }

    fun updateActiveServerBedrockDomain(domain: String) {
        scope.launch(Dispatchers.Main) {
            val active = getActiveConfig() ?: return@launch
            if (active.bedrockDomain != domain) {
                val updated = active.copy(bedrockDomain = domain)
                val current = _servers.value.toMutableList()
                val index = current.indexOfFirst { it.id == updated.id }
                if (index != -1) {
                    current[index] = updated
                    _servers.value = current
                }
                appContext?.let { ctx ->
                    ServerStorage.saveServers(ctx, current)
                }
            }
        }
    }

    fun adaptGeyserPort(newPort: Int, context: Context) {
        scope.launch(Dispatchers.Main) {
            val active = getActiveConfig() ?: return@launch
            if (active.geyserPort != newPort && newPort in 1024..65535) {
                addLog(LogMessage("[MineDroid] 🔄 Sincronizando porta do GeyserMC: ${active.geyserPort} ➔ $newPort (conforme túnel Playit)", LogType.SYSTEM))
                val updated = active.copy(geyserPort = newPort)
                val current = _servers.value.toMutableList()
                val index = current.indexOfFirst { it.id == updated.id }
                if (index != -1) {
                    current[index] = updated
                    _servers.value = current
                }
                ServerStorage.saveServers(context, current)
                withContext(Dispatchers.IO) {
                    val sDir = ServerInstaller.getServerDirectory(context, active.id)
                    ServerInstaller.setupGeyserConfig(sDir, updated)
                }

                if (_state.value == ServerState.RUNNING || _state.value == ServerState.STARTING) {
                    addLog(LogMessage("[MineDroid] 🔄 Reiniciando servidor para aplicar nova porta do GeyserMC ($newPort)...", LogType.WARN))
                    restartServer(context)
                }
            }
        }
    }

    fun restartServer(context: Context) {
        scope.launch {
            addLog(LogMessage("[MineDroid] 🔄 Reiniciando servidor...", LogType.SYSTEM))
            processRunner?.stop()
            var count = 0
            while (_state.value != ServerState.STOPPED && count < 25) {
                delay(400)
                count++
            }
            if (_state.value != ServerState.STOPPED) {
                processRunner?.kill()
                _state.value = ServerState.STOPPED
            }
            delay(800)
            withContext(Dispatchers.Main) {
                startServer(context)
            }
        }
    }

    fun deleteServer(context: Context, id: String) {
        if (_activeServerId.value == id && (_state.value != ServerState.STOPPED && _state.value != ServerState.ERROR)) {
            return
        }
        val current = _servers.value.toMutableList()
        current.removeAll { it.id == id }
        ServerStorage.deleteServerDirectory(context, id)

        _servers.value = current
        if (_activeServerId.value == id) {
            val nextId = current.firstOrNull()?.id ?: ""
            _activeServerId.value = nextId
            ServerStorage.saveActiveServerId(context, nextId)
        }
        ServerStorage.saveServers(context, current)
    }

    fun addLog(msg: LogMessage) {
        scope.launch {
            val current = _logs.value.toMutableList()
            if (current.size > 1000) {
                current.removeAt(0)
            }
            current.add(msg)
            _logs.value = current
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun startServer(context: Context, targetServerId: String? = null) {
        if (_state.value != ServerState.STOPPED && _state.value != ServerState.ERROR) return

        if (targetServerId != null) {
            _activeServerId.value = targetServerId
        }

        val cfg = getActiveConfig() ?: return

        // Se o túnel Playit estiver ativo, limpa domínios de sessões antigas para aguardar os novos túneis reais
        if (cfg.enablePlayitTunnel) {
            val sessionCleanConfig = cfg.copy(customDomain = "", bedrockDomain = "")
            updateServer(context, sessionCleanConfig)
        }

        // Start Foreground Service
        val serviceIntent = Intent(context, MinecraftServerService::class.java).apply {
            action = MinecraftServerService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        scope.launch {
            _state.value = ServerState.PREPARING
            addLog(LogMessage("[MineDroid] Preparando servidor '${cfg.name}'...", LogType.SYSTEM))

            // 1. Garantir que o Motor OpenJDK 17 nativo integrado esteja pronto (apenas na 1ª execução)
            if (cfg.serverType.isJava && !InternalRuntimeManager.isRuntimeInstalled(context)) {
                _state.value = ServerState.DOWNLOADING
                addLog(LogMessage("[MineDroid] Configurando motor Java nativo integrado no app...", LogType.SYSTEM))
                val runtimeOk = InternalRuntimeManager.prepareRuntime(context) { status, progress ->
                    _downloadProgress.value = status to progress
                }
                if (!runtimeOk) {
                    _state.value = ServerState.ERROR
                    addLog(LogMessage("[MineDroid] Falha ao configurar motor Java integrado.", LogType.ERROR))
                    return@launch
                }
                addLog(LogMessage("[MineDroid] Motor Java integrado configurado com sucesso!", LogType.SYSTEM))
            }

            // 2. Preparar arquivos do servidor (JAR, configs, EULA, plugins)
            val prepared = ServerInstaller.prepareServerFiles(context, cfg) { status, progress ->
                _downloadProgress.value = status to progress
                if (progress > 0f) {
                    _state.value = ServerState.DOWNLOADING
                }
            }

            if (!prepared) {
                _state.value = ServerState.ERROR
                addLog(LogMessage("[MineDroid] Falha na preparação dos arquivos.", LogType.ERROR))
                return@launch
            }

            processRunner = ProcessRunner(
                context = context,
                onLog = { logMsg ->
                    addLog(logMsg)
                    val domain = PlayitManager.parsePlayitDomainFromLog(logMsg.text)
                    if (domain != null) {
                        updateActiveServerDomain(domain)
                    }
                },
                onStateChange = { newState -> _state.value = newState },
                onStatsUpdate = { uptime, players ->
                    val (totalRam, availRam) = OptimizationEngine.getDeviceRamInfo(context)
                    val usedRam = totalRam - availRam
                    _stats.value = _stats.value.copy(
                        uptimeSec = uptime,
                        onlinePlayers = players,
                        usedRamMb = usedRam,
                        totalRamMb = totalRam
                    )
                }
            )

            processRunner?.start(cfg)

            // 3. Inicia o túnel Playit.gg nativo em paralelo se habilitado na configuração
            if (cfg.enablePlayitTunnel) {
                PlayitManager.ensureTunnelsExist(
                    context = context,
                    needJava = cfg.serverType != ServerType.GEYSER_STANDALONE,
                    needBedrock = cfg.enableGeyser || cfg.serverType == ServerType.GEYSER_STANDALONE
                )

                PlayitTunnelProcess.start(
                    context = context,
                    onLog = { playitMsg -> addLog(playitMsg) },
                    onDomainFound = { domain -> updateActiveServerDomain(domain) },
                    onBedrockDomainFound = { bDomain -> updateActiveServerBedrockDomain(bDomain) }
                )
            }
        }
    }

    fun stopServer(context: Context) {
        PlayitTunnelProcess.stop()
        processRunner?.stop()
        val serviceIntent = Intent(context, MinecraftServerService::class.java).apply {
            action = MinecraftServerService.ACTION_STOP
        }
        context.startService(serviceIntent)
    }

    fun killServer(context: Context) {
        PlayitTunnelProcess.stop()
        processRunner?.kill()
        _state.value = ServerState.STOPPED
        val serviceIntent = Intent(context, MinecraftServerService::class.java).apply {
            action = MinecraftServerService.ACTION_STOP
        }
        context.startService(serviceIntent)
    }

    fun sendCommand(cmd: String) {
        processRunner?.sendCommand(cmd)
    }
}
