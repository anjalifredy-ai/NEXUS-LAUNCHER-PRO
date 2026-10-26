package com.nexus.launcher.server

import com.nexus.launcher.filesystem.FileSystemManager
import com.nexus.launcher.server.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class WorldFolder(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val hasLevelDat: Boolean
)

/**
 * Browse launcher saves + server worlds; copy/link a world into a server directory.
 */
@Singleton
class ServerWorldManager @Inject constructor(
    private val fileSystemManager: FileSystemManager
) {

    /** Worlds already inside the server folder. */
    suspend fun listServerWorlds(config: ServerConfig): List<WorldFolder> =
        withContext(Dispatchers.IO) {
            val dir = File(config.serverDir)
            if (!dir.exists()) return@withContext emptyList()
            dir.listFiles()
                ?.filter { it.isDirectory && File(it, "level.dat").exists() }
                ?.map { toWorldFolder(it) }
                ?: emptyList()
        }

    /** Worlds from main launcher saves (client profiles). */
    suspend fun listLauncherSaves(): List<WorldFolder> = withContext(Dispatchers.IO) {
        val saves = fileSystemManager.savesDir
        if (!saves.exists()) return@withContext emptyList()
        saves.listFiles()
            ?.filter { it.isDirectory }
            ?.map { toWorldFolder(it) }
            ?: emptyList()
    }

    /**
     * Copy a world folder into the server directory as [targetName].
     * Server must be STOPPED. Does not delete source.
     */
    suspend fun importWorldToServer(
        config: ServerConfig,
        sourceWorldDir: File,
        targetName: String = sourceWorldDir.name
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!sourceWorldDir.isDirectory) {
            return@withContext Result.failure(IllegalArgumentException("Not a world folder"))
        }
        if (!File(sourceWorldDir, "level.dat").exists() &&
            !File(sourceWorldDir, "level.dat_old").exists()
        ) {
            return@withContext Result.failure(
                IllegalArgumentException("No level.dat — not a valid Minecraft world")
            )
        }

        val dest = File(config.serverDir, targetName)
        if (dest.exists()) {
            return@withContext Result.failure(
                IllegalStateException("World '$targetName' already exists on this server")
            )
        }

        sourceWorldDir.copyRecursively(dest, overwrite = false)
        Result.success(dest)
    }

    /** Set level-name in server.properties to use this world on next start. */
    suspend fun setActiveWorld(config: ServerConfig, worldName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val worldDir = File(config.serverDir, worldName)
            if (!worldDir.exists()) {
                return@withContext Result.failure(IllegalArgumentException("World not found"))
            }
            val propsFile = File(config.serverDir, "server.properties")
            val updated = config.copy(worldDir = worldName)
            ServerPropertiesWriter.applyConfig(propsFile, updated)
            Result.success(Unit)
        }

    suspend fun deleteServerWorld(config: ServerConfig, worldName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (worldName == "." || worldName.contains("..")) {
                return@withContext Result.failure(IllegalArgumentException("Invalid name"))
            }
            val worldDir = File(config.serverDir, worldName)
            if (!worldDir.exists()) {
                return@withContext Result.failure(IllegalArgumentException("World not found"))
            }
            worldDir.deleteRecursively()
            Result.success(Unit)
        }

    private fun toWorldFolder(dir: File): WorldFolder {
        val size = dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        return WorldFolder(
            name = dir.name,
            path = dir.absolutePath,
            sizeBytes = size,
            lastModified = dir.lastModified(),
            hasLevelDat = File(dir, "level.dat").exists()
        )
    }
}
