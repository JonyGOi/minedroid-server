package com.jonygoi.minedroidserver.server

import java.util.UUID

enum class ServerType(val displayName: String, val category: String, val isJava: Boolean) {
    PAPER("PaperMC", "Java Edition (Recomendado)", true),
    PURPUR("Purpur", "Java Edition (Ultra Otimizado)", true),
    FABRIC("Fabric", "Java Edition (Modded)", true),
    VANILLA("Vanilla", "Java Edition (Oficial)", true),
    BEDROCK_DEDICATED("Bedrock Dedicated (BDS)", "Bedrock Edition", false),
    POCKETMINE("PocketMine-MP", "Bedrock Edition (Leve)", false)
}

enum class DomainType(val displayName: String, val suffix: String) {
    PLAYIT("Playit.gg / Craft.gg (Global na Internet)", ".craft.gg"),
    LOCAL_MDNS("Rede Local mDNS (.local)", ".local"),
    CUSTOM("Domínio Próprio (DDNS / DNS)", "")
}

data class ServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Meu Servidor Minecraft",
    val customDomain: String = "meu-mundo.craft.gg",
    val domainType: DomainType = DomainType.PLAYIT,
    val serverType: ServerType = ServerType.PAPER,
    val version: String = "1.21.1",
    val allocatedRamMb: Int = 1536,
    val enableGeyser: Boolean = true,
    val enableFloodgate: Boolean = true,
    val enableAikarFlags: Boolean = true,
    val enableWakeLock: Boolean = true,
    val enableWifiLock: Boolean = true,
    val serverPort: Int = 25565,
    val geyserPort: Int = 19132,
    val motd: String = "§a§lMineDroid §fServer",
    val maxPlayers: Int = 10,
    val onlineMode: Boolean = false, // false = Pirata + Original
    val pvp: Boolean = true,
    val difficulty: String = "normal",
    val gamemode: String = "survival",
    val viewDistance: Int = 6,
    val simulationDistance: Int = 4
) {
    val friendlyJavaAddress: String
        get() = if (customDomain.isNotBlank()) {
            if (customDomain.contains(":")) customDomain else "$customDomain:$serverPort"
        } else {
            "servidor.local:$serverPort"
        }

    val friendlyBedrockAddress: String
        get() = if (customDomain.isNotBlank()) {
            val base = customDomain.split(":").first()
            "$base:$geyserPort"
        } else {
            "servidor.local:$geyserPort"
        }

    companion object {
        val AVAILABLE_VERSIONS = listOf(
            "1.21.1",
            "1.21",
            "1.20.4",
            "1.20.2",
            "1.20.1",
            "1.19.4",
            "1.18.2",
            "1.16.5"
        )
    }
}
