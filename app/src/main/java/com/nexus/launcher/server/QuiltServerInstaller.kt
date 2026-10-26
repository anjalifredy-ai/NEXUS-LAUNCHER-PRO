package com.nexus.launcher.server

import com.nexus.launcher.network.DownloadQueueManager
import com.nexus.launcher.server.model.ServerConfig
import com.nexus.launcher.server.model.ServerSoftware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Quilt dedicated server via meta.quiltmc.org installer jar endpoint.
 */
@Singleton
class QuiltServerInstaller @Inject constructor(
    private val downloadQueueManager: DownloadQueueManager,
    private val httpClient: OkHttpClient
) {
    data class InstallResult(val success: Boolean, val message: String, val loader: String? = null)

    suspend fun install(config: ServerConfig): InstallResult = withContext(Dispatchers.IO) {
        if (config.software != ServerSoftware.QUILT) {
            return@withContext InstallResult(false, "Not Quilt")
        }

        val serverDir = File(config.serverDir).also { it.mkdirs() }
        File(serverDir, "mods").mkdirs()

        try {
            val loader = resolveLatestLoader(config.minecraftVersion)
                ?: return@withContext InstallResult(
                    false,
                    "No Quilt loader for ${config.minecraftVersion}"
                )

            // Quilt server launch jar similar to Fabric meta pattern
            val jarUrl =
                "https://meta.quiltmc.org/v3/versions/loader/${config.minecraftVersion}/$loader/server/jar"

            val jarFile = File(serverDir, "server.jar")
            downloadQueueManager.downloadFile(jarUrl, jarFile)

            if (!jarFile.exists() || jarFile.length() == 0L) {
                // Fallback: download installer and note
                return@withContext InstallResult(
                    false,
                    "Quilt server jar empty for loader $loader"
                )
            }

            if (config.eulaAccepted) {
                File(serverDir, "eula.txt").writeText("# Nexus\neula=true\n")
            }
            val props = File(serverDir, "server.properties")
            if (!props.exists()) ServerPropertiesWriter.writeDefaults(props, config)

            InstallResult(true, "Quilt loader $loader installed", loader)
        } catch (e: Exception) {
            InstallResult(false, e.message ?: "Quilt install failed")
        }
    }

    private fun resolveLatestLoader(mcVersion: String): String? {
        val url = "https://meta.quiltmc.org/v3/versions/loader/$mcVersion"
        return try {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val arr = JSONArray(body)
                if (arr.length() == 0) return null
                val first = arr.getJSONObject(0)
                first.optJSONObject("loader")?.optString("version")
                    ?: first.optString("version", null)
            }
        } catch (_: Exception) {
            null
        }
    }
}
