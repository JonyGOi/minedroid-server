package com.jonygoi.minedroidserver.server

import android.content.Context
import kotlinx.coroutines.*
import java.io.*

class ProcessRunner(
    private val context: Context,
    private val onLog: (LogMessage) -> Unit,
    private val onStateChange: (ServerState) -> Unit,
    private val onStatsUpdate: (uptimeSec: Long, players: Int) -> Unit
) {
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var runnerScope: CoroutineScope? = null
    private var startTimeMs: Long = 0
    private var playerCount: Int = 0

    fun isRunning(): Boolean = process?.isAlive == true

    fun start(config: ServerConfig) {
        if (isRunning()) return

        runnerScope = CoroutineScope(Dispatchers.IO + Job())
        runnerScope?.launch {
            try {
                onStateChange(ServerState.STARTING)
                val serverDir = ServerInstaller.getServerDirectory(context, config.id)
                val jarFile = File(serverDir, "server.jar")

                val prootBin = InternalRuntimeManager.getPRootBinary(context)
                val rootfsDir = InternalRuntimeManager.getRootfsDirectory(context)
                val fakeProc = InternalRuntimeManager.ensureFakeProcFiles(context)
                val tmpDir = File(context.filesDir, "tmp").apply { mkdirs() }

                val jvmArgs = OptimizationEngine.buildJvmArgs(config)
                val command = mutableListOf<String>()

                if (InternalRuntimeManager.isRuntimeInstalled(context) && prootBin.exists()) {
                    onLog(LogMessage("[MineDroid] Executando com Motor OpenJDK 17 Integrado (100% no App)...", LogType.SYSTEM))
                    command.add(prootBin.absolutePath)
                    command.add("-r")
                    command.add(rootfsDir.absolutePath)
                    command.add("-b")
                    command.add("/dev")
                    command.add("-b")
                    command.add("/proc")
                    command.add("-b")
                    command.add("${fakeProc.absolutePath}/stat:/proc/stat")
                    command.add("-b")
                    command.add("${fakeProc.absolutePath}/version:/proc/version")
                    command.add("-b")
                    command.add("${fakeProc.absolutePath}/vmstat:/proc/vmstat")
                    command.add("-b")
                    command.add("${serverDir.absolutePath}:/server")
                    command.add("-w")
                    command.add("/server")

                    command.add("/opt/java/bin/java")
                    command.addAll(jvmArgs)
                    command.add("-jar")
                    command.add("server.jar")
                    command.add("nogui")
                } else {
                    val sysJava = findSystemJava() ?: "java"
                    command.add(sysJava)
                    command.addAll(jvmArgs)
                    command.add("-jar")
                    command.add(jarFile.absolutePath)
                    command.add("nogui")
                }

                onLog(LogMessage(
                    text = "[MineDroid] Servidor '${config.name}' iniciando com ${config.allocatedRamMb}MB...",
                    type = LogType.SYSTEM
                ))
                onLog(LogMessage(
                    text = "[MineDroid] Endereço amigável: ${config.friendlyJavaAddress}",
                    type = LogType.SYSTEM
                ))

                val processBuilder = ProcessBuilder(command)
                processBuilder.directory(serverDir)
                processBuilder.redirectErrorStream(true)

                // Environment
                val env = processBuilder.environment()
                env["PROOT_NO_SECCOMP"] = "1"
                env["PROOT_FORCE_SETID"] = "1"
                env["HOME"] = "/root"
                env["PATH"] = "/opt/java/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                env["LANG"] = "C.UTF-8"
                env["TMPDIR"] = tmpDir.absolutePath
                env["TMP"] = tmpDir.absolutePath

                val proc = processBuilder.start()
                process = proc
                writer = BufferedWriter(OutputStreamWriter(proc.outputStream))
                startTimeMs = System.currentTimeMillis()
                playerCount = 0

                // Read Output
                launch {
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    var line: String?
                    try {
                        while (reader.readLine().also { line = it } != null) {
                            val msg = line ?: continue
                            handleServerOutput(msg)
                        }
                    } catch (_: Exception) {
                        // Stream closed
                    }
                }

                // Monitor stats & uptime
                launch {
                    while (proc.isAlive) {
                        val uptimeSec = (System.currentTimeMillis() - startTimeMs) / 1000
                        onStatsUpdate(uptimeSec, playerCount)
                        delay(1000)
                    }
                }

                val exitCode = proc.waitFor()
                onLog(LogMessage("[MineDroid] Processo finalizado com código $exitCode", LogType.SYSTEM))
                onStateChange(ServerState.STOPPED)

            } catch (e: Exception) {
                e.printStackTrace()
                onLog(LogMessage("[MineDroid] Erro ao executar: ${e.message}", LogType.ERROR))
                onStateChange(ServerState.ERROR)
            } finally {
                cleanup()
            }
        }
    }

    private fun handleServerOutput(msg: String) {
        val cleanMsg = stripAnsi(msg)
        val type = when {
            cleanMsg.contains("ERROR", ignoreCase = true) || cleanMsg.contains("Exception") -> LogType.ERROR
            cleanMsg.contains("WARN", ignoreCase = true) -> LogType.WARN
            cleanMsg.contains("Done (", ignoreCase = true) -> {
                onStateChange(ServerState.RUNNING)
                LogType.SYSTEM
            }
            cleanMsg.contains("joined the game", ignoreCase = true) -> {
                playerCount++
                LogType.JOIN
            }
            cleanMsg.contains("left the game", ignoreCase = true) -> {
                playerCount = maxOf(0, playerCount - 1)
                LogType.LEAVE
            }
            else -> LogType.INFO
        }
        onLog(LogMessage(cleanMsg, type))
    }

    fun sendCommand(cmd: String) {
        runnerScope?.launch(Dispatchers.IO) {
            try {
                writer?.let { w ->
                    w.write(cmd)
                    w.newLine()
                    w.flush()
                    onLog(LogMessage("> $cmd", LogType.COMMAND))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        runnerScope?.launch(Dispatchers.IO) {
            onStateChange(ServerState.STOPPING)
            onLog(LogMessage("[MineDroid] Salvando mundo e parando servidor...", LogType.SYSTEM))
            sendCommand("stop")

            // Wait up to 10 seconds
            for (i in 0 until 20) {
                if (process?.isAlive != true) break
                delay(500)
            }

            if (process?.isAlive == true) {
                onLog(LogMessage("[MineDroid] Forçando encerramento...", LogType.WARN))
                process?.destroy()
            }
            cleanup()
        }
    }

    fun kill() {
        try {
            process?.destroyForcibly()
            cleanup()
            onStateChange(ServerState.STOPPED)
            onLog(LogMessage("[MineDroid] Processo encerrado à força.", LogType.WARN))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cleanup() {
        try { writer?.close() } catch (_: Exception) {}
        writer = null
        process = null
        runnerScope?.cancel()
        runnerScope = null
    }

    private fun findSystemJava(): String? {
        val candidates = listOf(
            "${context.filesDir.absolutePath}/usr/bin/java",
            "${context.filesDir.absolutePath}/java/bin/java",
            "/system/bin/java"
        )
        for (path in candidates) {
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                return path
            }
        }
        return null
    }

    private fun stripAnsi(text: String): String {
        return text.replace(Regex("\u001B\\[[;\\d]*m"), "")
    }
}
