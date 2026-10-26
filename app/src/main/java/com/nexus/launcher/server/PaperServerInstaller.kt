package com.nexus.launcher.server

import com.nexus.launcher.data.remote.PaperApi
import com.nexus.launcher.network.DownloadQueueManager
import com.nexus.launcher.server.model.ServerConfig
import com.nexus.launcher.server.model.ServerSoftware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs Paper dedicated server from official PaperMC API.
 * URL pattern: https://api.papermc.io/v2/projects/paper/versions/{ver}/builds/{build}/downloads/{name}
 */
@Singleton
class PaperServerInstaller @Inject constructor(
    private val paperApi: PaperApi,
    private val downloadQueueManager: DownloadQueueManager
) {
    data class InstallResult(
        val success: Boolean,
        val serverJar: File? = null,
        val buildNumber: Int? = null,
        val error: String? = null
    )

    suspend fun listSupportedVersions(): List<String> = withContext(Dispatchers.IO) {
        try {
            val res = paperApi.getProject()
            if (!res.isSuccessful) return@withContext emptyList()
            res.body()?.versions?.reversed() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun install(config: ServerConfig): InstallResult = withContext(Dispatchers.IO) {
        if (config.software != ServerSoftware.PAPER) {
            return@withContext InstallResult(false, error = "Not a Paper server")
        }

        val serverDir = File(config.serverDir)
        serverDir.mkdirs()

        try {
            val verRes = paperApi.getVersion(config.minecraftVersion)
            if (!verRes.isSuccessful) {
                return@withContext InstallResult(
                    false,
                    error = "Paper has no builds for ${config.minecraftVersion}"
                )
            }
            val builds = verRes.body()?.builds ?: emptyList()
            if (builds.isEmpty()) {
                return@withContext InstallResult(false, error = "No Paper builds for this version")
            }

            val buildNum = builds.last() // latest build
            val buildRes = paperApi.getBuild(config.minecraftVersion, buildNum)
            if (!buildRes.isSuccessful) {
                return@withContext InstallResult(false, error = "Failed to fetch Paper build $buildNum")
            }

            val download = buildRes.body()?.downloads?.get("application")
                ?: return@withContext InstallResult(false, error = "No application download in build")

            val fileName = download.name ?: "paper-${config.minecraftVersion}-$buildNum.jar"
            val url =
                "https://api.papermc.io/v2/projects/paper/versions/${config.minecraftVersion}/builds/$buildNum/downloads/$fileName"

            val jarFile = File(serverDir, "server.jar")
            downloadQueueManager.downloadFile(url, jarFile)

            if (!jarFile.exists() || jarFile.length() == 0L) {
                return@withContext InstallResult(false, error = "Paper jar download empty")
            }

            writeEulaAndDefaults(serverDir, config)
            // Paper uses plugins/ folder
            File(serverDir, "plugins").mkdirs()

            InstallResult(true, jarFile, buildNum)
        } catch (e: Exception) {
            InstallResult(false, error = e.message ?: "Paper install failed")
        }
    }

    private fun writeEulaAndDefaults(serverDir: File, config: ServerConfig) {
        if (config.eulaAccepted) {
            File(serverDir, "eula.txt").writeText("# Nexus Launcher\neula=true\n")
        }
        val props = File(serverDir, "server.properties")
        if (!props.exists()) {
            ServerPropertiesWriter.writeDefaults(props, config)
        }
    }
}
