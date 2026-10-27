package com.nexus.launcher.cosmetics.source

import com.nexus.launcher.cosmetics.CosmeticCategory
import com.nexus.launcher.cosmetics.CosmeticItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class CatalogueState(
    val items: List<CosmeticItem> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
    val sourceNotes: List<String> = emptyList()
)

/**
 * Merges paginated results from multiple sources with deduplication.
 * Does not preload full assets — items carry URL/path metadata only.
 */
@Singleton
class CatalogueRepository @Inject constructor(
    local: LocalCatalogueSource,
    mineSkin: MineSkinSource,
    mojangUsername: MojangUsernameSkinSource
) {
    private val sources: List<CosmeticSource> = listOf(local, mineSkin, mojangUsername)

    private val _state = MutableStateFlow(CatalogueState())
    val state: StateFlow<CatalogueState> = _state.asStateFlow()

    private var activeCategory: CosmeticCategory = CosmeticCategory.SKINS
    private var activeQuery: String = ""
    private val seenIds = linkedSetOf<String>()

    fun listSources(): List<SourceStatus> = sources.map {
        SourceStatus(it.id, it.displayName, true, "Categories: ${it.supportedCategories.joinToString { c -> c.name }}")
    }

    suspend fun resetAndLoad(category: CosmeticCategory, query: String = "") = withContext(Dispatchers.IO) {
        activeCategory = category
        activeQuery = query
        seenIds.clear()
        _state.value = CatalogueState(loading = true, page = 0, hasMore = true)
        loadPage(0, append = false)
    }

    suspend fun loadMore() = withContext(Dispatchers.IO) {
        val s = _state.value
        if (s.loading || !s.hasMore) return@withContext
        loadPage(s.page + 1, append = true)
    }

    private suspend fun loadPage(page: Int, append: Boolean) {
        _state.value = _state.value.copy(loading = true, error = null)

        val applicable = sources.filter { activeCategory in it.supportedCategories }
        if (applicable.isEmpty()) {
            _state.value = CatalogueState(
                items = if (append) _state.value.items else emptyList(),
                page = page,
                hasMore = false,
                loading = false,
                error = "No sources support ${activeCategory.name}"
            )
            return
        }

        val pages = coroutineScope {
            applicable.map { src ->
                async {
                    try {
                        src.fetchPage(activeCategory, activeQuery, page)
                    } catch (e: Exception) {
                        SourcePage(emptyList(), page, false, src.id, e.message)
                    }
                }
            }.awaitAll()
        }

        val notes = pages.mapNotNull { p ->
            p.error?.let { "${p.sourceId}: $it" }
        }

        val merged = mutableListOf<CosmeticItem>()
        var anyMore = false
        for (p in pages) {
            if (p.hasMore) anyMore = true
            for (item in p.items) {
                if (seenIds.add(item.id)) merged.add(item)
            }
        }

        val previous = if (append) _state.value.items else emptyList()
        _state.value = CatalogueState(
            items = previous + merged,
            page = page,
            hasMore = anyMore && merged.isNotEmpty() || (anyMore && page == 0),
            loading = false,
            error = if (merged.isEmpty() && notes.isNotEmpty()) notes.joinToString("\n") else null,
            sourceNotes = notes
        )

        // If all sources returned empty but claimed hasMore on first pages of remote, keep hasMore for MineSkin
        if (merged.isEmpty() && page > 0) {
            _state.value = _state.value.copy(hasMore = false)
        }
    }
}
