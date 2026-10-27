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
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Search by Minecraft username → official Mojang profile textures.
 * Legitimate Mojang session endpoints. Not a bulk catalogue; query-driven.
 * Preview via Crafatar (public skin render CDN).
 */
@Singleton
class MojangUsernameSkinSource @Inject constructor(
    private val httpClient: OkHttpClient
) : CosmeticSource {

    override val id = "mojang_username"
    override val displayName = "Minecraft Username"
    override val supportedCategories = setOf(CosmeticCategory.SKINS)

    override suspend fun fetchPage(
        category: CosmeticCategory,
        query: String,
        page: Int,
        pageSize: Int
    ): SourcePage {
        if (category != CosmeticCategory.SKINS) {
            return SourcePage(emptyList(), page, false, id)
        }
        val username = query.trim()
        if (username.length < 3 || page > 0) {
            // Only one result per username; page>0 = empty
            return SourcePage(
                emptyList(), page, false, id,
                if (username.length < 3) "Type a Minecraft username (min 3 chars)" else null
            )
        }

        return try {
            val profile = resolveProfile(username)
                ?: return SourcePage(emptyList(), 0, false, id, "Player not found: $username")

            val uuid = profile.first
            val name = profile.second
            val skinUrl = "https://crafatar.com/skins/$uuid"
            val thumb = "https://crafatar.com/avatars/$uuid?size=128"

            val item = CosmeticItem(
                id = "mojang_$uuid",
                name = name,
                description = "Official skin for $name (Mojang profile). Local apply only unless MSA upload is used.",
                category = CosmeticCategory.SKINS,
                rarity = CosmeticRarity.COMMON,
                collection = CosmeticCollection.COMMUNITY,
                free = true,
                thumbnailPath = thumb,
                assetPath = skinUrl,
                skinModel = "classic"
            )
            SourcePage(listOf(item), 0, false, id)
        } catch (e: Exception) {
            Timber.e(e, "Mojang username skin")
            SourcePage(emptyList(), 0, false, id, e.message)
        }
    }

    private fun resolveProfile(username: String): Pair<String, String>? {
        // Mojang API: username → UUID
        val url = "https://api.mojang.com/users/profiles/minecraft/$username"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "NexusLauncherPro/1.0")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (resp.code == 404 || !resp.isSuccessful) return null
            val obj = JSONObject(resp.body?.string() ?: return null)
            val id = obj.getString("id")
            val name = obj.optString("name", username)
            // dashed uuid for crafatar
            val dashed = if (id.length == 32) {
                "${id.substring(0, 8)}-${id.substring(8, 12)}-${id.substring(12, 16)}-" +
                    "${id.substring(16, 20)}-${id.substring(20)}"
            } else id
            return dashed to name
        }
    }
}
