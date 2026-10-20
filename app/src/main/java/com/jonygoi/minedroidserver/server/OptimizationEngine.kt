package com.jonygoi.minedroidserver.server

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.io.File

object OptimizationEngine {

    fun getDeviceRamInfo(context: Context): Pair<Long, Long> {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        return Pair(totalMb, availMb)
    }

    fun getRecommendedRam(totalRamMb: Long): Int {
        return when {
            totalRamMb >= 8192 -> 4096
            totalRamMb >= 6144 -> 3072
            totalRamMb >= 4096 -> 2048
            totalRamMb >= 3072 -> 1536
            totalRamMb >= 2048 -> 1024
            else -> 768
        }
    }

    fun buildJvmArgs(config: ServerConfig): List<String> {
        val args = mutableListOf<String>()

        // Memory allocation
        args.add("-Xms${config.allocatedRamMb}M")
        args.add("-Xmx${config.allocatedRamMb}M")

        if (config.enableAikarFlags) {
            // Aikar's Tuned G1GC Flags for Minecraft
            args.add("-XX:+UseG1GC")
            args.add("-XX:+ParallelRefProcEnabled")
            args.add("-XX:MaxGCPauseMillis=200")
            args.add("-XX:+UnlockExperimentalVMOptions")
            args.add("-XX:+DisableExplicitGC")
            args.add("-XX:+AlwaysPreTouch")
            args.add("-XX:G1NewSizePercent=30")
            args.add("-XX:G1MaxNewSizePercent=40")
            args.add("-XX:G1ReservePercent=20")
            args.add("-XX:G1HeapWastePercent=5")
            args.add("-XX:G1MixedGCCountTarget=4")
            args.add("-XX:InitiatingHeapOccupancyPercent=15")
            args.add("-XX:G1MixedGCLiveThresholdPercent=90")
            args.add("-XX:G1RSetUpdatingPauseTimePercent=5")
            args.add("-XX:SurvivorRatio=32")
            args.add("-XX:+PerfDisableSharedMem")
            args.add("-XX:MaxTenuringThreshold=1")
            args.add("-Dusing.aikars.flags=https://mcflags.emc.gs")
            args.add("-Daikars.new.flags=true")
        }

        // Terminal & headless tweaks for Android
        args.add("-Dterminal.jline=false")
        args.add("-Dterminal.ansi=true")
        args.add("-Djava.awt.headless=true")

        return args
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }
        return true
    }

    fun createBatteryOptimizationIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    /**
     * Generates an automated 1-click Termux script that configures everything
     * with maximum performance (matching or exceeding manual Termux setups).
     */
    fun generateTermuxScript(config: ServerConfig, serverDir: String): String {
        val jvmFlags = buildJvmArgs(config).joinToString(" ")
        return """
            #!/data/data/com.termux/files/usr/bin/bash
            # ===================================================
            #   MineDroid Server - Automação Total para Termux
            #   Otimizado com flags de alta performance (Aikar)
            # ===================================================

            echo -e "\e[1;32m[MineDroid]\e[0m Verificando pacotes necessários..."
            pkg update -y && pkg install -y openjdk-21 wget curl proot

            SERVER_DIR="${'$'}HOME/minedroid_server"
            mkdir -p "${'$'}SERVER_DIR/plugins"
            cd "${'$'}SERVER_DIR" || exit 1

            # Aceitar EULA automaticamente
            echo "eula=true" > eula.txt

            # Configurar server.properties
            cat <<EOF > server.properties
            server-port=${config.serverPort}
            motd=${config.motd}
            max-players=${config.maxPlayers}
            online-mode=${config.onlineMode}
            pvp=${config.pvp}
            difficulty=${config.difficulty}
            view-distance=${config.viewDistance}
            simulation-distance=${config.simulationDistance}
            gamemode=${config.gamemode}
            EOF

            echo -e "\e[1;32m[MineDroid]\e[0m Baixando servidor ${config.serverType.displayName} ${config.version}..."
            # O jar do servidor e geyser sao baixados automaticamente pelo app
            
            # Impedir CPU de dormir no Termux
            termux-wake-lock 2>/dev/null || true

            echo -e "\e[1;36m[MineDroid]\e[0m Iniciando servidor com ${config.allocatedRamMb}MB RAM e Aikar's Flags..."
            exec java $jvmFlags -jar server.jar nogui
        """.trimIndent()
    }
}
