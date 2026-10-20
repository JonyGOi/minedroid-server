package com.example.minedroidserver.server

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

    private val _state = MutableStateFlow(ServerState.STOPPED)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    private val _config = MutableStateFlow(ServerConfig())
    val config: StateFlow<ServerConfig> = _config.asStateFlow()

    private val _logs = MutableStateFlow<List<LogMessage>>(emptyList())
    val logs: StateFlow<List<LogMessage>> = _logs.asStateFlow()

    private val _stats = MutableStateFlow(ServerStats())
    val stats: StateFlow<ServerStats> = _stats.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Pair<String, Float>>("" to 0f)
    val downloadProgress: StateFlow<Pair<String, Float>> = _downloadProgress.asStateFlow()

    private var processRunner: ProcessRunner? = null

    fun updateConfig(newConfig: ServerConfig) {
        _config.value = newConfig
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

    fun startServer(context: Context) {
        if (_state.value != ServerState.STOPPED && _state.value != ServerState.ERROR) return

        val cfg = _config.value

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
            addLog(LogMessage("[MineDroid] Preparando servidor...", LogType.SYSTEM))

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
                onLog = { logMsg -> addLog(logMsg) },
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
        }
    }

    fun stopServer(context: Context) {
        processRunner?.stop()
        val serviceIntent = Intent(context, MinecraftServerService::class.java).apply {
            action = MinecraftServerService.ACTION_STOP
        }
        context.startService(serviceIntent)
    }

    fun killServer(context: Context) {
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
