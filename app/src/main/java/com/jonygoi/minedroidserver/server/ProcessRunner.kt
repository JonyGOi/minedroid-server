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
                val fakeDns = InternalRuntimeManager.ensureDnsFiles(context)
                val tmpDir = File(context.filesDir, "tmp").apply {
                    mkdirs()
                    try {
                        setWritable(true, false)
                        setReadable(true, false)
                        setExecutable(true, false)
                    } catch (_: Exception) {}
                }
                val l2sDir = File(context.filesDir, "l2s").apply {
                    mkdirs()
                    try {
                        setWritable(true, false)
                        setReadable(true, false)
                        setExecutable(true, false)
                    } catch (_: Exception) {}
                }

                // Verificações essenciais de ambiente
                if (!prootBin.exists()) {
                    onLog(LogMessage("[MineDroid] ERRO CRÍTICO: Binário nativo libproot.so não encontrado em:", LogType.ERROR))
                    onLog(LogMessage("[MineDroid] ${prootBin.absolutePath}", LogType.ERROR))
                    onLog(LogMessage("[MineDroid] O Android não extraiu as bibliotecas nativas. Reinstale o APK.", LogType.ERROR))
                    onStateChange(ServerState.ERROR)
                    return@launch
                }

                if (!InternalRuntimeManager.isRuntimeInstalled(context)) {
                    onLog(LogMessage("[MineDroid] ERRO CRÍTICO: OpenJDK 21 interno não encontrado no sandbox!", LogType.ERROR))
                    onLog(LogMessage("[MineDroid] A inicialização do motor Java falhou. Tente iniciar o servidor novamente para concluir o download.", LogType.ERROR))
                    onStateChange(ServerState.ERROR)
                    return@launch
                }

                onLog(LogMessage("[MineDroid] Executando com Motor OpenJDK 21 Integrado via PRoot nativo...", LogType.SYSTEM))

                val jvmArgs = OptimizationEngine.buildJvmArgs(config)
                val command = mutableListOf<String>()

                // Monta comando PRoot isolado no sandbox
                command.add(prootBin.absolutePath)
                command.add("-0")
                command.add("-l")
                command.add("-r")
                command.add(rootfsDir.absolutePath)

                // Bind mounts
                command.add("-b")
                command.add("/dev")
                command.add("-b")
                command.add("/proc")
                command.add("-b")
                command.add("/sys")
                command.add("-b")
                command.add("${fakeProc.absolutePath}/stat:/proc/stat")
                command.add("-b")
                command.add("${fakeProc.absolutePath}/version:/proc/version")
                command.add("-b")
                command.add("${fakeProc.absolutePath}/vmstat:/proc/vmstat")
                command.add("-b")
                command.add("${fakeDns.absolutePath}/resolv.conf:/etc/resolv.conf")
                command.add("-b")
                command.add("${fakeDns.absolutePath}/hosts:/etc/hosts")
                command.add("-b")
                command.add("${tmpDir.absolutePath}:/tmp")
                command.add("-b")
                command.add("${serverDir.absolutePath}:/server")
                command.add("-w")
                command.add("/server")

                val javaRelPath = InternalRuntimeManager.getJavaRelativePath(context)
                command.add(javaRelPath)
                command.addAll(jvmArgs)
                command.add("-jar")
                command.add("server.jar")
                command.add("nogui")

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

                val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
                val loaderFile = File(nativeLibDir, "libproot_loader.so")

                // Variáveis de ambiente exigidas pelo PRoot
                val env = processBuilder.environment()
                env["LD_LIBRARY_PATH"] = nativeLibDir.absolutePath
                if (loaderFile.exists() && loaderFile.length() > 0L) {
                    env["PROOT_LOADER"] = loaderFile.absolutePath
                }
                env["PROOT_TMP_DIR"] = tmpDir.absolutePath
                env["PROOT_L2S_DIR"] = l2sDir.absolutePath
                env["PROOT_UNSETENV"] = "LD_LIBRARY_PATH:TMPDIR:TMP"
                env["PROOT_NO_SECCOMP"] = "1"
                env["PROOT_FORCE_SETID"] = "1"
                env["PROOT_LINK2SYMLINK"] = "1"
                env["PROOT_IGNORE_MISSING_BINDINGS"] = "1"
                env["HOME"] = "/root"
                env["PATH"] = "/opt/java/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                env["LANG"] = "C.UTF-8"
                env["TERM"] = "xterm-256color"
                env["TMPDIR"] = "/tmp"
                env["TMP"] = "/tmp"

                val proc = processBuilder.start()
                process = proc
                writer = BufferedWriter(OutputStreamWriter(proc.outputStream))
                startTimeMs = System.currentTimeMillis()
                playerCount = 0

                // Leitura de logs da saída do servidor
                launch {
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    var line: String?
                    try {
                        while (reader.readLine().also { line = it } != null) {
                            val msg = line ?: continue
                            handleServerOutput(msg)
                        }
                    } catch (_: Exception) {
                        // Stream fechado
                    }
                }

                // Monitoramento de tempo de atividade e estatísticas
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
            cleanMsg.contains("Done (", ignoreCase = true) ||
            cleanMsg.contains("Started Geyser on", ignoreCase = true) ||
            cleanMsg.contains("Started Bedrock server", ignoreCase = true) ||
            cleanMsg.contains("Server started on", ignoreCase = true) -> {
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

            // Aguarda até 10 segundos para salvar o mundo
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
        PlayitTunnelProcess.stop()
        try { writer?.close() } catch (_: Exception) {}
        writer = null
        process = null
        runnerScope?.cancel()
        runnerScope = null
    }

    private fun stripAnsi(text: String): String {
        return text.replace(Regex("\u001B\\[[;\\d]*m"), "")
    }
}
