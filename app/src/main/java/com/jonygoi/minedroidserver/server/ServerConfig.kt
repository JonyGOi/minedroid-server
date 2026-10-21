package com.jonygoi.minedroidserver.server

import java.util.UUID

enum class ServerType(val displayName: String, val category: String, val isJava: Boolean) {
    PAPER("PaperMC", "Crossplay Java + Bedrock (Recomendado - Com Mundo e Plugins)", true),
    PURPUR("Purpur", "Crossplay Java + Bedrock (Ultra Otimizado)", true),
    FABRIC("Fabric", "Java Edition (Modded)", true),
    VANILLA("Vanilla", "Java Edition (Oficial Mojang)", true),
    BEDROCK_DEDICATED("Bedrock Dedicated (BDS)", "Bedrock Edition Nativo C++", false),
    POCKETMINE("PocketMine-MP", "Bedrock Edition PHP (Leve)", false),
    GEYSER_STANDALONE("GeyserMC Proxy", "Apenas Ponte/Proxy (Sem Mundo - Requer Servidor Java Externo)", true)
}

enum class DomainType(val displayName: String, val suffix: String) {
    PLAYIT("Playit.gg (Túnel Automático com Domínio Real)", ".playit.gg"),
    LOCAL_MDNS("Rede Local mDNS (.local)", ".local"),
    CUSTOM("Domínio / IP Próprio", "")
}

data class ServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Meu Servidor Minecraft",
    val customDomain: String = "",
    val bedrockDomain: String = "",
    val domainType: DomainType = DomainType.PLAYIT,
    val enablePlayitTunnel: Boolean = true,
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
        get() = when {
            customDomain.isNotBlank() -> {
                if (customDomain.contains(":")) customDomain else "$customDomain:$serverPort"
            }
            enablePlayitTunnel -> "Aguardando túnel Playit.gg..."
            else -> "servidor.local:$serverPort"
        }

    val friendlyBedrockAddress: String
        get() = when {
            bedrockDomain.isNotBlank() -> {
                if (bedrockDomain.contains(":")) bedrockDomain else "$bedrockDomain:$geyserPort"
            }
            customDomain.isNotBlank() -> {
                val base = customDomain.split(":").first()
                "$base:$geyserPort"
            }
            enablePlayitTunnel -> "Aguardando túnel Playit.gg..."
            else -> "servidor.local:$geyserPort"
        }

    companion object {
        val AVAILABLE_VERSIONS = listOf(
            "26.3",
            "26.2",
            "26.1",
            "1.21.11",
            "1.21.10",
            "1.21.9",
            "1.21.8",
            "1.21.7",
            "1.21.6",
            "1.21.5",
            "1.21.4",
            "1.21.3",
            "1.21.2",
            "1.21.1",
            "1.21",
            "1.20.6",
            "1.20.4",
            "1.20.2",
            "1.20.1",
            "1.20",
            "1.19.4",
            "1.19.3",
            "1.19.2",
            "1.19.1",
            "1.19",
            "1.18.2",
            "1.18.1",
            "1.18",
            "1.17.1",
            "1.16.5",
            "1.15.2",
            "1.14.4",
            "1.13.2",
            "1.12.2",
            "1.8.8",
            "1.7.10"
        )
    }
}
