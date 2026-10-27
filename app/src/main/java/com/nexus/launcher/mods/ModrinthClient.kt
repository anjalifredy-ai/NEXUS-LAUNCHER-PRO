package com.nexus.launcher.mods

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class ModrinthSearchHit(
    val projectId: String,
    val slug: String,
    val title: String,
    val description: String,
    val downloads: Int,
    val iconUrl: String?,
    val categories: List<String>
)

data class ModrinthVersionFile(
    val versionId: String,
    val versionNumber: String,
    val downloadUrl: String,
    val fileName: String,
    val primary: Boolean
)

/**
 * Minimal Modrinth API client for search + latest file URL.
 * https://docs.modrinth.com/
 */
@Singleton
class ModrinthClient @Inject constructor(
    private val httpClient: OkHttpClient
) {
    fun search(query: String, limit: Int = 20): List<ModrinthSearchHit> {
        val q = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://api.modrinth.com/v2/search?query=$q&limit=$limit&index=relevance"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "NexusLauncherPro/1.0")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string() ?: return emptyList()
            val hits = JSONObject(body).optJSONArray("hits") ?: return emptyList()
            return buildList {
                for (i in 0 until hits.length()) {
                    val o = hits.getJSONObject(i)
                    add(
                        ModrinthSearchHit(
                            projectId = o.optString("project_id"),
                            slug = o.optString("slug"),
                            title = o.optString("title"),
                            description = o.optString("description"),
                            downloads = o.optInt("downloads"),
                            iconUrl = o.optString("icon_url").ifBlank { null },
                            categories = o.optJSONArray("categories")?.toStringList() ?: emptyList()
                        )
                    )
                }
            }
        }
    }

    fun latestPrimaryFile(projectId: String, gameVersion: String? = null): ModrinthVersionFile? {
        var url = "https://api.modrinth.com/v2/project/$projectId/version?limit=5"
        if (!gameVersion.isNullOrBlank()) {
            val gv = java.net.URLEncoder.encode("[\"$gameVersion\"]", Charsets.UTF_8.name())
            url += "&game_versions=$gv"
        }
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
                    return ModrinthVersionFile(
                        versionId = ver.optString("id"),
                        versionNumber = ver.optString("version_number"),
                        downloadUrl = f.getString("url"),
                        fileName = f.getString("filename"),
                        primary = true
                    )
                }
            }
            return null
        }
    }

    private fun JSONArray.toStringList(): List<String> =
        buildList { for (i in 0 until length()) add(getString(i)) }
}
