package com.nexus.launcher.server

import com.nexus.launcher.network.DownloadQueueManager
import com.nexus.launcher.server.model.ServerConfig
import com.nexus.launcher.server.model.ServerSoftware
import com.nexus.launcher.version.VersionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs a Vanilla Minecraft dedicated server jar into the server directory.
 * Uses the official server download from the version JSON when available.
 */
@Singleton
class VanillaServerInstaller @Inject constructor(
    private val versionManager: VersionManager,
    private val downloadQueueManager: DownloadQueueManager
) {
    data class InstallResult(
        val success: Boolean,
        val serverJar: File?,
        val error: String? = null
    )

    suspend fun install(config: ServerConfig): InstallResult = withContext(Dispatchers.IO) {
        if (config.software != ServerSoftware.VANILLA) {
            return@withContext InstallResult(false, null, "This installer only supports Vanilla")
        }

        val serverDir = File(config.serverDir)
        serverDir.mkdirs()

        try {
            val versionJson = versionManager.getVersionDetails(config.minecraftVersion)
            val serverDownload = versionJson.downloads?.server
                ?: return@withContext InstallResult(
                    false, null,
                    "No server jar listed for ${config.minecraftVersion}. This version may not publish a dedicated server."
                )

            val jarFile = File(serverDir, "server.jar")
            if (jarFile.exists() && jarFile.length() == serverDownload.size) {
                ensureEulaAndProperties(serverDir, config)
                return@withContext InstallResult(true, jarFile)
            }

            downloadQueueManager.downloadFile(serverDownload.url, jarFile)

            if (!jarFile.exists() || jarFile.length() == 0L) {
                return@withContext InstallResult(false, null, "Download finished but server.jar is missing or empty")
            }

            ensureEulaAndProperties(serverDir, config)
            InstallResult(true, jarFile)
        } catch (e: Exception) {
            InstallResult(false, null, e.message ?: "Vanilla server install failed")
        }
    }

    private fun ensureEulaAndProperties(serverDir: File, config: ServerConfig) {
        val eula = File(serverDir, "eula.txt")
        if (config.eulaAccepted) {
            eula.writeText("# Accepted by user in Nexus Launcher\neula=true\n")
        } else if (!eula.exists()) {
            eula.writeText("# By changing the setting below to TRUE you are indicating your agreement to the EULA\n# https://aka.ms/MinecraftEULA\neula=false\n")
        }

        val props = File(serverDir, "server.properties")
        if (!props.exists()) {
            props.writeText(
                """
                |motd=${config.motd}
                |server-port=${config.port}
                |server-ip=
                |max-players=${config.maxPlayers}
                |online-mode=${config.onlineMode}
                |white-list=${config.whitelist}
                |difficulty=${config.difficulty}
                |gamemode=${config.gamemode}
                |view-distance=${config.viewDistance}
                |simulation-distance=${config.simulationDistance}
                |pvp=${config.pvp}
                |hardcore=${config.hardcore}
                |allow-flight=${config.allowFlight}
                |enable-command-block=${config.enableCommandBlocks}
                |spawn-protection=${config.spawnProtection}
                |level-name=${config.worldDir}
                |max-world-size=29999984
                |spawn-monsters=true
                |spawn-animals=true
                |spawn-npcs=true
                |
                """.trimMargin()
            )
        }
    }
}
