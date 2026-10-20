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

    fun getServerDirectory(context: Context): File {
        val dir = File(context.filesDir, "minecraft_server")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPluginsDirectory(context: Context): File {
        val dir = File(getServerDirectory(context), "plugins")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun prepareServerFiles(
        context: Context,
        config: ServerConfig,
        onProgress: (status: String, progress: Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val serverDir = getServerDirectory(context)
        val serverJar = File(serverDir, "server.jar")

        try {
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
                        val mapped = 0.20f + (fileProgress * 0.50f)
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
                val pluginsDir = getPluginsDirectory(context)
                val geyserJar = File(pluginsDir, "Geyser-Spigot.jar")

                if (!geyserJar.exists() || geyserJar.length() < 1024) {
                    onProgress("Baixando GeyserMC (Crossplay Bedrock)...", 0.75f)
                    val geyserUrl = "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot"
                    downloadFile(geyserUrl, geyserJar) { p ->
                        onProgress("Baixando GeyserMC: ${(p * 100).toInt()}%", 0.75f + (p * 0.15f))
                    }
                }

                if (config.enableFloodgate) {
                    val floodgateJar = File(pluginsDir, "floodgate-spigot.jar")
                    if (!floodgateJar.exists() || floodgateJar.length() < 1024) {
                        onProgress("Baixando Floodgate (Entrada sem conta Java)...", 0.90f)
                        val floodgateUrl = "https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot"
                        downloadFile(floodgateUrl, floodgateJar) { p ->
                            onProgress("Baixando Floodgate: ${(p * 100).toInt()}%", 0.90f + (p * 0.08f))
                        }
                    }
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
                    // Fetch PaperMC build list
                    val buildsApi = "https://api.papermc.io/v2/projects/paper/versions/${config.version}/builds"
                    val jsonStr = httpGet(buildsApi) ?: return null
                    val json = JSONObject(jsonStr)
                    val builds = json.getJSONArray("builds")
                    if (builds.length() > 0) {
                        val latestBuildObj = builds.getJSONObject(builds.length() - 1)
                        val buildNum = latestBuildObj.getInt("build")
                        val downloads = latestBuildObj.getJSONObject("downloads")
                        val application = downloads.getJSONObject("application")
                        val jarName = application.getString("name")
                        "https://api.papermc.io/v2/projects/paper/versions/${config.version}/builds/$buildNum/downloads/$jarName"
                    } else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            ServerType.PURPUR -> {
                "https://api.purpurmc.org/v2/purpur/${config.version}/latest/download"
            }
            ServerType.VANILLA -> {
                // Mojang paper / vanilla fallback
                "https://api.papermc.io/v2/projects/paper/versions/${config.version}/builds"
            }
            else -> {
                "https://api.papermc.io/v2/projects/paper/versions/${config.version}/builds"
            }
        }
    }

    private fun httpGet(urlStr: String): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "MineDroid-Server-App")
            if (conn.responseCode == 200) {
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
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "MineDroid-Server-App")
            conn.connect()

            val totalSize = conn.contentLength.toLong()
            input = conn.inputStream
            output = FileOutputStream(destination)

            val buffer = ByteArray(8192)
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
            true
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
