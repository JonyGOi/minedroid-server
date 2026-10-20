package com.jonygoi.minedroidserver.server

import android.content.Context
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object PlayitTunnelProcess {
    private var process: Process? = null
    private var processScope: CoroutineScope? = null
    @Volatile
    private var isStarting: Boolean = false

    fun isRunning(): Boolean = isStarting || (process?.isAlive == true)

    @Synchronized
    fun start(
        context: Context,
        onLog: (LogMessage) -> Unit = {},
        onDomainFound: (String) -> Unit = {},
        onClaimUrlFound: (String) -> Unit = {}
    ) {
        if (isRunning()) return
        isStarting = true

        val playitBin = File(context.applicationInfo.nativeLibraryDir, "libplayit.so")
        if (!playitBin.exists() || playitBin.length() == 0L) {
            onLog(LogMessage("[Playit.gg] Binário nativo libplayit.so não encontrado no app.", LogType.WARN))
            isStarting = false
            return
        }

        try {
            playitBin.setExecutable(true, false)
        } catch (_: Exception) {}

        val playitDir = File(context.filesDir, "playit").apply { mkdirs() }
        val playitToml = File(playitDir, "playit.toml")

        // Se o usuário já tiver uma chave salva nas preferências, escreve no playit.toml
        val savedSecret = PlayitManager.getSavedSecretKey(context)
        if (!savedSecret.isNullOrBlank()) {
            val tomlContent = buildString {
                appendLine("secret = \"$savedSecret\"")
                appendLine("secret_key = \"$savedSecret\"")
            }
            if (!playitToml.exists() || !playitToml.readText().contains(savedSecret)) {
                try {
                    playitToml.writeText(tomlContent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            // Se não há secret salvo, remove playit.toml para que o agente gere um claim novo sem travar esperando resposta
            if (playitToml.exists()) {
                try { playitToml.delete() } catch (_: Exception) {}
            }
        }

        processScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        processScope?.launch {
            try {
                onLog(LogMessage("[Playit.gg] Iniciando túnel nativo integrado via libplayit...", LogType.SYSTEM))

                val cmd = mutableListOf<String>()
                cmd.add(playitBin.absolutePath)
                // Flags de root: -s aponta para o playit.toml, --stdout ativa logs do agente, e 'start' inicia o túnel
                cmd.add("-s")
                cmd.add(playitToml.absolutePath)
                cmd.add("--stdout")
                cmd.add("start")

                val pb = ProcessBuilder(cmd)
                pb.directory(playitDir)
                pb.redirectErrorStream(true)

                val env = pb.environment()
                env["HOME"] = playitDir.absolutePath
                val tmpDir = File(context.filesDir, "tmp").apply { mkdirs() }
                env["TMPDIR"] = tmpDir.absolutePath
                val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
                env["LD_LIBRARY_PATH"] = "${nativeLibDir.absolutePath}:/system/lib64:/system/lib"
                env["PATH"] = "/system/bin:/system/xbin"

                val proc = pb.start()
                process = proc
                isStarting = false

                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val rawMsg = line ?: continue
                    val cleanMsg = stripAnsi(rawMsg).trim()
                    if (cleanMsg.isBlank()) continue

                    // 1. Detectar link de reivindicação / login automático (caso ainda não autenticado)
                    val claimMatch = Regex("""https://playit\.gg/claim/([a-zA-Z0-9]+)""").find(cleanMsg)
                    if (claimMatch != null) {
                        val claimUrl = claimMatch.value
                        onLog(LogMessage("[Playit.gg] 🔗 Autorize seu servidor no navegador: $claimUrl", LogType.WARN))
                        onClaimUrlFound(claimUrl)
                        PlayitManager.setLiveClaimUrl(claimUrl)
                    }

                    // 2. Detectar endereço público nominal (xyz.ply.gg / joinmc.link / etc.)
                    val domain = PlayitManager.parsePlayitDomainFromLog(cleanMsg)
                    if (domain != null) {
                        onLog(LogMessage("[Playit.gg] ✅ Túnel nominal ativo: $domain", LogType.SYSTEM))
                        onDomainFound(domain)

                        // Se o playit salvou uma chave nova no playit.toml, sincroniza com as preferências do app
                        try {
                            if (playitToml.exists()) {
                                val content = playitToml.readText()
                                val secretMatch = Regex("""(?:secret|secret_key)\s*=\s*\"([^\"]+)\"""").find(content)
                                if (secretMatch != null) {
                                    val secret = secretMatch.groupValues[1]
                                    PlayitManager.saveSecretKey(context, secret)
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // 3. Exibir mensagens de túnel no console do servidor com categorização adequada
                    val type = when {
                        cleanMsg.contains("error", ignoreCase = true) || cleanMsg.contains("failed", ignoreCase = true) -> LogType.ERROR
                        cleanMsg.contains("warn", ignoreCase = true) -> LogType.WARN
                        cleanMsg.contains("tunnel running", ignoreCase = true) ||
                        cleanMsg.contains("connected", ignoreCase = true) ||
                        cleanMsg.contains("registered", ignoreCase = true) ||
                        cleanMsg.contains("agent has", ignoreCase = true) -> LogType.SYSTEM
                        else -> LogType.INFO
                    }
                    onLog(LogMessage("[Playit.gg] $cleanMsg", type))
                }

                val exitCode = proc.waitFor()
                if (exitCode != 0) {
                    onLog(LogMessage("[Playit.gg] Processo de túnel finalizado com código $exitCode", LogType.WARN))
                } else {
                    onLog(LogMessage("[Playit.gg] Processo de túnel finalizado normalmente", LogType.INFO))
                }
            } catch (e: Exception) {
                isStarting = false
                onLog(LogMessage("[Playit.gg] Erro ao iniciar processo de túnel: ${e.message}", LogType.ERROR))
            } finally {
                cleanup()
            }
        }
    }

    fun stop() {
        try {
            process?.destroy()
        } catch (_: Exception) {}
        cleanup()
    }

    private fun cleanup() {
        isStarting = false
        process = null
        processScope?.cancel()
        processScope = null
    }

    private fun stripAnsi(text: String): String {
        return text.replace(Regex("\u001B\\[[;\\d]*m"), "")
    }
}
