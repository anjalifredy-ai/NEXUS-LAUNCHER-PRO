package com.nexus.launcher.cosmetics.source

import com.nexus.launcher.cosmetics.CosmeticCategory
import com.nexus.launcher.cosmetics.LocalCosmeticCatalogue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalCatalogueSource @Inject constructor() : CosmeticSource {
    override val id = "local"
    override val displayName = "Nexus Local"
    override val supportedCategories: Set<CosmeticCategory> =
        CosmeticCategory.entries.toSet()

    override suspend fun fetchPage(
        category: CosmeticCategory,
        query: String,
        page: Int,
        pageSize: Int
    ): SourcePage {
        val q = query.trim().lowercase()
        val all = LocalCosmeticCatalogue.all().filter {
            it.category == category &&
                (q.isEmpty() || it.name.lowercase().contains(q) || it.id.contains(q))
        }
        val from = page * pageSize
        if (from >= all.size) {
            return SourcePage(emptyList(), page, false, id)
        }
        val slice = all.drop(from).take(pageSize)
        return SourcePage(slice, page, from + slice.size < all.size, id)
    }
}
