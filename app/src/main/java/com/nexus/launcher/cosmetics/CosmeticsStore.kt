package com.nexus.launcher.cosmetics

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nexus.launcher.filesystem.FileSystemManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local cosmetics store + wardrobe persistence.
 * No fake currency. No multiplayer sync claimed.
 */
@Singleton
class CosmeticsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileSystemManager: FileSystemManager,
    private val gson: Gson
) {
    private val prefs = context.getSharedPreferences("nexus_cosmetics", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow(loadStringSet("favorites"))
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _loadout = MutableStateFlow(loadLoadout())
    val loadout: StateFlow<EquippedLoadout> = _loadout.asStateFlow()

    private val _outfits = MutableStateFlow(loadOutfits())
    val outfits: StateFlow<List<SavedOutfit>> = _outfits.asStateFlow()

    private val _recent = MutableStateFlow(loadStringList("recent"))
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    fun catalogue(): List<CosmeticItem> = LocalCosmeticCatalogue.all()

    fun search(
        query: String = "",
        category: CosmeticCategory? = null,
        rarity: CosmeticRarity? = null,
        collection: CosmeticCollection? = null,
        ownedOnly: Boolean = false,
        favoritesOnly: Boolean = false,
        animatedOnly: Boolean = false
    ): List<CosmeticItem> {
        val q = query.trim().lowercase()
        val fav = _favorites.value
        return catalogue().filter { item ->
            (q.isEmpty() || item.name.lowercase().contains(q) || item.id.contains(q)) &&
                (category == null || item.category == category) &&
                (rarity == null || item.rarity == rarity) &&
                (collection == null || item.collection == collection) &&
                (!animatedOnly || item.animated) &&
                (!favoritesOnly || item.id in fav) &&
                (!ownedOnly || item.free || isImportedSkin(item.id))
        }
    }

    fun toggleFavorite(id: String) {
        val next = _favorites.value.toMutableSet()
        if (!next.add(id)) next.remove(id)
        _favorites.value = next
        prefs.edit().putStringSet("favorites", next).apply()
    }

    fun markViewed(id: String) {
        val list = _recent.value.toMutableList()
        list.remove(id)
        list.add(0, id)
        val trimmed = list.take(30)
        _recent.value = trimmed
        prefs.edit().putString("recent", gson.toJson(trimmed)).apply()
    }

    fun equip(category: CosmeticCategory, itemId: String?) {
        val cur = _loadout.value
        val next = when (category) {
            CosmeticCategory.SKINS -> cur.copy(skinId = itemId)
            CosmeticCategory.CAPES -> cur.copy(capeId = itemId)
            CosmeticCategory.HATS, CosmeticCategory.MASKS, CosmeticCategory.GLASSES ->
                cur.copy(hatId = itemId)
            CosmeticCategory.WINGS -> cur.copy(wingsId = itemId)
            CosmeticCategory.BACK_ACCESSORIES -> cur.copy(backId = itemId)
            else -> cur
        }
        _loadout.value = next
        saveLoadout(next)
        itemId?.let { markViewed(it) }
    }

    fun setEmoteSlot(index: Int, emoteId: String?) {
        if (index !in 0..5) return
        val slots = _loadout.value.emoteSlots.toMutableList()
        while (slots.size < 6) slots.add(null)
        slots[index] = emoteId
        val next = _loadout.value.copy(emoteSlots = slots)
        _loadout.value = next
        saveLoadout(next)
    }

    fun saveOutfit(name: String): SavedOutfit {
        val outfit = SavedOutfit(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Outfit" },
            loadout = _loadout.value
        )
        val list = _outfits.value + outfit
        _outfits.value = list
        prefs.edit().putString("outfits", gson.toJson(list)).apply()
        return outfit
    }

    fun applyOutfit(id: String) {
        val outfit = _outfits.value.find { it.id == id } ?: return
        _loadout.value = outfit.loadout
        saveLoadout(outfit.loadout)
    }

    fun deleteOutfit(id: String) {
        val list = _outfits.value.filterNot { it.id == id }
        _outfits.value = list
        prefs.edit().putString("outfits", gson.toJson(list)).apply()
    }

    fun resetLoadout() {
        val empty = EquippedLoadout()
        _loadout.value = empty
        saveLoadout(empty)
    }

    fun importedSkinsDir(): File =
        File(fileSystemManager.minecraftDir, "nexus_cosmetics/skins").also { it.mkdirs() }

    suspend fun importSkin(source: File, displayName: String? = null): Result<CosmeticItem> =
        withContext(Dispatchers.IO) {
            val validation = SkinImportValidator.validate(source)
            if (!validation.ok) return@withContext Result.failure(IllegalArgumentException(validation.message))

            val id = "imported_${UUID.randomUUID().toString().take(8)}"
            val dest = File(importedSkinsDir(), "$id.png")
            source.copyTo(dest, overwrite = true)

            val item = CosmeticItem(
                id = id,
                name = displayName ?: source.nameWithoutExtension,
                description = "Imported local skin (${validation.width}×${validation.height})",
                category = CosmeticCategory.SKINS,
                rarity = CosmeticRarity.COMMON,
                collection = CosmeticCollection.COMMUNITY,
                free = true,
                assetPath = dest.absolutePath,
                thumbnailPath = dest.absolutePath,
                skinModel = validation.modelHint
            )
            // Persist imported index
            val imported = loadImported() + item
            prefs.edit().putString("imported_skins", gson.toJson(imported)).apply()
            equip(CosmeticCategory.SKINS, id)
            Result.success(item)
        }

    fun importedItems(): List<CosmeticItem> = loadImported()

    fun isImportedSkin(id: String): Boolean = loadImported().any { it.id == id }

    fun visibilityFor(category: CosmeticCategory): CosmeticVisibility = when (category) {
        CosmeticCategory.SKINS -> CosmeticVisibility.LOCAL_ONLY // offline; MSA path not wired yet
        else -> CosmeticVisibility.NEXUS_PROTOCOL_REQUIRED
    }

    fun visibilityNote(): String =
        "Local cosmetics are visible on this device only. Other players need a compatible Nexus " +
            "cosmetics client (protocol not shipped yet). Official Microsoft skin changes need MSA login."

    private fun loadLoadout(): EquippedLoadout {
        val raw = prefs.getString("loadout", null) ?: return EquippedLoadout()
        return try {
            gson.fromJson(raw, EquippedLoadout::class.java) ?: EquippedLoadout()
        } catch (_: Exception) {
            EquippedLoadout()
        }
    }

    private fun saveLoadout(l: EquippedLoadout) {
        prefs.edit().putString("loadout", gson.toJson(l)).apply()
    }

    private fun loadOutfits(): List<SavedOutfit> {
        val raw = prefs.getString("outfits", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SavedOutfit>>() {}.type
            gson.fromJson(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadImported(): List<CosmeticItem> {
        val raw = prefs.getString("imported_skins", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<CosmeticItem>>() {}.type
            gson.fromJson(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadStringSet(key: String): Set<String> =
        prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    private fun loadStringList(key: String): List<String> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
