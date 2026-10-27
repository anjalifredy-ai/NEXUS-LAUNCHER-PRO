package com.nexus.launcher.cosmetics

import com.nexus.launcher.filesystem.FileSystemManager
import com.nexus.launcher.network.DownloadQueueManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Disk cache for remote cosmetic assets (skins). Full files on demand only.
 */
@Singleton
class CosmeticAssetCache @Inject constructor(
    private val fileSystemManager: FileSystemManager,
    private val downloadQueueManager: DownloadQueueManager
) {
    private val root: File
        get() = File(fileSystemManager.minecraftDir, "nexus_cosmetics/cache").also { it.mkdirs() }

    fun cacheFileFor(urlOrId: String): File {
        val key = sha1(urlOrId).take(24)
        return File(root, "$key.png")
    }

    suspend fun ensureDownloaded(url: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dest = cacheFileFor(url)
            if (dest.exists() && dest.length() > 0) return@withContext Result.success(dest)
            downloadQueueManager.downloadFile(url, dest)
            if (!dest.exists() || dest.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Download empty"))
            }
            // Soft size guard
            if (dest.length() > 4 * 1024 * 1024) {
                dest.delete()
                return@withContext Result.failure(IllegalStateException("Asset too large (>4MB)"))
            }
            Result.success(dest)
        } catch (e: Exception) {
            Timber.e(e, "Cosmetic download")
            Result.failure(e)
        }
    }

    /**
     * Copies a validated skin into the active profile so offline clients can read it.
     * Does NOT upload to Microsoft servers.
     */
    suspend fun applyLocalSkin(source: File, profileId: String = "default"): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val validation = SkinImportValidator.validate(source)
                if (!validation.ok) {
                    return@withContext Result.failure(IllegalArgumentException(validation.message))
                }
                val profileDir = File(fileSystemManager.minecraftDir, "profiles/$profileId").also { it.mkdirs() }
                val dest = File(profileDir, "skin.png")
                source.copyTo(dest, overwrite = true)
                // Also mirror into standard game dir for some launchers
                val gameSkin = File(fileSystemManager.minecraftDir, "skin.png")
                source.copyTo(gameSkin, overwrite = true)
                Result.success(dest)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun clearCache(): Int {
        val files = root.listFiles() ?: return 0
        var n = 0
        files.forEach {
            if (it.delete()) n++
        }
        return n
    }

    fun cacheSizeBytes(): Long =
        root.listFiles()?.sumOf { it.length() } ?: 0L

    private fun sha1(s: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}
