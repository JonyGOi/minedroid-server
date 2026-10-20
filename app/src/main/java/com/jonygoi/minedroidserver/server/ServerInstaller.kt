package com.jonygoi.minedroidserver.server

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object ServerInstaller {

    fun getServerDirectory(context: Context, serverId: String): File {
        return ServerStorage.getServerDirectory(context, serverId)
    }

    fun getPluginsDirectory(context: Context, serverId: String): File {
        val dir = File(getServerDirectory(context, serverId), "plugins")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun prepareServerFiles(
        context: Context,
        config: ServerConfig,
        onProgress: (status: String, progress: Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val serverDir = getServerDirectory(context, config.id)
        val serverJar = File(serverDir, "server.jar")

        try {
            // Caso especial: GeyserMC Standalone (Proxy puro sem servidor Vanilla/Java)
            if (config.serverType == ServerType.GEYSER_STANDALONE) {
                onProgress("Configurando GeyserMC Standalone Proxy...", 0.10f)
                if (!serverJar.exists() || serverJar.length() < 1024) {
                    onProgress("Baixando GeyserMC Standalone...", 0.30f)
                    val geyserStandaloneUrl = "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/standalone"
                    val ok = downloadFile(geyserStandaloneUrl, serverJar) { p ->
                        onProgress("Baixando Geyser Standalone: ${(p * 100).toInt()}%", 0.30f + (p * 0.60f))
                    }
                    if (!ok) {
                        onProgress("Falha ao baixar GeyserMC Standalone. Verifique sua conexão com a internet.", 0f)
                        return@withContext false
                    }
                }
                onProgress("GeyserMC Standalone pronto!", 1.0f)
                return@withContext true
            }

            // 1. Accept EULA automatically
            onProgress("Aceitando EULA do Minecraft...", 0.05f)
            val eulaFile = File(serverDir, "eula.txt")
            eulaFile.writeText("eula=true\n# Accepted automatically by MineDroid Server\n")

            // 2. Generate server.properties
            onProgress("Configurando server.properties otimizado...", 0.10f)
            writeServerProperties(serverDir, config)

            // 3. Download Server JAR if not exists or requested
            if (!serverJar.exists() || serverJar.length() < 1024) {
                onProgress("Obtendo link de download do ${config.serverType.displayName} ${config.version}...", 0.15f)
                val downloadUrl = resolveServerDownloadUrl(config)

                if (downloadUrl != null) {
                    onProgress("Baixando ${config.serverType.displayName}...", 0.20f)
                    val downloaded = downloadFile(downloadUrl, serverJar) { fileProgress ->
                        val mapped = 0.20f + (fileProgress * 0.45f)
                        onProgress("Baixando servidor: ${(fileProgress * 100).toInt()}%", mapped)
                    }
                    if (!downloaded) {
                        onProgress("Falha ao baixar servidor. Verifique a conexão com a internet.", 0f)
                        return@withContext false
                    }
                }
            }

            // 4. Download GeyserMC & Floodgate if crossplay is enabled
            if (config.enableGeyser && config.serverType.isJava) {
                val pluginsDir = getPluginsDirectory(context, config.id)
                val geyserJar = File(pluginsDir, "Geyser-Spigot.jar")

                if (!geyserJar.exists() || geyserJar.length() < 1024) {
                    onProgress("Baixando GeyserMC (Crossplay Bedrock)...", 0.68f)
                    val geyserUrl = "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot"
                    downloadFile(geyserUrl, geyserJar) { p ->
                        onProgress("Baixando GeyserMC: ${(p * 100).toInt()}%", 0.68f + (p * 0.12f))
                    }
                }

                if (config.enableFloodgate) {
                    val floodgateJar = File(pluginsDir, "floodgate-spigot.jar")
                    if (!floodgateJar.exists() || floodgateJar.length() < 1024) {
                        onProgress("Baixando Floodgate (Entrada sem conta Java)...", 0.80f)
                        val floodgateUrl = "https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot"
                        downloadFile(floodgateUrl, floodgateJar) { p ->
                            onProgress("Baixando Floodgate: ${(p * 100).toInt()}%", 0.80f + (p * 0.08f))
                        }
                    }
                }
            }

            // 5. Download Playit.gg Plugin if Playit tunnel is enabled
            if (config.enablePlayitTunnel && config.serverType.isJava) {
                val pluginsDir = getPluginsDirectory(context, config.id)
                onProgress("Configurando túnel automático Playit.gg...", 0.90f)
                PlayitManager.downloadPlayitPlugin(pluginsDir)

                val savedSecret = PlayitManager.getSavedSecretKey(context)
                if (!savedSecret.isNullOrBlank()) {
                    PlayitManager.setupPlayitConfig(serverDir, savedSecret)
                }
            }

            onProgress("Tudo pronto para iniciar!", 1.0f)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            onProgress("Erro na preparação: ${e.message}", 0f)
            false
        }
    }

    private fun writeServerProperties(serverDir: File, config: ServerConfig) {
        val propsFile = File(serverDir, "server.properties")
        val content = StringBuilder().apply {
            appendLine("# Minecraft server properties - Gerado por MineDroid Server")
            appendLine("server-port=${config.serverPort}")
            appendLine("server-ip=")
            appendLine("motd=${config.motd}")
            appendLine("max-players=${config.maxPlayers}")
            appendLine("online-mode=${config.onlineMode}")
            appendLine("pvp=${config.pvp}")
            appendLine("difficulty=${config.difficulty}")
            appendLine("gamemode=${config.gamemode}")
            appendLine("view-distance=${config.viewDistance}")
            appendLine("simulation-distance=${config.simulationDistance}")
            appendLine("spawn-protection=0")
            appendLine("enable-command-block=true")
            appendLine("sync-chunk-writes=false")
            appendLine("network-compression-threshold=256")
            appendLine("white-list=false")
        }.toString()
        propsFile.writeText(content)
    }

    private fun resolveServerDownloadUrl(config: ServerConfig): String? {
        return when (config.serverType) {
            ServerType.PAPER -> {
                try {
                    // Novo endpoint v3 da PaperMC (fill.papermc.io)
                    val v3Api = "https://fill.papermc.io/v3/projects/paper/versions/${config.version}"
                    val jsonStr = httpGet(v3Api)
                    if (jsonStr != null) {
                        val json = JSONObject(jsonStr)
                        val builds = json.optJSONArray("builds")
                        if (builds != null && builds.length() > 0) {
                            val latestBuild = builds.getInt(0)
                            val buildApi = "https://fill.papermc.io/v3/projects/paper/versions/${config.version}/builds/$latestBuild"
                            val buildStr = httpGet(buildApi)
                            if (buildStr != null) {
                                val buildJson = JSONObject(buildStr)
                                val downloads = buildJson.optJSONObject("downloads")
                                val serverDef = downloads?.optJSONObject("server:default")
                                val dUrl = serverDef?.optString("url")
                                if (!dUrl.isNullOrBlank()) return dUrl
                            }
                        }
                    }
                    // Fallback para Purpur se Paper v3 não tiver a versão específica
                    "https://api.purpurmc.org/v2/purpur/${config.version}/latest/download"
                } catch (e: Exception) {
                    e.printStackTrace()
                    "https://api.purpurmc.org/v2/purpur/${config.version}/latest/download"
                }
            }
            ServerType.PURPUR -> {
                "https://api.purpurmc.org/v2/purpur/${config.version}/latest/download"
            }
            ServerType.VANILLA -> {
                try {
                    // Manifesto Oficial Mojang Piston Meta
                    val manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
                    val manifestStr = httpGet(manifestUrl)
                    if (manifestStr != null) {
                        val manifestJson = JSONObject(manifestStr)
                        val versions = manifestJson.optJSONArray("versions")
                        if (versions != null) {
                            for (i in 0 until versions.length()) {
                                val vObj = versions.getJSONObject(i)
                                if (vObj.optString("id") == config.version) {
                                    val pkgUrl = vObj.optString("url")
                                    val pkgStr = httpGet(pkgUrl)
                                    if (pkgStr != null) {
                                        val pkgJson = JSONObject(pkgStr)
                                        val downloads = pkgJson.optJSONObject("downloads")
                                        val serverObj = downloads?.optJSONObject("server")
                                        val serverUrl = serverObj?.optString("url")
                                        if (!serverUrl.isNullOrBlank()) return serverUrl
                                    }
                                    break
                                }
                            }
                        }
                    }
                    null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            ServerType.FABRIC -> {
                try {
                    val loaderApi = "https://meta.fabricmc.net/v2/versions/loader/${config.version}"
                    val loaderStr = httpGet(loaderApi)
                    val installerApi = "https://meta.fabricmc.net/v2/versions/installer"
                    val installerStr = httpGet(installerApi)
                    if (loaderStr != null && installerStr != null) {
                        val loaderArr = org.json.JSONArray(loaderStr)
                        val installerArr = org.json.JSONArray(installerStr)
                        if (loaderArr.length() > 0 && installerArr.length() > 0) {
                            val loaderVer = loaderArr.getJSONObject(0).getJSONObject("loader").getString("version")
                            val instVer = installerArr.getJSONObject(0).getString("version")
                            "https://meta.fabricmc.net/v2/versions/loader/${config.version}/$loaderVer/$instVer/server/jar"
                        } else null
                    } else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            ServerType.GEYSER_STANDALONE -> {
                "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/standalone"
            }
            else -> {
                "https://api.purpurmc.org/v2/purpur/${config.version}/latest/download"
            }
        }
    }

    private fun httpGet(urlStr: String): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "MineDroid-Server-App/1.6.2 (contact@minedroid.app)")
            conn.instanceFollowRedirects = true
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun downloadFile(urlStr: String, destination: File, onProgress: (Float) -> Unit): Boolean {
        var input: InputStream? = null
        var output: FileOutputStream? = null
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 25000
            conn.readTimeout = 180000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "MineDroid-Server-App/1.6.1")
            conn.connect()

            val totalSize = conn.contentLength.toLong()
            input = conn.inputStream
            output = FileOutputStream(destination)

            val buffer = ByteArray(32768)
            var totalRead: Long = 0
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                if (totalSize > 0) {
                    onProgress(totalRead.toFloat() / totalSize)
                }
            }
            output.flush()
            destination.exists() && destination.length() > 1024L
        } catch (e: Exception) {
            e.printStackTrace()
            if (destination.exists()) destination.delete()
            false
        } finally {
            try { output?.close() } catch (_: Exception) {}
            try { input?.close() } catch (_: Exception) {}
            conn?.disconnect()
        }
    }
}
