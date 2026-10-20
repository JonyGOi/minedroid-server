package com.jonygoi.minedroidserver.server

import android.content.Context
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Random

data class PlayitClaimSession(
    val code: String,
    val claimUrl: String
)

enum class PlayitClaimStatus {
    WAITING_FOR_BROWSER,
    WAITING_FOR_USER_CLICK,
    ACCEPTED,
    REJECTED,
    ERROR
}

object PlayitManager {

    private const val PREFS_NAME = "minedroid_playit"
    private const val KEY_SECRET = "playit_secret_key"
    private const val KEY_LAST_DOMAIN = "playit_last_domain"

    private val pollerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activePollerJob: Job? = null

    fun getSavedSecretKey(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SECRET, null)
    }

    fun saveSecretKey(context: Context, secretKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SECRET, secretKey).apply()

        // Sincroniza também no arquivo playit.toml nativo
        try {
            val playitDir = File(context.filesDir, "playit").apply { mkdirs() }
            val playitToml = File(playitDir, "playit.toml")
            playitToml.writeText("secret = \"$secretKey\"\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLastKnownDomain(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_DOMAIN, null)
    }

    fun saveLastKnownDomain(context: Context, domain: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_DOMAIN, domain).apply()
    }

    /**
     * Inicia uma sessão de reivindicação / login no Playit.gg
     * Usa o protocolo oficial do Playit.gg com código hexadecimal de 8 bytes (16 caracteres)
     */
    suspend fun startClaimSession(): PlayitClaimSession? = withContext(Dispatchers.IO) {
        try {
            val bytes = ByteArray(8)
            Random().nextBytes(bytes)
            val hexCode = bytes.joinToString("") { "%02x".format(it) }

            val url = URL("https://api.playit.gg/claim/setup")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "playit-cli/0.15.26")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true

            val body = JSONObject().apply {
                put("code", hexCode)
                put("agent_type", "self-managed")
                put("version", "0.15.26")
            }.toString()

            conn.outputStream.use { it.write(body.toByteArray()) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                if (json.optString("status") == "success") {
                    val claimUrl = "https://playit.gg/claim/$hexCode"
                    return@withContext PlayitClaimSession(code = hexCode, claimUrl = claimUrl)
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Envia o sinal de heartbeat (ping) para a API do Playit.gg
     * Isso avisa a página no navegador que o agente está ativo e pronto para ser vinculado!
     */
    suspend fun checkClaimStatus(code: String): PlayitClaimStatus = withContext(Dispatchers.IO) {
        try {
            val setupUrl = URL("https://api.playit.gg/claim/setup")
            val setupConn = setupUrl.openConnection() as HttpURLConnection
            setupConn.requestMethod = "POST"
            setupConn.connectTimeout = 8000
            setupConn.readTimeout = 8000
            setupConn.setRequestProperty("User-Agent", "playit-cli/0.15.26")
            setupConn.setRequestProperty("Content-Type", "application/json")
            setupConn.setRequestProperty("Accept", "application/json")
            setupConn.doOutput = true

            val setupBody = JSONObject().apply {
                put("code", code)
                put("agent_type", "self-managed")
                put("version", "0.15.26")
            }.toString()

            setupConn.outputStream.use { it.write(setupBody.toByteArray()) }

            if (setupConn.responseCode in 200..299) {
                val setupResStr = setupConn.inputStream.bufferedReader().use { it.readText() }
                val setupJson = JSONObject(setupResStr)
                val data = setupJson.optString("data", "")
                return@withContext when (data) {
                    "UserAccepted" -> PlayitClaimStatus.ACCEPTED
                    "WaitingForUser" -> PlayitClaimStatus.WAITING_FOR_USER_CLICK
                    "WaitingForUserVisit" -> PlayitClaimStatus.WAITING_FOR_BROWSER
                    "UserRejected" -> PlayitClaimStatus.REJECTED
                    else -> PlayitClaimStatus.WAITING_FOR_USER_CLICK
                }
            }
            PlayitClaimStatus.ERROR
        } catch (e: Exception) {
            PlayitClaimStatus.ERROR
        }
    }

    /**
     * Inicia o poller contínuo em segundo plano que mantém o sinal ativo
     * mesmo que o aplicativo vá para segundo plano enquanto o usuário usa o Chrome.
     */
    fun startBackgroundClaimPoller(
        code: String,
        context: Context,
        onStatusChange: (PlayitClaimStatus, String?) -> Unit
    ) {
        activePollerJob?.cancel()
        activePollerJob = pollerScope.launch {
            while (isActive) {
                val status = checkClaimStatus(code)
                when (status) {
                    PlayitClaimStatus.ACCEPTED -> {
                        val secret = exchangeClaim(code)
                        if (!secret.isNullOrBlank()) {
                            saveSecretKey(context, secret)

                            // Conecta imediatamente o agente nativo libplayit para responder à tela "Looking for your agent..." do Playit.gg
                            try {
                                PlayitTunnelProcess.start(
                                    context = context,
                                    onLog = { msg -> ServerManager.addLog(msg) },
                                    onDomainFound = { domain -> ServerManager.updateActiveServerDomain(domain) }
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            withContext(Dispatchers.Main) {
                                try {
                                    onStatusChange(PlayitClaimStatus.ACCEPTED, secret)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            break
                        }
                    }
                    PlayitClaimStatus.REJECTED -> {
                        withContext(Dispatchers.Main) {
                            try {
                                onStatusChange(PlayitClaimStatus.REJECTED, null)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        break
                    }
                    else -> {
                        withContext(Dispatchers.Main) {
                            try {
                                onStatusChange(status, null)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                delay(1500)
            }
        }
    }

    fun stopBackgroundClaimPoller() {
        activePollerJob?.cancel()
        activePollerJob = null
    }

    /**
     * Troca o código autorizado pelo Secret Key permanente do Playit.gg
     */
    suspend fun exchangeClaim(code: String): String? = withContext(Dispatchers.IO) {
        try {
            val exchUrl = URL("https://api.playit.gg/claim/exchange")
            val exchConn = exchUrl.openConnection() as HttpURLConnection
            exchConn.requestMethod = "POST"
            exchConn.connectTimeout = 12000
            exchConn.readTimeout = 15000
            exchConn.setRequestProperty("User-Agent", "playit-cli/0.15.26")
            exchConn.setRequestProperty("Content-Type", "application/json")
            exchConn.setRequestProperty("Accept", "application/json")
            exchConn.doOutput = true

            val exchBody = JSONObject().put("code", code).toString()
            exchConn.outputStream.use { it.write(exchBody.toByteArray()) }

            if (exchConn.responseCode in 200..299) {
                val exchResStr = exchConn.inputStream.bufferedReader().use { it.readText() }
                val exchJson = JSONObject(exchResStr)
                if (exchJson.optString("status") == "success") {
                    val dataObj = exchJson.optJSONObject("data")
                    if (dataObj != null && dataObj.has("secret_key")) {
                        return@withContext dataObj.getString("secret_key")
                    }
                    if (exchJson.has("secret_key")) {
                        return@withContext exchJson.getString("secret_key")
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Escreve a chave secreta no arquivo de configuração do plugin Playit
     * para que servidores Spigot/Paper também iniciem autenticados
     */
    fun setupPlayitConfig(serverDir: File, secretKey: String) {
        try {
            val playitConfigDir = File(File(serverDir, "plugins"), "playit-minecraft-plugin")
            if (!playitConfigDir.exists()) playitConfigDir.mkdirs()
            val configFile = File(playitConfigDir, "config.yml")
            configFile.writeText("agent-secret: \"$secretKey\"\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Baixa o plugin oficial do Playit.gg para servidores Spigot/Paper/Purpur
     */
    suspend fun downloadPlayitPlugin(pluginsDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!pluginsDir.exists()) pluginsDir.mkdirs()
            val targetFile = File(pluginsDir, "playit-minecraft-plugin.jar")
            if (targetFile.exists() && targetFile.length() > 1024) return@withContext true

            val downloadUrl = "https://github.com/playit-cloud/playit-minecraft-plugin/releases/download/v0.2.0/playit-minecraft-plugin.jar"
            val url = URL(downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "playit-minecraft-plugin/0.2.0")
            conn.connect()

            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Detecta o domínio real do Playit quando emitido no console do servidor ou pelo binário nativo
     */
    fun parsePlayitDomainFromLog(logText: String): String? {
        val stripped = logText.replace(Regex("""https?://[^\s]+"""), "")
        val regex = Regex(
            """\b([a-zA-Z0-9_-]+(?:\.[a-zA-Z0-9_-]+)*\.(?:ply\.gg|joinmc\.link|playit\.gg|craft\.gg)(?::\d{1,5})?)\b""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.find(stripped)
        val candidate = match?.value ?: return null

        if (candidate.equals("playit.gg", ignoreCase = true) ||
            candidate.equals("api.playit.gg", ignoreCase = true) ||
            candidate.equals("ply.gg", ignoreCase = true)) {
            return null
        }

        return candidate
    }
}
