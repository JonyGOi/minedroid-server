package com.jonygoi.minedroidserver.server

import android.content.Context
import android.os.Build
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object InternalRuntimeManager {

    private const val TAG = "InternalRuntimeManager"

    // Ubuntu 22.04 LTS (Jammy) minimal base
    private const val UBUNTU_BASE_ARM64 =
        "https://cdimage.ubuntu.com/ubuntu-base/releases/22.04/release/ubuntu-base-22.04.5-base-arm64.tar.gz"
    private const val UBUNTU_BASE_AMD64 =
        "https://cdimage.ubuntu.com/ubuntu-base/releases/22.04/release/ubuntu-base-22.04.5-base-amd64.tar.gz"

    // Adoptium Eclipse Temurin OpenJDK 17 Hotspot JRE
    private const val ADOPTIUM_JRE_ARM64 =
        "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.12%2B7/OpenJDK17U-jre_aarch64_linux_hotspot_17.0.12_7.tar.gz"
    private const val ADOPTIUM_JRE_AMD64 =
        "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.12%2B7/OpenJDK17U-jre_x64_linux_hotspot_17.0.12_7.tar.gz"

    fun getRootfsDirectory(context: Context): File {
        return File(context.filesDir, "internal_rootfs")
    }

    fun getJavaExecutable(context: Context): File {
        val rootfs = getRootfsDirectory(context)
        val optJava = File(rootfs, "opt/java/bin/java")
        if (optJava.exists()) return optJava

        // Also check if extracted under a subfolder in opt
        val optDir = File(rootfs, "opt")
        if (optDir.exists()) {
            val matching = optDir.listFiles()?.firstOrNull {
                File(it, "bin/java").exists()
            }
            if (matching != null) {
                return File(matching, "bin/java")
            }
        }
        return optJava
    }

    /**
     * Verifica se o Runtime OpenJDK interno já está completamente instalado
     */
    fun isRuntimeInstalled(context: Context): Boolean {
        val javaBin = getJavaExecutable(context)
        return javaBin.exists() && javaBin.length() > 0
    }

    /**
     * Localiza o binário nativo do PRoot montado pelo Android
     */
    fun getPRootBinary(context: Context): File {
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val libProot = File(nativeLibDir, "libproot.so")
        if (libProot.exists() && libProot.length() > 0) {
            return libProot
        }
        return File(context.filesDir, "bin/libproot.so")
    }

    /**
     * Baixa e instala o ambiente OpenJDK 17 completo dentro do sandbox do aplicativo
     */
    suspend fun prepareRuntime(
        context: Context,
        onProgress: (status: String, progress: Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (isRuntimeInstalled(context)) return@withContext true

        val rootfsDir = getRootfsDirectory(context)
        val isArm64 = Build.SUPPORTED_ABIS.firstOrNull()?.contains("arm64") == true

        val ubuntuUrl = if (isArm64) UBUNTU_BASE_ARM64 else UBUNTU_BASE_AMD64
        val jreUrl = if (isArm64) ADOPTIUM_JRE_ARM64 else ADOPTIUM_JRE_AMD64

        val cacheBaseFile = File(context.cacheDir, "ubuntu_base.tar.gz")
        val cacheJreFile = File(context.cacheDir, "openjdk17.tar.gz")

        try {
            if (!rootfsDir.exists()) rootfsDir.mkdirs()

            // 1. Download Base Linux (Ubuntu minimal)
            onProgress("Baixando base do sistema integrada...", 0.05f)
            val baseOk = downloadFileWithProgress(ubuntuUrl, cacheBaseFile) { p ->
                onProgress("Baixando base integrada: ${(p * 100).toInt()}%", 0.05f + (p * 0.35f))
            }
            if (!baseOk) {
                onProgress("Falha ao baixar base do sistema.", 0f)
                return@withContext false
            }

            // 2. Extrair Base
            onProgress("Extraindo base do sistema integrada...", 0.42f)
            extractTarGz(cacheBaseFile, rootfsDir) { _, _ -> }
            cacheBaseFile.delete()

            // 3. Download OpenJDK 17
            onProgress("Baixando OpenJDK 17 integrado...", 0.50f)
            val jreOk = downloadFileWithProgress(jreUrl, cacheJreFile) { p ->
                onProgress("Baixando OpenJDK 17: ${(p * 100).toInt()}%", 0.50f + (p * 0.35f))
            }
            if (!jreOk) {
                onProgress("Falha ao baixar OpenJDK 17.", 0f)
                return@withContext false
            }

            // 4. Extrair OpenJDK em /opt/java
            onProgress("Instalando OpenJDK 17...", 0.88f)
            val optDir = File(rootfsDir, "opt")
            if (!optDir.exists()) optDir.mkdirs()

            extractTarGz(cacheJreFile, optDir) { _, _ -> }
            cacheJreFile.delete()

            // Se o JRE foi extraído dentro de uma subpasta (ex: jdk-17.0.12+7-jre), renomeia para /opt/java
            val javaTargetDir = File(optDir, "java")
            if (!javaTargetDir.exists()) {
                val jreSubfolder = optDir.listFiles()?.firstOrNull {
                    File(it, "bin/java").exists()
                }
                if (jreSubfolder != null && jreSubfolder.name != "java") {
                    jreSubfolder.renameTo(javaTargetDir)
                }
            }

            // 5. Configurar permissões e arquivos /proc emulados
            val javaBin = getJavaExecutable(context)
            javaBin.setExecutable(true, false)
            javaBin.setReadable(true, false)

            ensureFakeProcFiles(context)

            onProgress("Motor Java integrado pronto com sucesso!", 1.0f)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            onProgress("Erro na instalação do motor Java: ${e.message}", 0f)
            false
        } finally {
            cacheBaseFile.delete()
            cacheJreFile.delete()
        }
    }

    /**
     * Cria arquivos emulados /proc para permitir que a JVM consulte núcleos e memória sem restrições
     */
    fun ensureFakeProcFiles(context: Context): File {
        val fakeProcDir = File(context.filesDir, "fake_proc").apply { if (!exists()) mkdirs() }
        val numCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val statFile = File(fakeProcDir, "stat")
        val statContent = buildString {
            appendLine("cpu  2000 0 2000 200000 0 0 0 0 0 0")
            for (i in 0 until numCores) {
                appendLine("cpu$i ${2000 / numCores} 0 ${2000 / numCores} ${200000 / numCores} 0 0 0 0 0 0")
            }
            appendLine("intr 1000 0 0")
            appendLine("ctxt 5000")
            appendLine("btime ${System.currentTimeMillis() / 1000 - 86400}")
            appendLine("processes 1000")
            appendLine("procs_running 1")
            appendLine("procs_blocked 0")
            appendLine("softirq 1000 0 0 0 0 0 0 0 0 0")
        }
        statFile.writeText(statContent)

        val versionFile = File(fakeProcDir, "version")
        if (!versionFile.exists() || versionFile.length() == 0L) {
            versionFile.writeText("Linux version 6.1.0-android-proot (gcc version 11.4.0) #1 SMP PREEMPT\n")
        }

        val vmstatFile = File(fakeProcDir, "vmstat")
        if (!vmstatFile.exists() || vmstatFile.length() == 0L) {
            vmstatFile.writeText("nr_free_pages 100000\nnr_alloc_batch 1000\npgpgin 50000\npgpgout 50000\npswpin 0\npswpout 0\n")
        }

        return fakeProcDir
    }

    private fun downloadFileWithProgress(
        urlStr: String,
        dest: File,
        onProgress: (Float) -> Unit
    ): Boolean {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "MineDroid-App")
            conn.connect()

            val totalBytes = conn.contentLengthLong
            var downloaded = 0L

            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (totalBytes > 0) {
                            onProgress(downloaded.toFloat() / totalBytes)
                        }
                    }
                }
            }
            dest.exists() && dest.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Extrai arquivos .tar.gz diretamente usando a biblioteca padrão do Java
     */
    fun extractTarGz(
        tarGzFile: File,
        targetDir: File,
        onProgress: ((String, Int) -> Unit)? = null
    ) {
        if (!targetDir.exists()) targetDir.mkdirs()
        val gzipIn = GZIPInputStream(BufferedInputStream(FileInputStream(tarGzFile), 65536))
        val buffer = ByteArray(512)
        var extractedFiles = 0

        while (true) {
            readFully(gzipIn, buffer)
            val isZeroBlock = buffer.all { it.toInt() == 0 }
            if (isZeroBlock) break

            val rawName = String(buffer, 0, 100, Charsets.US_ASCII).trimEnd('\u0000', ' ')
            val prefix = String(buffer, 345, 155, Charsets.US_ASCII).trimEnd('\u0000', ' ')
            val entryName = if (prefix.isNotEmpty()) "$prefix/$rawName" else rawName
            if (entryName.isEmpty()) continue

            val size = parseOctal(buffer, 124, 12)
            val typeFlag = buffer[156].toInt().toChar()
            val destFile = File(targetDir, entryName)
            extractedFiles++

            when (typeFlag) {
                '5' -> { // Directory
                    destFile.mkdirs()
                    destFile.setReadable(true, false)
                    destFile.setWritable(true, false)
                    destFile.setExecutable(true, false)
                    val remainder = (512 - (size % 512)) % 512
                    if (remainder > 0) skipBytes(gzipIn, remainder)
                }
                '0', '\u0000' -> { // Regular file
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { out ->
                        copyBytes(gzipIn, out, size)
                    }
                    destFile.setReadable(true, false)
                    val isExec = entryName.contains("bin/") ||
                            entryName.endsWith(".sh") ||
                            entryName.contains("lib/") ||
                            entryName.contains(".so")
                    if (isExec) {
                        destFile.setExecutable(true, false)
                    }
                    val remainder = (512 - (size % 512)) % 512
                    if (remainder > 0) skipBytes(gzipIn, remainder)
                }
                '1', '2' -> { // Symlink / Hardlink
                    destFile.parentFile?.mkdirs()
                    val rawLink = String(buffer, 157, 100, Charsets.US_ASCII).trimEnd('\u0000', ' ')
                    if (rawLink.isNotEmpty()) {
                        try {
                            if (destFile.exists()) destFile.delete()
                            Os.symlink(rawLink, destFile.absolutePath)
                        } catch (_: Exception) {}
                    }
                    val remainder = (512 - (size % 512)) % 512
                    if (remainder > 0) skipBytes(gzipIn, remainder)
                }
                else -> {
                    val remainder = (512 - (size % 512)) % 512
                    skipBytes(gzipIn, size + remainder)
                }
            }
        }
        gzipIn.close()
    }

    private fun parseOctal(buffer: ByteArray, offset: Int, length: Int): Long {
        var result = 0L
        val end = offset + length
        for (i in offset until end) {
            val b = buffer[i].toInt() and 0xFF
            if (b == 0 || b == ' '.code) continue
            if (b in '0'.code..'7'.code) {
                result = (result shl 3) + (b - '0'.code)
            }
        }
        return result
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var read = 0
        while (read < buffer.size) {
            val r = input.read(buffer, read, buffer.size - read)
            if (r == -1) break
            read += r
        }
    }

    private fun copyBytes(input: InputStream, output: FileOutputStream, count: Long) {
        var remaining = count
        val buf = ByteArray(16384)
        while (remaining > 0) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val r = input.read(buf, 0, toRead)
            if (r == -1) break
            output.write(buf, 0, r)
            remaining -= r
        }
    }

    private fun skipBytes(input: InputStream, count: Long) {
        var remaining = count
        val buf = ByteArray(16384)
        while (remaining > 0) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val r = input.read(buf, 0, toRead)
            if (r == -1) break
            remaining -= r
        }
    }
}
