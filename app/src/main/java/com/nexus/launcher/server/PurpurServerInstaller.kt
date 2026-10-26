package com.nexus.launcher.server

import com.nexus.launcher.network.DownloadQueueManager
import com.nexus.launcher.server.model.ServerConfig
import com.nexus.launcher.server.model.ServerSoftware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Purpur server jar from api.purpurmc.org
 */
@Singleton
class PurpurServerInstaller @Inject constructor(
    private val downloadQueueManager: DownloadQueueManager,
    private val httpClient: OkHttpClient
) {
    data class InstallResult(val success: Boolean, val message: String, val build: String? = null)

    suspend fun install(config: ServerConfig): InstallResult = withContext(Dispatchers.IO) {
        if (config.software != ServerSoftware.PURPUR) {
            return@withContext InstallResult(false, "Not Purpur")
        }

        val serverDir = File(config.serverDir).also { it.mkdirs() }
        File(serverDir, "plugins").mkdirs()

        try {
            val buildsUrl = "https://api.purpurmc.org/v2/purpur/${config.minecraftVersion}"
            val req = Request.Builder().url(buildsUrl).build()
            val build = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext InstallResult(
                    false,
                    "Purpur has no builds for ${config.minecraftVersion}"
                )
                val json = JSONObject(resp.body?.string() ?: return@withContext InstallResult(false, "Empty Purpur response"))
                json.optJSONObject("builds")?.optString("latest")
                    ?: return@withContext InstallResult(false, "No latest Purpur build")
            }

            val jarUrl =
                "https://api.purpurmc.org/v2/purpur/${config.minecraftVersion}/$build/download"
            val jar = File(serverDir, "server.jar")
            downloadQueueManager.downloadFile(jarUrl, jar)

            if (!jar.exists() || jar.length() == 0L) {
                return@withContext InstallResult(false, "Purpur jar empty")
            }

            if (config.eulaAccepted) {
                File(serverDir, "eula.txt").writeText("# Nexus\neula=true\n")
            }
            val props = File(serverDir, "server.properties")
            if (!props.exists()) ServerPropertiesWriter.writeDefaults(props, config)

            InstallResult(true, "Purpur build $build installed", build)
        } catch (e: Exception) {
            InstallResult(false, e.message ?: "Purpur install failed")
        }
    }
}
