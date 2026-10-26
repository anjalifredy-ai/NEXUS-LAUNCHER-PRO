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
 * Installs ViaVersion + ViaBackwards from Modrinth into Paper/Spigot/Purpur plugins/.
 *
 * Effect: clients on nearby protocol versions (e.g. 1.21.1, 1.21.3, 1.21.4)
 * can join a server running a specific 1.21.x build without changing the server jar.
 *
 * This is the real way multi-version join works — not by ignoring protocol.
 */
@Singleton
class ViaVersionInstaller @Inject constructor(
    private val downloadQueueManager: DownloadQueueManager,
    private val httpClient: OkHttpClient
) {
    data class Result(val success: Boolean, val message: String)

    private val viaVersionProject = "viaversion"
    private val viaBackwardsProject = "viabackwards"

    suspend fun installForCrossVersion(config: ServerConfig): Result = withContext(Dispatchers.IO) {
        if (config.software !in listOf(
                ServerSoftware.PAPER,
                ServerSoftware.SPIGOT,
                ServerSoftware.PURPUR
            )
        ) {
            return@withContext Result(
                false,
                "Cross-version join (ViaVersion) needs Paper/Spigot/Purpur. Fabric uses different mods."
            )
        }

        val plugins = File(config.serverDir, "plugins").also { it.mkdirs() }

        try {
            val vv = downloadLatestModrinthPlugin(viaVersionProject, plugins, "ViaVersion.jar")
            val vb = downloadLatestModrinthPlugin(viaBackwardsProject, plugins, "ViaBackwards.jar")
            if (vv && vb) {
                Result(
                    true,
                    "ViaVersion + ViaBackwards installed. Nearby client versions can join after restart."
                )
            } else {
                Result(false, "Could not download Via plugins from Modrinth")
            }
        } catch (e: Exception) {
            Result(false, e.message ?: "ViaVersion install failed")
        }
    }

    private fun downloadLatestModrinthPlugin(
        projectSlug: String,
        pluginsDir: File,
        fileName: String
    ): Boolean {
        // Modrinth: GET /v2/project/{id|slug}/version?loaders=["paper","bukkit"]
        val api =
            "https://api.modrinth.com/v2/project/$projectSlug/version?loaders=%5B%22paper%22%2C%22bukkit%22%2C%22spigot%22%5D&limit=5"
        val req = Request.Builder()
            .url(api)
            .header("User-Agent", "NexusLauncherPro/1.0")
            .build()

        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return false
            val body = resp.body?.string() ?: return false
            val arr = org.json.JSONArray(body)
            if (arr.length() == 0) return false

            val first = arr.getJSONObject(0)
            val files = first.getJSONArray("files")
            if (files.length() == 0) return false

            var url: String? = null
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                if (f.optBoolean("primary", false) || url == null) {
                    url = f.getString("url")
                }
            }
            url ?: return false

            val target = File(pluginsDir, fileName)
            downloadQueueManager.downloadFile(url, target)
            return target.exists() && target.length() > 0
        }
    }
}
