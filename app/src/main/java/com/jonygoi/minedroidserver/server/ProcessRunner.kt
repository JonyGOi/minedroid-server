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
                val serverDir = ServerInstaller.getServerDirectory(context)
                val jarFile = File(serverDir, "server.jar")

                // Locate Java binary
                val javaBin = findJavaBinary()

                val jvmArgs = OptimizationEngine.buildJvmArgs(config)
                val command = mutableListOf<String>()

                if (javaBin != null) {
                    command.add(javaBin)
                    command.addAll(jvmArgs)
                    command.add("-jar")
                    command.add(jarFile.absolutePath)
                    command.add("nogui")
                } else {
                    // Fallback to proot or shell execution
                    command.add("sh")
                    command.add("-c")
                    val cmdStr = "export PATH=\$PATH:/data/data/com.termux/files/usr/bin && " +
                            "java ${jvmArgs.joinToString(" ")} -jar ${jarFile.name} nogui"
                    command.add(cmdStr)
                }

                onLog(LogMessage(
                    text = "[MineDroid] Iniciando processo: ${command.first()} com ${config.allocatedRamMb}MB...",
                    type = LogType.SYSTEM
                ))

                val processBuilder = ProcessBuilder(command)
                processBuilder.directory(serverDir)
                processBuilder.redirectErrorStream(true)

                // Environment
                val env = processBuilder.environment()
                env["HOME"] = serverDir.absolutePath
                env["LANG"] = "en_US.UTF-8"
                env["PATH"] = "${env["PATH"] ?: ""}:/data/data/com.termux/files/usr/bin:/system/bin"

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
                    } catch (e: Exception) {
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
                LogType.INFO
            }
            cleanMsg.contains("joined the game", ignoreCase = true) -> {
                playerCount++
                LogType.JOIN
            }
            cleanMsg.contains("left the game", ignoreCase = true) -> {
                if (playerCount > 0) playerCount--
                LogType.LEAVE
            }
            else -> LogType.INFO
        }

        onLog(LogMessage(text = cleanMsg, type = type))
    }

    fun sendCommand(command: String) {
        runnerScope?.launch(Dispatchers.IO) {
            try {
                val w = writer
                if (w != null && isRunning()) {
                    w.write(command)
                    w.newLine()
                    w.flush()
                    onLog(LogMessage("> $command", LogType.COMMAND))
                }
            } catch (e: Exception) {
                onLog(LogMessage("[MineDroid] Falha ao enviar comando: ${e.message}", LogType.ERROR))
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

    private fun findJavaBinary(): String? {
        val candidates = listOf(
            "/data/data/com.termux/files/usr/bin/java",
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
