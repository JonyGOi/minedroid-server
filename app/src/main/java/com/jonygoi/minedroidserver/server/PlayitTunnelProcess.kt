package com.jonygoi.minedroidserver.server

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object PlayitTunnelProcess {
    private var process: Process? = null
    private var processScope: CoroutineScope? = null
    @Volatile
    private var isStarting: Boolean = false

    val isRunningFlow = MutableStateFlow(false)

    fun isRunning(): Boolean = isStarting || (process?.isAlive == true)

    @Synchronized
    fun start(
        context: Context,
        onLog: (LogMessage) -> Unit = {},
        onDomainFound: (String) -> Unit = {},
        onBedrockDomainFound: (String) -> Unit = {},
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
        val configDirs = listOf(
            playitDir,
            File(playitDir, ".config/playit_gg").apply { mkdirs() },
            File(playitDir, ".config/playit").apply { mkdirs() }
        )

        // Se o usuário já tiver uma chave salva nas preferências, escreve nos locais padrão do playit
        val savedSecret = PlayitManager.getSavedSecretKey(context)
        if (!savedSecret.isNullOrBlank()) {
            val tomlContent = buildString {
                appendLine("secret = \"$savedSecret\"")
                appendLine("secret_key = \"$savedSecret\"")
            }
            configDirs.forEach { dir ->
                try {
                    File(dir, "playit.toml").writeText(tomlContent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            // Se não há chave salva, remove os arquivos playit.toml antigos para forçar o auto-run a gerar claim URL novo
            configDirs.forEach { dir ->
                try {
                    File(dir, "playit.toml").delete()
                } catch (_: Exception) {}
            }
        }

        processScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        processScope?.launch {
            try {
                onLog(LogMessage("[Playit.gg] Iniciando túnel nativo integrado...", LogType.SYSTEM))
                isRunningFlow.value = true

                // Executa o binário nativo do playit diretamente no diretório onde está o playit.toml
                // Sem flags adicionais, acionando o auto-run nativo do playit de forma 100% limpa
                val cmd = listOf(playitBin.absolutePath)

                val pb = ProcessBuilder(cmd)
                pb.directory(playitDir)
                pb.redirectErrorStream(true)

                val env = pb.environment()
                env["HOME"] = playitDir.absolutePath
                val tmpDir = File(context.filesDir, "tmp").apply { mkdirs() }
                env["TMPDIR"] = tmpDir.absolutePath
                env["PATH"] = "/system/bin:/system/xbin"

                val proc = pb.start()
                process = proc
                isStarting = false

                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var line: String?

                val discoveredTunnels = mutableSetOf<String>()
                var initialStatusLogged = false
                var assignedJavaDomain: String? = null
                var assignedBedrockDomain: String? = null

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

                    // Sincroniza a chave se o agente foi aprovado na web
                    if (cleanMsg.contains("Program approved", ignoreCase = true) || cleanMsg.contains("tunnel running", ignoreCase = true)) {
                        try {
                            for (dir in configDirs) {
                                val toml = File(dir, "playit.toml")
                                if (toml.exists()) {
                                    val content = toml.readText()
                                    val secretMatch = Regex("""(?:secret|secret_key)\s*=\s*\"([^\"]+)\"""").find(content)
                                    if (secretMatch != null) {
                                        val secret = secretMatch.groupValues[1]
                                        PlayitManager.saveSecretKey(context, secret)
                                        break
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // 2. Detectar linha de mapeamento de túnel (ex: xyz.tun.ply.gg => 127.0.0.1:25565 (minecraft-java))
                    val domain = PlayitManager.parsePlayitDomainFromLog(cleanMsg)
                    if (domain != null && cleanMsg.contains("=>")) {
                        val isBedrock = cleanMsg.contains("bedrock", ignoreCase = true) ||
                                        cleanMsg.contains("19132") ||
                                        (cleanMsg.contains("udp", ignoreCase = true) && !cleanMsg.contains("tcp", ignoreCase = true))
                        val isJava = cleanMsg.contains("java", ignoreCase = true) ||
                                     cleanMsg.contains("25565") ||
                                     (cleanMsg.contains("tcp", ignoreCase = true) && !cleanMsg.contains("udp", ignoreCase = true))

                        val isNew = discoveredTunnels.add(cleanMsg)
                        if (isNew) {
                            if (isBedrock) {
                                assignedBedrockDomain = domain
                                onLog(LogMessage("[Playit.gg] 📱 Túnel Bedrock ativo: $cleanMsg", LogType.SYSTEM))
                                onBedrockDomainFound(domain)
                            } else if (isJava) {
                                // Se o servidor ativo salvou indevidamente este domínio Java como Bedrock no passado, limpa o valor falso
                                val activeConfig = ServerManager.getActiveConfig()
                                if (activeConfig != null && activeConfig.bedrockDomain == domain) {
                                    ServerManager.updateActiveServerBedrockDomain("")
                                }

                                if (assignedJavaDomain == null) {
                                    assignedJavaDomain = domain
                                    onDomainFound(domain)
                                }
                                onLog(LogMessage("[Playit.gg] ☕ Túnel Java ativo: $cleanMsg", LogType.SYSTEM))
                            } else {
                                onLog(LogMessage("[Playit.gg] 🌐 Túnel ativo: $cleanMsg", LogType.SYSTEM))
                            }
                        }
                        continue
                    } else if (domain != null && !cleanMsg.contains("http", ignoreCase = true)) {
                        val isNew = discoveredTunnels.add(domain)
                        if (isNew) {
                            if (assignedJavaDomain == null) {
                                assignedJavaDomain = domain
                                onLog(LogMessage("[Playit.gg] ✅ Túnel Java nominal ativo: $domain", LogType.SYSTEM))
                                onDomainFound(domain)
                            }
                        }
                    }

                    // 3. Filtrar atualizações periódicas repetitivas da interface TUI do Playit (evita flood no console)
                    val isTuiHeartbeat = cleanMsg.contains("tunnel running,", ignoreCase = true) ||
                                         cleanMsg.equals("TUNNELS", ignoreCase = true) ||
                                         cleanMsg.contains("doing auto run", ignoreCase = true)

                    if (isTuiHeartbeat) {
                        // Loga apenas a primeira confirmação de inicialização do túnel
                        if (!initialStatusLogged && cleanMsg.contains("tunnel running", ignoreCase = true)) {
                            initialStatusLogged = true
                            onLog(LogMessage("[Playit.gg] ✅ Túnel conectado e em execução com sucesso!", LogType.SYSTEM))

                            // Se o servidor ativo exige Bedrock (Geyser ou Proxy Standalone) e nenhum túnel Bedrock UDP foi encontrado
                            val activeCfg = ServerManager.getActiveConfig()
                            val needsBedrock = activeCfg?.enableGeyser == true || activeCfg?.serverType == ServerType.GEYSER_STANDALONE
                            if (needsBedrock && assignedBedrockDomain == null) {
                                onLog(LogMessage("[Playit.gg] ⚠️ ATENÇÃO BEDROCK: Nenhum túnel Bedrock (UDP :19132) foi detectado na sua conta Playit!", LogType.WARN))
                                onLog(LogMessage("[Playit.gg] ⚠️ Seus túneis ativos são apenas Java (TCP :25565). O GeyserMC está ouvindo localmente na UDP :19132, mas precisa de um túnel Bedrock no Playit para receber conexões externas pelo celular.", LogType.WARN))
                                onLog(LogMessage("[Playit.gg] 👉 Acesse https://playit.gg/account/tunnels e altere o túnel para 'Minecraft Bedrock' (UDP :19132), ou clique em 'Criar Automático' no app.", LogType.WARN))

                                // Tentativa de criação automática via API Playit.gg
                                val secret = PlayitManager.getSavedSecretKey(context)
                                if (!secret.isNullOrBlank()) {
                                    processScope?.launch {
                                        val ok = PlayitManager.createTunnelViaApi(secret, "minecraft-bedrock", 19132)
                                        if (ok) {
                                            onLog(LogMessage("[Playit.gg] ⚡ Túnel Bedrock provisionado via API com sucesso! Reiniciando agente...", LogType.SYSTEM))
                                            delay(1500)
                                            withContext(Dispatchers.Main) {
                                                stop()
                                                start(context, onLog, onDomainFound, onBedrockDomainFound, onClaimUrlFound)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        continue
                    }

                    // 4. Exibir mensagens de túnel relevantes no console do servidor
                    val type = when {
                        cleanMsg.contains("error", ignoreCase = true) || cleanMsg.contains("failed", ignoreCase = true) -> LogType.ERROR
                        cleanMsg.contains("warn", ignoreCase = true) -> LogType.WARN
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
        isRunningFlow.value = false
        process = null
        processScope?.cancel()
        processScope = null
    }

    private fun stripAnsi(text: String): String {
        return text
            .replace(Regex("\u001B\\[[0-9;?]*[a-zA-Z]"), "")
            .replace(Regex("\u001B[0-9=><()#]"), "")
            .replace(Regex("""\[\d*[A-Za-z]"""), "")
            .replace(Regex("""\[2J\d*"""), "")
            .trim()
    }
}
