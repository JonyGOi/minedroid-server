package com.jonygoi.minedroidserver.server

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import java.io.File

object TermuxBridge {

    const val TERMUX_PACKAGE = "com.termux"
    private const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

    /**
     * Verifica se o Termux está instalado no dispositivo Android
     */
    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Gera o script executável 'start.sh' otimizado com detecção automática do Java e Aikar flags
     */
    fun generateStartupScript(context: Context, config: ServerConfig): File {
        val serverDir = ServerInstaller.getServerDirectory(context, config.id)
        val scriptFile = File(serverDir, "start.sh")

        val jvmArgs = OptimizationEngine.buildJvmArgs(config).joinToString(" ")

        val scriptContent = """
            #!/data/data/com.termux/files/usr/bin/bash
            # ==========================================================
            #   MineDroid Server - Inicializador Otimizado Termux
            #   Servidor: ${config.name} (${config.serverType.displayName} ${config.version})
            # ==========================================================
            
            echo "§a=================================================="
            echo "  Iniciando Servidor '${config.name}'..."
            echo "  Memoria alocada: ${config.allocatedRamMb} MB"
            echo "§a=================================================="
            
            # 1. Garante que o OpenJDK 17 esteja instalado
            if ! command -v java &> /dev/null; then
                echo "[MineDroid] Java nao encontrado no Termux. Instalando OpenJDK 17 automaticamente..."
                pkg update -y && pkg install -y openjdk-17
            fi
            
            # 2. Navega para o diretorio do servidor
            cd "${serverDir.absolutePath}" || exit 1
            
            # 3. Executa o servidor com argumentos de alta performance
            exec java $jvmArgs -jar server.jar nogui
        """.trimIndent()

        scriptFile.writeText(scriptContent)
        scriptFile.setExecutable(true, false)
        return scriptFile
    }

    /**
     * Obtém o comando direto em uma linha para rodar no Termux
     */
    fun getTermuxRunCommand(context: Context, config: ServerConfig): String {
        val serverDir = ServerInstaller.getServerDirectory(context, config.id)
        generateStartupScript(context, config)
        return "pkg install -y openjdk-17 && cd \"${serverDir.absolutePath}\" && bash start.sh"
    }

    /**
     * Tenta iniciar o servidor automaticamente no Termux ou abre o Termux copiando o comando
     */
    fun launchInTermux(context: Context, config: ServerConfig): Boolean {
        val serverDir = ServerInstaller.getServerDirectory(context, config.id)
        val script = generateStartupScript(context, config)
        val runCmd = getTermuxRunCommand(context, config)

        // Copia para a área de transferência por conveniência
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("MineDroid Termux Command", runCmd))

        if (!isTermuxInstalled(context)) {
            Toast.makeText(
                context,
                "Termux nao instalado. Comando copiado para a area de transferencia!",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        return try {
            // Tenta invocar o RunCommandService oficial do Termux
            val intent = Intent(ACTION_RUN_COMMAND).apply {
                setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", "cd \"${serverDir.absolutePath}\" && bash \"${script.name}\""))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", serverDir.absolutePath)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            }
            context.startService(intent)
            Toast.makeText(context, "Executando servidor via Termux!", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            // Se RunCommand falhar por falta de permissão externa, abre o Termux diretamente
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                    Toast.makeText(
                        context,
                        "Comando copiado! Cole no Termux para iniciar o servidor.",
                        Toast.LENGTH_LONG
                    ).show()
                    true
                } else false
            } catch (ex: Exception) {
                ex.printStackTrace()
                false
            }
        }
    }
}
