package com.nexus.launcher.voice

import com.nexus.launcher.mods.ModrinthClient
import com.nexus.launcher.network.DownloadQueueManager
import com.nexus.launcher.server.model.ServerConfig
import com.nexus.launcher.server.model.ServerSoftware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs official Simple Voice Chat from Modrinth into a profile mods folder
 * or server mods folder. Does not invent a custom protocol.
 */
@Singleton
class VoiceChatInstaller @Inject constructor(
    private val modrinthClient: ModrinthClient,
    private val downloadQueueManager: DownloadQueueManager,
    private val httpClient: OkHttpClient
) {
    data class InstallResult(
        val success: Boolean,
        val message: String,
        val file: File? = null,
        val versionNumber: String? = null
    )

    /**
     * @param loader fabric | quilt | forge | neoforge
     * @param modsDir profile or server mods directory
     */
    suspend fun installClientMod(
        minecraftVersion: String,
        loader: String,
        modsDir: File
    ): InstallResult = withContext(Dispatchers.IO) {
        val support = VoiceChatCompatibility.clientSupport(loader, minecraftVersion)
        if (support.level == VoiceChatCompatibility.SupportLevel.UNSUPPORTED) {
            return@withContext InstallResult(false, support.reason ?: "Unsupported")
        }

        modsDir.mkdirs()
        // Already installed?
        val existing = modsDir.listFiles()?.firstOrNull {
            it.name.contains("voice-chat", ignoreCase = true) ||
                it.name.contains("voicechat", ignoreCase = true)
        }
        if (existing != null && existing.length() > 0) {
            return@withContext InstallResult(
                true,
                "Already installed: ${existing.name}",
                existing
            )
        }

        val file = resolveModrinthFile(minecraftVersion, loader)
            ?: return@withContext InstallResult(
                false,
                "No Simple Voice Chat build for $minecraftVersion / $loader on Modrinth"
            )

        val dest = File(modsDir, file.fileName)
        try {
            downloadQueueManager.downloadFile(file.downloadUrl, dest)
            if (!dest.exists() || dest.length() == 0L) {
                return@withContext InstallResult(false, "Download empty")
            }
            InstallResult(true, "Installed ${file.fileName}", dest, file.versionNumber)
        } catch (e: Exception) {
            Timber.e(e, "SVC install")
            dest.delete()
            InstallResult(false, e.message ?: "Install failed")
        }
    }

    suspend fun installServerComponent(config: ServerConfig): InstallResult {
        val support = VoiceChatCompatibility.serverSupport(config.software, config.minecraftVersion)
        if (support.level == VoiceChatCompatibility.SupportLevel.UNSUPPORTED) {
            return InstallResult(false, support.reason ?: "Unsupported server software")
        }

        val loader = when (config.software) {
            ServerSoftware.FABRIC -> "fabric"
            ServerSoftware.QUILT -> "quilt"
            ServerSoftware.FORGE -> "forge"
            ServerSoftware.NEOFORGE -> "neoforge"
            else -> return InstallResult(false, "No server voice component for ${config.software}")
        }

        val modsDir = File(config.serverDir, "mods")
        return installClientMod(config.minecraftVersion, loader, modsDir)
    }

    suspend fun uninstallFrom(modsDir: File): InstallResult = withContext(Dispatchers.IO) {
        val removed = modsDir.listFiles()?.filter {
            it.name.contains("voice-chat", ignoreCase = true) ||
                it.name.contains("voicechat", ignoreCase = true)
        }.orEmpty()
        if (removed.isEmpty()) return@withContext InstallResult(true, "Nothing to remove")
        removed.forEach { it.delete() }
        InstallResult(true, "Removed: ${removed.joinToString { it.name }}")
    }

    fun isInstalled(modsDir: File): Boolean {
        return modsDir.listFiles()?.any {
            it.name.contains("voice-chat", ignoreCase = true) ||
                it.name.contains("voicechat", ignoreCase = true)
        } == true
    }

    private fun resolveModrinthFile(
        minecraftVersion: String,
        loader: String
    ): ModFile? {
        // Prefer ModrinthClient; fall back to direct API with loaders filter
        modrinthClient.latestPrimaryFile(
            projectId = resolveProjectId(),
            gameVersion = minecraftVersion
        )?.let { f ->
            return ModFile(f.fileName, f.downloadUrl, f.versionNumber)
        }

        // Filtered version list
        val loaders = java.net.URLEncoder.encode("[\"$loader\"]", Charsets.UTF_8.name())
        val versions = java.net.URLEncoder.encode("[\"$minecraftVersion\"]", Charsets.UTF_8.name())
        val url =
            "https://api.modrinth.com/v2/project/${VoiceChatCompatibility.MODRINTH_PROJECT}/version" +
                "?loaders=$loaders&game_versions=$versions&limit=5"

        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "NexusLauncherPro/1.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val arr = JSONArray(resp.body?.string() ?: return null)
                if (arr.length() == 0) return null
                val ver = arr.getJSONObject(0)
                val files = ver.optJSONArray("files") ?: return null
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    if (f.optBoolean("primary", i == 0)) {
                        return ModFile(
                            f.getString("filename"),
                            f.getString("url"),
                            ver.optString("version_number")
                        )
                    }
                }
                null
            }
        } catch (e: Exception) {
            Timber.e(e)
            null
        }
    }

    private fun resolveProjectId(): String {
        // slug works for many Modrinth endpoints; if id needed, search once
        return try {
            val url = "https://api.modrinth.com/v2/project/${VoiceChatCompatibility.MODRINTH_PROJECT}"
            val req = Request.Builder().url(url).header("User-Agent", "NexusLauncherPro/1.0").build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return VoiceChatCompatibility.MODRINTH_PROJECT
                JSONObject(resp.body?.string() ?: return VoiceChatCompatibility.MODRINTH_PROJECT)
                    .optString("id", VoiceChatCompatibility.MODRINTH_PROJECT)
            }
        } catch (_: Exception) {
            VoiceChatCompatibility.MODRINTH_PROJECT
        }
    }

    private data class ModFile(val fileName: String, val downloadUrl: String, val versionNumber: String)
}
