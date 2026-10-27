package com.nexus.launcher.cosmetics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class EquipResult(
    val success: Boolean,
    val message: String,
    val localFile: File? = null
)

/**
 * Resolves catalogue item → local file → profile skin apply.
 * Capes/accessories: metadata equip only until client renderer exists.
 */
@Singleton
class CosmeticEquipService @Inject constructor(
    private val store: CosmeticsStore,
    private val assetCache: CosmeticAssetCache
) {
    suspend fun equipItem(item: CosmeticItem): EquipResult = withContext(Dispatchers.IO) {
        store.markViewed(item.id)
        store.equip(item.category, item.id)

        when (item.category) {
            CosmeticCategory.SKINS -> equipSkin(item)
            else -> EquipResult(
                true,
                "Equipped ${item.name} in wardrobe (local metadata). " +
                    "In-game render needs Nexus cosmetics client — not claimed here."
            )
        }
    }

    private suspend fun equipSkin(item: CosmeticItem): EquipResult {
        // Imported path already on disk
        item.assetPath?.let { path ->
            if (!path.startsWith("http", ignoreCase = true)) {
                val f = File(path)
                if (f.exists()) {
                    val applied = assetCache.applyLocalSkin(f)
                    return applied.fold(
                        onSuccess = {
                            EquipResult(true, "Local skin applied: ${it.absolutePath}", it)
                        },
                        onFailure = { EquipResult(false, it.message ?: "Apply failed") }
                    )
                }
            }
        }

        val url = item.assetPath ?: item.thumbnailPath
        if (url.isNullOrBlank() || !url.startsWith("http", ignoreCase = true)) {
            return EquipResult(
                true,
                "Skin id saved in wardrobe. No downloadable URL for this entry."
            )
        }

        val downloaded = assetCache.ensureDownloaded(url)
        return downloaded.fold(
            onSuccess = { file ->
                val applied = assetCache.applyLocalSkin(file)
                applied.fold(
                    onSuccess = {
                        EquipResult(true, "Downloaded & applied skin → ${it.name}", it)
                    },
                    onFailure = {
                        EquipResult(false, "Downloaded but apply failed: ${it.message}", file)
                    }
                )
            },
            onFailure = {
                EquipResult(false, "Download failed: ${it.message}")
            }
        )
    }
}
