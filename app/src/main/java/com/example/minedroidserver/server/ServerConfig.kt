package com.example.minedroidserver.server

enum class ServerType(val displayName: String, val category: String, val isJava: Boolean) {
    PAPER("PaperMC", "Java Edition (Recomendado)", true),
    PURPUR("Purpur", "Java Edition (Ultra Otimizado)", true),
    FABRIC("Fabric", "Java Edition (Modded)", true),
    VANILLA("Vanilla", "Java Edition (Oficial)", true),
    BEDROCK_DEDICATED("Bedrock Dedicated (BDS)", "Bedrock Edition", false),
    POCKETMINE("PocketMine-MP", "Bedrock Edition (Leve)", false)
}

data class ServerConfig(
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
    val motd: String = "§a§lMineDroid §fServer §7(Rodando no Celular!)",
    val maxPlayers: Int = 10,
    val onlineMode: Boolean = false, // false = Pirata + Original
    val pvp: Boolean = true,
    val difficulty: String = "normal",
    val gamemode: String = "survival",
    val viewDistance: Int = 6,
    val simulationDistance: Int = 4,
    val enablePlayitTunnel: Boolean = false
) {
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
