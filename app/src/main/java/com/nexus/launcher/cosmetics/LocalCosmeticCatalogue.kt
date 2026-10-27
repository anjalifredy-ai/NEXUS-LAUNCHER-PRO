package com.nexus.launcher.cosmetics

/**
 * Built-in free starter catalogue (original Nexus metadata).
 * No scraped paid cosmetics. Expandable via future remote JSON.
 */
object LocalCosmeticCatalogue {

    fun all(): List<CosmeticItem> = skins() + capes() + hats() + emotes() + wings() + extras()

    private fun skins() = listOf(
        item("skin_nexus_default", "Nexus Default", "Starter classic skin placeholder", CosmeticCategory.SKINS, CosmeticRarity.COMMON, CosmeticCollection.NEXUS_ORIGINALS, skinModel = "classic"),
        item("skin_nexus_slim", "Nexus Slim", "Slim-arm starter placeholder", CosmeticCategory.SKINS, CosmeticRarity.COMMON, CosmeticCollection.NEXUS_ORIGINALS, skinModel = "slim"),
        item("skin_shadow_scout", "Shadow Scout", "Dark PvP-themed placeholder skin", CosmeticCategory.SKINS, CosmeticRarity.RARE, CosmeticCollection.SHADOW, skinModel = "classic"),
        item("skin_neon_runner", "Neon Runner", "Bright neon placeholder", CosmeticCategory.SKINS, CosmeticRarity.EPIC, CosmeticCollection.NEON, skinModel = "slim"),
        item("skin_ice_warden", "Ice Warden", "Cold fantasy placeholder", CosmeticCategory.SKINS, CosmeticRarity.RARE, CosmeticCollection.ICE, skinModel = "classic"),
        item("skin_cyber_ops", "Cyber Ops", "Cyber collection placeholder", CosmeticCategory.SKINS, CosmeticRarity.LEGENDARY, CosmeticCollection.CYBER, skinModel = "slim")
    )

    private fun capes() = listOf(
        item("cape_nexus_plain", "Nexus Plain", "Simple solid cape (local-only render)", CosmeticCategory.CAPES, CosmeticRarity.COMMON, CosmeticCollection.NEXUS_ORIGINALS),
        item("cape_shadow_veil", "Shadow Veil", "Dark cape placeholder", CosmeticCategory.CAPES, CosmeticRarity.RARE, CosmeticCollection.SHADOW),
        item("cape_galaxy_wave", "Galaxy Wave", "Animated-capable cape id", CosmeticCategory.CAPES, CosmeticRarity.EPIC, CosmeticCollection.GALAXY, animated = true),
        item("cape_neon_pulse", "Neon Pulse", "Animated neon cape id", CosmeticCategory.CAPES, CosmeticRarity.LEGENDARY, CosmeticCollection.NEON, animated = true),
        item("cape_fire_trail", "Ember Cape", "Fire collection cape", CosmeticCategory.CAPES, CosmeticRarity.RARE, CosmeticCollection.FIRE)
    )

    private fun hats() = listOf(
        item("hat_crown_simple", "Simple Crown", "Head accessory placeholder", CosmeticCategory.HATS, CosmeticRarity.RARE, CosmeticCollection.FANTASY),
        item("hat_beanie_nexus", "Nexus Beanie", "Casual headwear id", CosmeticCategory.HATS, CosmeticRarity.COMMON, CosmeticCollection.NEXUS_ORIGINALS),
        item("hat_horns_shadow", "Shadow Horns", "Accessory placeholder", CosmeticCategory.HATS, CosmeticRarity.EPIC, CosmeticCollection.SHADOW),
        item("glasses_cyber", "Cyber Visor", "Glasses category placeholder", CosmeticCategory.GLASSES, CosmeticRarity.RARE, CosmeticCollection.CYBER),
        item("mask_void", "Void Mask", "Mask placeholder", CosmeticCategory.MASKS, CosmeticRarity.EPIC, CosmeticCollection.SHADOW)
    )

    private fun wings() = listOf(
        item("wings_feather_light", "Light Feathers", "Back wings placeholder", CosmeticCategory.WINGS, CosmeticRarity.RARE, CosmeticCollection.NATURE),
        item("wings_shadow_span", "Shadow Span", "Dark wings id", CosmeticCategory.WINGS, CosmeticRarity.LEGENDARY, CosmeticCollection.SHADOW),
        item("back_pack_small", "Scout Pack", "Back accessory", CosmeticCategory.BACK_ACCESSORIES, CosmeticRarity.COMMON, CosmeticCollection.CLASSIC_PVP)
    )

    private fun emotes() = listOf(
        item("emote_wave", "Wave", "Greeting emote id", CosmeticCategory.EMOTES, CosmeticRarity.COMMON, CosmeticCollection.NEXUS_ORIGINALS),
        item("emote_cheer", "Cheer", "Celebration emote", CosmeticCategory.EMOTES, CosmeticRarity.COMMON, CosmeticCollection.NEXUS_ORIGINALS),
        item("emote_dance_basic", "Basic Dance", "Idle dance placeholder", CosmeticCategory.EMOTES, CosmeticRarity.RARE, CosmeticCollection.NEON),
        item("emote_sit", "Sit", "Idle sit", CosmeticCategory.EMOTES, CosmeticRarity.COMMON, CosmeticCollection.CLASSIC_PVP)
    )

    private fun extras() = listOf(
        item("banner_nexus", "Nexus Banner", "Profile banner metadata", CosmeticCategory.BANNERS, CosmeticRarity.COMMON, CosmeticCollection.NEXUS_ORIGINALS),
        item("nameplate_shadow", "Shadow Nameplate", "Decoration metadata only", CosmeticCategory.NAMEPLATES, CosmeticRarity.RARE, CosmeticCollection.SHADOW),
        item("trail_spark", "Spark Trail", "Effect id — needs client renderer", CosmeticCategory.TRAILS, CosmeticRarity.EPIC, CosmeticCollection.NEON, animated = true),
        item("pet_shoulder_cube", "Cube Pet", "Shoulder pet placeholder", CosmeticCategory.SHOULDER_PETS, CosmeticRarity.RARE, CosmeticCollection.CYBER),
        item("bundle_shadow_starter", "Shadow Starter Pack", "Bundle of shadow ids (metadata)", CosmeticCategory.BUNDLES, CosmeticRarity.EPIC, CosmeticCollection.SHADOW)
    )

    private fun item(
        id: String,
        name: String,
        description: String,
        category: CosmeticCategory,
        rarity: CosmeticRarity,
        collection: CosmeticCollection,
        animated: Boolean = false,
        skinModel: String? = null
    ) = CosmeticItem(
        id = id,
        name = name,
        description = description,
        category = category,
        rarity = rarity,
        collection = collection,
        animated = animated,
        free = true,
        skinModel = skinModel
    )
}
