package com.nexus.launcher.server

import com.nexus.launcher.network.DownloadQueueManager
import com.nexus.launcher.server.model.ServerConfig
import com.nexus.launcher.server.model.ServerSoftware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ServerPluginInfo(
    val fileName: String,
    val path: String,
    val sizeBytes: Long,
    val enabled: Boolean
)

/**
 * Manages plugins/ for Paper/Spigot/Purpur and mods/ for Fabric/Forge/Quilt.
 * Enable/disable by rename to .disabled — requires server restart.
 */
@Singleton
class ServerPluginManager @Inject constructor(
    private val downloadQueueManager: DownloadQueueManager
) {

    private fun contentDir(config: ServerConfig): File {
        val name = when (config.software) {
            ServerSoftware.PAPER,
            ServerSoftware.SPIGOT,
            ServerSoftware.PURPUR -> "plugins"
            ServerSoftware.FABRIC,
            ServerSoftware.FORGE,
            ServerSoftware.NEOFORGE,
            ServerSoftware.QUILT -> "mods"
            ServerSoftware.VANILLA -> "plugins" // unused
        }
        return File(config.serverDir, name).also { it.mkdirs() }
    }

    suspend fun list(config: ServerConfig): List<ServerPluginInfo> =
        withContext(Dispatchers.IO) {
            if (config.software == ServerSoftware.VANILLA) return@withContext emptyList()
            val dir = contentDir(config)
            dir.listFiles()
                ?.filter { it.isFile && (it.name.endsWith(".jar") || it.name.endsWith(".jar.disabled")) }
                ?.map {
                    val enabled = !it.name.endsWith(".disabled")
                    ServerPluginInfo(
                        fileName = it.name.removeSuffix(".disabled"),
                        path = it.absolutePath,
                        sizeBytes = it.length(),
                        enabled = enabled
                    )
                }
                ?: emptyList()
        }

    suspend fun installFromUrl(config: ServerConfig, url: String, fileName: String): Result<File> =
        withContext(Dispatchers.IO) {
            if (config.software == ServerSoftware.VANILLA) {
                return@withContext Result.failure(
                    IllegalStateException("Vanilla has no plugins/mods")
                )
            }
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            if (!safeName.endsWith(".jar")) {
                return@withContext Result.failure(IllegalArgumentException("Only .jar allowed"))
            }
            val target = File(contentDir(config), safeName)
            try {
                downloadQueueManager.downloadFile(url, target)
                if (!target.exists() || target.length() == 0L) {
                    return@withContext Result.failure(IllegalStateException("Download empty"))
                }
                Result.success(target)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun installLocal(config: ServerConfig, sourceJar: File): Result<File> =
        withContext(Dispatchers.IO) {
            if (!sourceJar.exists() || !sourceJar.name.endsWith(".jar")) {
                return@withContext Result.failure(IllegalArgumentException("Invalid jar"))
            }
            val target = File(contentDir(config), sourceJar.name)
            sourceJar.copyTo(target, overwrite = true)
            Result.success(target)
        }

    /** Toggle enabled; returns true if now enabled. Restart required. */
    suspend fun setEnabled(config: ServerConfig, fileName: String, enabled: Boolean): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val dir = contentDir(config)
            val active = File(dir, fileName)
            val disabled = File(dir, "$fileName.disabled")
            try {
                when {
                    enabled && disabled.exists() -> {
                        disabled.renameTo(active)
                        Result.success(true)
                    }
                    !enabled && active.exists() -> {
                        active.renameTo(disabled)
                        Result.success(false)
                    }
                    enabled && active.exists() -> Result.success(true)
                    !enabled && disabled.exists() -> Result.success(false)
                    else -> Result.failure(IllegalArgumentException("Plugin not found"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun delete(config: ServerConfig, fileName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val dir = contentDir(config)
            val f1 = File(dir, fileName)
            val f2 = File(dir, "$fileName.disabled")
            when {
                f1.exists() -> f1.delete()
                f2.exists() -> f2.delete()
                else -> return@withContext Result.failure(IllegalArgumentException("Not found"))
            }
            Result.success(Unit)
        }
}
