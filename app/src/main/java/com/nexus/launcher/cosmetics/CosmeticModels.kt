package com.nexus.launcher.cosmetics

enum class CosmeticCategory {
    SKINS,
    CAPES,
    WINGS,
    HATS,
    MASKS,
    GLASSES,
    SHOULDER_PETS,
    BACK_ACCESSORIES,
    ANIMATED,
    EMOTES,
    BANNERS,
    NAMEPLATES,
    TRAILS,
    BUNDLES
}

enum class CosmeticRarity {
    COMMON, RARE, EPIC, LEGENDARY, MYTHIC, LIMITED
}

enum class CosmeticCollection {
    CLASSIC_PVP, NEON, CYBER, FANTASY, ICE, FIRE, SHADOW, GALAXY, NATURE, SEASONAL, COMMUNITY, NEXUS_ORIGINALS
}

/**
 * Catalogue entry — metadata only.
 * Assets referenced by path/url; no fabricated popularity/ratings.
 */
data class CosmeticItem(
    val id: String,
    val name: String,
    val description: String,
    val category: CosmeticCategory,
    val rarity: CosmeticRarity,
    val collection: CosmeticCollection,
    val animated: Boolean = false,
    val free: Boolean = true,
    /** Relative path under cosmetics cache or asset pack id */
    val thumbnailPath: String? = null,
    val assetPath: String? = null,
    /** Skin model: classic | slim */
    val skinModel: String? = null
)

data class EquippedLoadout(
    val skinId: String? = null,
    val capeId: String? = null,
    val hatId: String? = null,
    val backId: String? = null,
    val wingsId: String? = null,
    val emoteSlots: List<String?> = List(6) { null }
)

data class SavedOutfit(
    val id: String,
    val name: String,
    val loadout: EquippedLoadout,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Visibility truth for UI labels.
 */
enum class CosmeticVisibility {
    /** Only local player / local client */
    LOCAL_ONLY,
    /** Official Mojang/Microsoft skin/cape path */
    OFFICIAL_ACCOUNT,
    /** Requires compatible Nexus cosmetics client on both sides — not implemented yet */
    NEXUS_PROTOCOL_REQUIRED
}
