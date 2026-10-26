package com.nexus.launcher.server

import com.nexus.launcher.network.DownloadQueueManager
import com.nexus.launcher.server.model.ServerConfig
import com.nexus.launcher.server.model.ServerSoftware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs Fabric dedicated server using official Fabric meta installer jar.
 * Docs: https://fabricmc.net/use/server/
 *
 * Flow: download fabric-server-launcher jar for MC+loader, run once is done by user start;
 * we place the launcher jar as server.jar and create mods/ folder.
 */
@Singleton
class FabricServerInstaller @Inject constructor(
    private val downloadQueueManager: DownloadQueueManager,
    private val httpClient: OkHttpClient
) {
    data class InstallResult(
        val success: Boolean,
        val serverJar: File? = null,
        val loaderVersion: String? = null,
        val error: String? = null
    )

    suspend fun getLatestLoaderVersion(minecraftVersion: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val url =
                    "https://meta.fabricmc.net/v2/versions/loader/$minecraftVersion"
                val req = Request.Builder().url(url).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val body = resp.body?.string() ?: return@withContext null
                    // Simple parse first loader version string
                    val regex = "\"version\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    regex.find(body)?.groupValues?.get(1)
                }
            } catch (_: Exception) {
                null
            }
        }

    suspend fun install(
        config: ServerConfig,
        loaderVersion: String? = null
    ): InstallResult = withContext(Dispatchers.IO) {
        if (config.software != ServerSoftware.FABRIC) {
            return@withContext InstallResult(false, error = "Not a Fabric server")
        }

        val serverDir = File(config.serverDir)
        serverDir.mkdirs()
        File(serverDir, "mods").mkdirs()

        try {
            val loader = loaderVersion
                ?: getLatestLoaderVersion(config.minecraftVersion)
                ?: return@withContext InstallResult(
                    false,
                    error = "No Fabric loader for ${config.minecraftVersion}"
                )

            // Official server launcher jar from Fabric meta
            val jarUrl =
                "https://meta.fabricmc.net/v2/versions/loader/${config.minecraftVersion}/$loader/1.0.0/server/jar"

            val jarFile = File(serverDir, "server.jar")
            downloadQueueManager.downloadFile(jarUrl, jarFile)

            if (!jarFile.exists() || jarFile.length() == 0L) {
                return@withContext InstallResult(false, error = "Fabric server jar empty")
            }

            if (config.eulaAccepted) {
                File(serverDir, "eula.txt").writeText("# Nexus\neula=true\n")
            }
            val props = File(serverDir, "server.properties")
            if (!props.exists()) {
                ServerPropertiesWriter.writeDefaults(props, config)
            }

            InstallResult(true, jarFile, loader)
        } catch (e: Exception) {
            InstallResult(false, error = e.message ?: "Fabric server install failed")
        }
    }
}
