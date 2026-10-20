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

    // Adoptium Eclipse Temurin OpenJDK 21 Hotspot JRE (Suporte a Minecraft 1.7 até 1.21.3 e Geyser Standalone)
    private const val ADOPTIUM_JRE_ARM64 =
        "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jre_aarch64_linux_hotspot_21.0.12.1_1.tar.gz"
    private const val ADOPTIUM_JRE_AMD64 =
        "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jre_x64_linux_hotspot_21.0.12.1_1.tar.gz"

    fun getRootfsDirectory(context: Context): File {
        return File(context.filesDir, "internal_rootfs")
    }

    /**
     * Localiza o executável real do Java dentro da raiz do rootfs
     */
    fun getJavaExecutable(context: Context): File {
        val rootfs = getRootfsDirectory(context)
        val directOpt = File(rootfs, "opt/java/bin/java")
        if (directOpt.exists() && directOpt.length() > 1000L) return directOpt

        val directUsr = File(rootfs, "usr/bin/java")
        if (directUsr.exists() && directUsr.length() > 1000L) return directUsr

        val directBin = File(rootfs, "bin/java")
        if (directBin.exists() && directBin.length() > 1000L) return directBin

        // Pesquisa no subdiretório opt
        val optDir = File(rootfs, "opt")
        if (optDir.exists()) {
            optDir.listFiles()?.forEach { sub ->
                val cand = File(sub, "bin/java")
                if (cand.exists() && cand.length() > 1000L) return cand
            }
        }

        // Pesquisa em usr/lib/jvm
        val jvmDir = File(rootfs, "usr/lib/jvm")
        if (jvmDir.exists()) {
            jvmDir.listFiles()?.forEach { sub ->
                val cand = File(sub, "bin/java")
                if (cand.exists() && cand.length() > 1000L) return cand
            }
        }

        return directOpt
    }

    /**
     * Caminho relativo dentro do PRoot (ex: /opt/java/bin/java)
     */
    fun getJavaRelativePath(context: Context): String {
        val rootfs = getRootfsDirectory(context)
        val javaBin = getJavaExecutable(context)
        if (javaBin.exists()) {
            val rel = javaBin.relativeToOrNull(rootfs)?.invariantSeparatorsPath
            if (!rel.isNullOrEmpty()) {
                return "/$rel"
            }
        }
        return "/opt/java/bin/java"
    }

    /**
     * Verifica se o Runtime OpenJDK 21 interno já está completamente instalado
     */
    fun isRuntimeInstalled(context: Context): Boolean {
        val rootfs = getRootfsDirectory(context)
        val versionMarker = File(rootfs, "opt/java_version.txt")
        if (!versionMarker.exists() || versionMarker.readText().trim() != "21") {
            return false
        }
        val javaBin = getJavaExecutable(context)
        return javaBin.exists() && javaBin.length() > 10000L
    }

    /**
     * Localiza o binário nativo do PRoot montado pelo Android PackageManager
     */
    fun getPRootBinary(context: Context): File {
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val libProot = File(nativeLibDir, "libproot.so")
        if (libProot.exists() && libProot.length() > 0L) {
            libProot.setExecutable(true, false)
            return libProot
        }

        // Fallback em caso de emulação/diretório alternativo
        val filesProot = File(context.filesDir, "bin/libproot.so")
        if (filesProot.exists() && filesProot.length() > 0L) {
            filesProot.setExecutable(true, false)
            return filesProot
        }

        return libProot
    }

    /**
     * Baixa e instala o ambiente OpenJDK 21 completo dentro do sandbox do aplicativo
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
        val cacheJreFile = File(context.cacheDir, "openjdk21.tar.gz")

        try {
            if (!rootfsDir.exists()) rootfsDir.mkdirs()

            // 1. Download e extração da Base Linux (apenas se ainda não existir)
            val hasBaseLinux = File(rootfsDir, "bin/sh").exists() || File(rootfsDir, "usr/bin/sh").exists()
            if (!hasBaseLinux) {
                onProgress("Baixando base do sistema integrada...", 0.05f)
                val baseOk = downloadFileWithProgress(ubuntuUrl, cacheBaseFile) { p ->
                    onProgress("Baixando base integrada: ${(p * 100).toInt()}%", 0.05f + (p * 0.35f))
                }
                if (!baseOk) {
                    onProgress("Falha ao baixar base do sistema.", 0f)
                    return@withContext false
                }

                onProgress("Extraindo base do sistema integrada...", 0.42f)
                extractTarGz(cacheBaseFile, rootfsDir)
                cacheBaseFile.delete()
            }

            // 2. Download OpenJDK 21
            onProgress("Baixando OpenJDK 21 integrado...", 0.50f)
            val jreOk = downloadFileWithProgress(jreUrl, cacheJreFile) { p ->
                onProgress("Baixando OpenJDK 21: ${(p * 100).toInt()}%", 0.50f + (p * 0.35f))
            }
            if (!jreOk) {
                onProgress("Falha ao baixar OpenJDK 21.", 0f)
                return@withContext false
            }

            // 3. Extrair OpenJDK em /opt/java
            onProgress("Instalando OpenJDK 21...", 0.88f)
            val optDir = File(rootfsDir, "opt")
            if (!optDir.exists()) optDir.mkdirs()

            // Remove versões anteriores de JRE para evitar conflito de classes
            val oldJava = File(optDir, "java")
            if (oldJava.exists()) {
                oldJava.deleteRecursively()
            }
            optDir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name.startsWith("jdk-")) {
                    file.deleteRecursively()
                }
            }

            extractTarGz(cacheJreFile, optDir)
            cacheJreFile.delete()

            // Se o JRE foi extraído dentro de uma subpasta (ex: jdk-21.0.12.1+1-jre), renomeia para /opt/java
            val javaTargetDir = File(optDir, "java")
            if (!javaTargetDir.exists()) {
                val jreSubfolder = optDir.listFiles()?.firstOrNull {
                    File(it, "bin/java").exists()
                }
                if (jreSubfolder != null && jreSubfolder.name != "java") {
                    jreSubfolder.renameTo(javaTargetDir)
                }
            }

            // Grava marcador de versão 21
            File(optDir, "java_version.txt").writeText("21\n")

            // 4. Configurar permissões de execução e arquivos /proc emulados
            val javaBin = getJavaExecutable(context)
            javaBin.parentFile?.listFiles()?.forEach { binFile ->
                binFile.setExecutable(true, false)
                binFile.setReadable(true, false)
            }
            javaBin.setExecutable(true, false)
            javaBin.setReadable(true, false)

            // Criar atalho /usr/bin/java se possível
            try {
                val usrBin = File(rootfsDir, "usr/bin").apply { mkdirs() }
                val usrJava = File(usrBin, "java")
                if (!usrJava.exists()) {
                    val relPath = getJavaRelativePath(context)
                    Os.symlink(relPath, usrJava.absolutePath)
                }
            } catch (_: Exception) {}

            ensureFakeProcFiles(context)

            onProgress("Motor Java 21 integrado pronto com sucesso!", 1.0f)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            onProgress("Erro na instalação do motor Java: ${e.message}", 0f)
            false
        } finally {
            try { cacheBaseFile.delete() } catch (_: Exception) {}
            try { cacheJreFile.delete() } catch (_: Exception) {}
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

    /**
     * Cria e configura arquivos de resolução DNS (/etc/resolv.conf e /etc/hosts)
     * Garante que o Java e o Geyser consigam resolver serviços da Microsoft/Mojang sem UnknownHostException
     */
    fun ensureDnsFiles(context: Context): File {
        val fakeDnsDir = File(context.filesDir, "fake_dns").apply { if (!exists()) mkdirs() }

        val resolvFile = File(fakeDnsDir, "resolv.conf")
        val resolvContent = buildString {
            appendLine("nameserver 1.1.1.1")
            appendLine("nameserver 1.0.0.1")
            appendLine("nameserver 8.8.8.8")
            appendLine("nameserver 8.8.4.4")
            appendLine("options timeout:2 attempts:3")
        }
        resolvFile.writeText(resolvContent)
        resolvFile.setReadable(true, false)

        val hostsFile = File(fakeDnsDir, "hosts")
        val hostsContent = buildString {
            appendLine("127.0.0.1 localhost localhost.localdomain")
            appendLine("::1 localhost ip6-localhost ip6-loopback")
        }
        hostsFile.writeText(hostsContent)
        hostsFile.setReadable(true, false)

        // Também assegura a gravação física dentro do próprio rootfs
        try {
            val rootfs = getRootfsDirectory(context)
            val etcDir = File(rootfs, "etc").apply { if (!exists()) mkdirs() }
            val rootfsResolv = File(etcDir, "resolv.conf")
            if (rootfsResolv.exists()) rootfsResolv.delete()
            rootfsResolv.writeText(resolvContent)
            rootfsResolv.setReadable(true, false)

            val rootfsHosts = File(etcDir, "hosts")
            rootfsHosts.writeText(hostsContent)
            rootfsHosts.setReadable(true, false)
        } catch (_: Exception) {}

        return fakeDnsDir
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
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "MineDroid-App/1.6.0")
            conn.connect()

            val totalBytes = conn.contentLengthLong
            var downloaded = 0L

            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(32768)
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
     * Extrai arquivos .tar.gz com suporte a GNU LongLink ('L' e 'K') e symlinks
     */
    fun extractTarGz(tarGzFile: File, targetDir: File) {
        if (!targetDir.exists()) targetDir.mkdirs()
        val gzipIn = GZIPInputStream(BufferedInputStream(FileInputStream(tarGzFile), 65536))
        val buffer = ByteArray(512)

        var longLinkName: String? = null
        var longTargetName: String? = null

        while (true) {
            readFully(gzipIn, buffer)
            val isZeroBlock = buffer.all { it.toInt() == 0 }
            if (isZeroBlock) break

            val rawName = String(buffer, 0, 100, Charsets.US_ASCII).trimEnd('\u0000', ' ')
            val prefix = String(buffer, 345, 155, Charsets.US_ASCII).trimEnd('\u0000', ' ')
            var entryName = if (prefix.isNotEmpty()) "$prefix/$rawName" else rawName
            val size = parseOctal(buffer, 124, 12)
            val typeFlag = buffer[156].toInt().toChar()

            if (typeFlag == 'L') {
                val nameBytes = ByteArray(size.toInt())
                readFully(gzipIn, nameBytes)
                longLinkName = String(nameBytes, Charsets.UTF_8).trimEnd('\u0000', ' ', '\n', '\r')
                val remainder = ((512 - (size % 512)) % 512).toInt()
                if (remainder > 0) skipBytes(gzipIn, remainder.toLong())
                continue
            }
            if (typeFlag == 'K') {
                val linkBytes = ByteArray(size.toInt())
                readFully(gzipIn, linkBytes)
                longTargetName = String(linkBytes, Charsets.UTF_8).trimEnd('\u0000', ' ', '\n', '\r')
                val remainder = ((512 - (size % 512)) % 512).toInt()
                if (remainder > 0) skipBytes(gzipIn, remainder.toLong())
                continue
            }

            if (!longLinkName.isNullOrEmpty()) {
                entryName = longLinkName
                longLinkName = null
            }
            if (entryName.isEmpty()) {
                val remainder = ((512 - (size % 512)) % 512).toInt()
                if (remainder > 0) skipBytes(gzipIn, remainder.toLong())
                continue
            }

            // Normaliza caminho sem barras iniciais
            entryName = entryName.removePrefix("./").removePrefix("/")
            val destFile = File(targetDir, entryName)

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
                    val rawLink = longTargetName ?: String(buffer, 157, 100, Charsets.US_ASCII).trimEnd('\u0000', ' ')
                    longTargetName = null
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
        val buf = ByteArray(32768)
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
        val buf = ByteArray(32768)
        while (remaining > 0) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val r = input.read(buf, 0, toRead)
            if (r == -1) break
            remaining -= r
        }
    }
}
