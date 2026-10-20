package com.jonygoi.minedroidserver.server

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class PlayitClaimSession(
    val code: String,
    val claimUrl: String
)

object PlayitManager {

    private const val PREFS_NAME = "minedroid_playit"
    private const val KEY_SECRET = "playit_secret_key"
    private const val KEY_LAST_DOMAIN = "playit_last_domain"

    fun getSavedSecretKey(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SECRET, null)
    }

    fun saveSecretKey(context: Context, secretKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SECRET, secretKey).apply()
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
     * Retorna a URL para o usuário abrir no navegador e confirmar
     */
    suspend fun startClaimSession(): PlayitClaimSession? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.playit.gg/claim/setup")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("User-Agent", "MineDroid-App")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write("{}".toByteArray()) }

            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val code = json.getString("code")
                val claimUrl = "https://playit.gg/claim/$code"
                PlayitClaimSession(code = code, claimUrl = claimUrl)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Verifica se o usuário já confirmou o login no navegador
     * Se confirmado, retorna a chave secreta gerada pelo Playit.gg
     */
    suspend fun exchangeClaim(code: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.playit.gg/claim/exchange")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("User-Agent", "MineDroid-App")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            val body = JSONObject().put("code", code).toString()
            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                if (json.has("secret_key")) {
                    json.getString("secret_key")
                } else null
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "MineDroid-App")
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
     * Detecta o domínio real do Playit quando emitido no console do servidor
     */
    fun parsePlayitDomainFromLog(logText: String): String? {
        // Exemplo: tunnel running: xyz.gl.joinmc.link:12345 ou xyz.auto.playit.gg:12345
        val regex = Regex("""([a-zA-Z0-9-]+\.(?:gl\.joinmc\.link|craft\.gg|playit\.gg)(?::\d+)?)""")
        val match = regex.find(logText)
        return match?.value
    }
}
