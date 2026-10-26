package com.nexus.launcher.jre

import android.os.Build
import com.nexus.launcher.filesystem.FileSystemManager
import com.nexus.launcher.network.DownloadQueueManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auto-downloads Liberica JRE (BellSoft) for the device ABI and extracts to
 * NexusLauncher/runtime/jre-{8|17|21}/.
 *
 * Linux aarch64/arm/x86_64 builds are used — same approach as many Android MC launchers.
 */
@Singleton
class JreDownloader @Inject constructor(
    private val fileSystemManager: FileSystemManager,
    private val downloadQueueManager: DownloadQueueManager,
    private val httpClient: OkHttpClient
) {
    sealed class Progress {
        object Idle : Progress()
        data class Resolving(val javaMajor: Int) : Progress()
        data class Downloading(val javaMajor: Int, val fraction: Float) : Progress()
        data class Extracting(val javaMajor: Int) : Progress()
        data class Done(val javaMajor: Int, val path: File) : Progress()
        data class Error(val javaMajor: Int, val message: String) : Progress()
    }

    private val _progress = MutableStateFlow<Progress>(Progress.Idle)
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    suspend fun ensureJre(javaMajor: Int): File = withContext(Dispatchers.IO) {
        val targetDir = File(fileSystemManager.jreDir, "jre-$javaMajor")
        val javaBin = File(targetDir, "bin/java")
        if (javaBin.exists() && javaBin.canExecute()) {
            return@withContext targetDir
        }

        _progress.value = Progress.Resolving(javaMajor)
        val downloadUrl = resolveLibericaUrl(javaMajor)
            ?: throw RuntimeException(
                "No Liberica JRE package found for Java $javaMajor / ABI ${deviceAbi()}"
            )

        val tmp = File(fileSystemManager.jreDir, "jre-$javaMajor-download.tar.gz")
        try {
            _progress.value = Progress.Downloading(javaMajor, 0f)
            downloadQueueManager.downloadFileWithProgress(downloadUrl, tmp) { p ->
                _progress.value = Progress.Downloading(javaMajor, p)
            }

            _progress.value = Progress.Extracting(javaMajor)
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            extractTarGz(tmp, targetDir)
            normalizeExtractedLayout(targetDir)

            val bin = File(targetDir, "bin/java")
            if (!bin.exists()) {
                throw RuntimeException("Extracted JRE missing bin/java")
            }
            bin.setExecutable(true, false)
            // Make all binaries executable
            File(targetDir, "bin").listFiles()?.forEach { f ->
                if (f.isFile) f.setExecutable(true, false)
            }

            _progress.value = Progress.Done(javaMajor, targetDir)
            targetDir
        } catch (e: Exception) {
            Timber.e(e, "JRE $javaMajor install failed")
            _progress.value = Progress.Error(javaMajor, e.message ?: "JRE install failed")
            throw e
        } finally {
            tmp.delete()
        }
    }

    private fun deviceAbi(): String {
        val abis = Build.SUPPORTED_ABIS
        return when {
            abis.any { it.startsWith("arm64") } -> "arm64"
            abis.any { it.startsWith("armeabi") || it == "arm" } -> "arm"
            abis.any { it.startsWith("x86_64") } -> "x86_64"
            abis.any { it.startsWith("x86") } -> "x86"
            else -> "arm64"
        }
    }

    /**
     * BellSoft Liberica API:
     * https://api.bell-sw.com/v1/liberica?version-feature=21&bitness=64&os=linux&arch=arm&package-type=tar.gz&bundle-type=jre
     */
    private fun resolveLibericaUrl(javaMajor: Int): String? {
        val abi = deviceAbi()
        val (arch, bitness) = when (abi) {
            "arm64" -> "arm" to "64"
            "arm" -> "arm" to "32"
            "x86_64" -> "x86" to "64"
            "x86" -> "x86" to "32"
            else -> "arm" to "64"
        }

        val api =
            "https://api.bell-sw.com/v1/liberica" +
                "?version-feature=$javaMajor" +
                "&bitness=$bitness" +
                "&os=linux" +
                "&arch=$arch" +
                "&package-type=tar.gz" +
                "&bundle-type=jre" +
                "&build-latest=true"

        val req = Request.Builder()
            .url(api)
            .header("User-Agent", "NexusLauncherPro/1.0")
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Timber.w("Liberica API ${resp.code} for Java $javaMajor")
                return null
            }
            val body = resp.body?.string() ?: return null
            val arr = JSONArray(body)
            if (arr.length() == 0) return null

            // Prefer standard JRE package
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val downloadUrl = obj.optString("downloadUrl", null)
                    ?: obj.optString("download_url", null)
                if (!downloadUrl.isNullOrBlank()) return downloadUrl
            }
            return null
        }
    }

    private fun extractTarGz(archive: File, destDir: File) {
        // Minimal tar.gz extractor (ustar) without external libs
        GZIPInputStream(BufferedInputStream(FileInputStream(archive))).use { gis ->
            val buffer = ByteArray(512)
            while (true) {
                val header = ByteArray(512)
                var read = 0
                while (read < 512) {
                    val r = gis.read(header, read, 512 - read)
                    if (r < 0) return
                    read += r
                }
                if (header.all { it == 0.toByte() }) return

                val name = header.decodeToString(0, 100).trim { it <= ' ' || it == 0.toChar() }
                if (name.isEmpty()) continue

                val sizeOctal = header.decodeToString(124, 136).trim { it <= ' ' || it == 0.toChar() || it == '0' }
                val size = sizeOctal.toLongOrNull(8) ?: 0L
                val typeFlag = header[156].toInt().toChar()

                val outFile = File(destDir, name)
                when (typeFlag) {
                    '5', 'D' -> outFile.mkdirs()
                    '0', '\u0000', '7' -> {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            var remaining = size
                            while (remaining > 0) {
                                val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                                val n = gis.read(buffer, 0, toRead)
                                if (n < 0) break
                                fos.write(buffer, 0, n)
                                remaining -= n
                            }
                        }
                    }
                    else -> {
                        // skip other types
                        var remaining = size
                        while (remaining > 0) {
                            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                            val n = gis.read(buffer, 0, toRead)
                            if (n < 0) break
                            remaining -= n
                        }
                    }
                }
                // tar blocks are 512-aligned
                val padding = ((512 - (size % 512)) % 512).toInt()
                if (padding > 0) {
                    var skip = padding.toLong()
                    while (skip > 0) {
                        val n = gis.skip(skip)
                        if (n <= 0) break
                        skip -= n
                    }
                }
            }
        }
    }

    /** Liberica often extracts to jre-21.0.x-… subfolder — flatten to targetDir. */
    private fun normalizeExtractedLayout(targetDir: File) {
        val javaDirect = File(targetDir, "bin/java")
        if (javaDirect.exists()) return

        val nested = targetDir.listFiles()?.firstOrNull { dir ->
            dir.isDirectory && File(dir, "bin/java").exists()
        } ?: return

        nested.listFiles()?.forEach { child ->
            val dest = File(targetDir, child.name)
            if (dest.exists()) dest.deleteRecursively()
            child.renameTo(dest)
        }
        nested.delete()
    }
}
