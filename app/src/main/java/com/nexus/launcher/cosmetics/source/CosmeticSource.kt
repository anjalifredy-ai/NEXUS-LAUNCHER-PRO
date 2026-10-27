package com.nexus.launcher.cosmetics.source

import com.nexus.launcher.cosmetics.CosmeticCategory
import com.nexus.launcher.cosmetics.CosmeticItem

/**
 * Pluggable catalogue source. Each adapter declares which asset types it supports.
 * Pagination is source-defined (page index or cursor).
 */
interface CosmeticSource {
    val id: String
    val displayName: String
    val supportedCategories: Set<CosmeticCategory>

    /**
     * @param page zero-based page index
     * @param pageSize requested page size (source may clamp)
     */
    suspend fun fetchPage(
        category: CosmeticCategory,
        query: String,
        page: Int,
        pageSize: Int = 24
    ): SourcePage
}

data class SourcePage(
    val items: List<CosmeticItem>,
    val page: Int,
    val hasMore: Boolean,
    val sourceId: String,
    val error: String? = null
)

data class SourceStatus(
    val sourceId: String,
    val displayName: String,
    val online: Boolean,
    val message: String?
)
