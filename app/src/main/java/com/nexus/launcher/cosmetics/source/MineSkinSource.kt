package com.nexus.launcher.cosmetics.source

import com.nexus.launcher.cosmetics.CosmeticCategory
import com.nexus.launcher.cosmetics.CosmeticCollection
import com.nexus.launcher.cosmetics.CosmeticItem
import com.nexus.launcher.cosmetics.CosmeticRarity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MineSkin public list API — community skins with pagination.
 * Docs-oriented: GET https://api.mineskin.org/get/list/{page}
 * Only provides SKINS (not capes/emotes).
 * Attribution: skins generated/uploaded via MineSkin; respect their ToS/rate limits.
 */
@Singleton
class MineSkinSource @Inject constructor(
    private val httpClient: OkHttpClient
) : CosmeticSource {

    override val id = "mineskin"
    override val displayName = "MineSkin"
    override val supportedCategories = setOf(CosmeticCategory.SKINS)

    override suspend fun fetchPage(
        category: CosmeticCategory,
        query: String,
        page: Int,
        pageSize: Int
    ): SourcePage {
        if (category != CosmeticCategory.SKINS) {
            return SourcePage(emptyList(), page, false, id, "MineSkin only provides skins")
        }

        return try {
            // MineSkin list endpoint (page index)
            val url = "https://api.mineskin.org/get/list/$page"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "NexusLauncherPro/1.0 (cosmetics; +https://github.com/anjalifredy-ai/NEXUS-LAUNCHER-PRO)")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return SourcePage(
                        emptyList(), page, false, id,
                        "MineSkin HTTP ${resp.code}"
                    )
                }
                val body = resp.body?.string() ?: return SourcePage(emptyList(), page, false, id, "Empty body")
                val root = JSONObject(body)
                val skins: JSONArray = when {
                    root.has("skins") -> root.getJSONArray("skins")
                    root.has("data") && root.get("data") is JSONArray -> root.getJSONArray("data")
                    else -> JSONArray()
                }

                val q = query.trim().lowercase()
                val items = buildList {
                    for (i in 0 until skins.length()) {
                        val s = skins.getJSONObject(i)
                        val name = s.optString("name").ifBlank {
                            s.optString("id", "skin_$i")
                        }
                        if (q.isNotEmpty() && !name.lowercase().contains(q)) continue

                        val idStr = s.optString("uuid").ifBlank {
                            s.optString("id", "ms_${page}_$i")
                        }
                        val textureUrl = s.optJSONObject("data")?.optJSONObject("texture")?.optString("url")
                            ?: s.optString("url").ifBlank { null }
                            ?: s.optJSONObject("urls")?.optString("skin")

                        add(
                            CosmeticItem(
                                id = "mineskin_$idStr",
                                name = name.take(64),
                                description = "MineSkin community skin · page $page",
                                category = CosmeticCategory.SKINS,
                                rarity = CosmeticRarity.COMMON,
                                collection = CosmeticCollection.COMMUNITY,
                                free = true,
                                thumbnailPath = textureUrl,
                                assetPath = textureUrl,
                                skinModel = if (s.optBoolean("steve", true)) "classic" else "slim"
                            )
                        )
                    }
                }

                // If we got a full-ish page, assume more may exist
                val hasMore = skins.length() > 0
                SourcePage(items.take(pageSize), page, hasMore, id)
            }
        } catch (e: Exception) {
            Timber.e(e, "MineSkin fetch")
            SourcePage(emptyList(), page, false, id, e.message ?: "MineSkin error")
        }
    }
}
