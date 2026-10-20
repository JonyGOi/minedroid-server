package com.jonygoi.minedroidserver.server

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ServerStorage {

    private const val FILE_NAME = "servers_registry.json"

    fun getServerDirectory(context: Context, serverId: String): File {
        val dir = File(File(context.filesDir, "servers"), serverId)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun loadServers(context: Context): List<ServerConfig> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            val defaultServer = ServerConfig(
                name = "Servidor Principal (Survival)",
                customDomain = "meu-mundo.craft.gg"
            )
            saveServers(context, listOf(defaultServer))
            return listOf(defaultServer)
        }

        return try {
            val content = file.readText()
            val jsonArray = JSONArray(content)
            val list = mutableListOf<ServerConfig>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    ServerConfig(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", "Servidor Minecraft"),
                        customDomain = obj.optString("customDomain", "meu-mundo.craft.gg"),
                        domainType = DomainType.valueOf(obj.optString("domainType", DomainType.PLAYIT.name)),
                        serverType = ServerType.valueOf(obj.optString("serverType", ServerType.PAPER.name)),
                        version = obj.optString("version", "1.21.1"),
                        allocatedRamMb = obj.optInt("allocatedRamMb", 1536),
                        enableGeyser = obj.optBoolean("enableGeyser", true),
                        enableFloodgate = obj.optBoolean("enableFloodgate", true),
                        enableAikarFlags = obj.optBoolean("enableAikarFlags", true),
                        enableWakeLock = obj.optBoolean("enableWakeLock", true),
                        enableWifiLock = obj.optBoolean("enableWifiLock", true),
                        serverPort = obj.optInt("serverPort", 25565),
                        geyserPort = obj.optInt("geyserPort", 19132),
                        motd = obj.optString("motd", "§a§lMineDroid §fServer"),
                        maxPlayers = obj.optInt("maxPlayers", 10),
                        onlineMode = obj.optBoolean("onlineMode", false),
                        pvp = obj.optBoolean("pvp", true),
                        difficulty = obj.optString("difficulty", "normal"),
                        gamemode = obj.optString("gamemode", "survival"),
                        viewDistance = obj.optInt("viewDistance", 6),
                        simulationDistance = obj.optInt("simulationDistance", 4)
                    )
                )
            }

            if (list.isEmpty()) {
                val defaultServer = ServerConfig(name = "Servidor Principal")
                listOf(defaultServer)
            } else list

        } catch (e: Exception) {
            e.printStackTrace()
            listOf(ServerConfig())
        }
    }

    fun saveServers(context: Context, servers: List<ServerConfig>) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val jsonArray = JSONArray()

            servers.forEach { s ->
                val obj = JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("customDomain", s.customDomain)
                    put("domainType", s.domainType.name)
                    put("serverType", s.serverType.name)
                    put("version", s.version)
                    put("allocatedRamMb", s.allocatedRamMb)
                    put("enableGeyser", s.enableGeyser)
                    put("enableFloodgate", s.enableFloodgate)
                    put("enableAikarFlags", s.enableAikarFlags)
                    put("enableWakeLock", s.enableWakeLock)
                    put("enableWifiLock", s.enableWifiLock)
                    put("serverPort", s.serverPort)
                    put("geyserPort", s.geyserPort)
                    put("motd", s.motd)
                    put("maxPlayers", s.maxPlayers)
                    put("onlineMode", s.onlineMode)
                    put("pvp", s.pvp)
                    put("difficulty", s.difficulty)
                    put("gamemode", s.gamemode)
                    put("viewDistance", s.viewDistance)
                    put("simulationDistance", s.simulationDistance)
                }
                jsonArray.put(obj)
            }

            file.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteServerDirectory(context: Context, serverId: String) {
        try {
            val dir = getServerDirectory(context, serverId)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
