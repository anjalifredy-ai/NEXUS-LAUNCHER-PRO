package com.nexus.launcher.ui.cosmetics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.launcher.cosmetics.CosmeticCategory
import com.nexus.launcher.cosmetics.CosmeticItem
import com.nexus.launcher.cosmetics.CosmeticsStore
import com.nexus.launcher.cosmetics.source.CatalogueRepository
import com.nexus.launcher.ui.theme.NexusColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun CosmeticsStoreScreen(
    store: CosmeticsStore,
    catalogueRepository: CatalogueRepository,
    onOpenWardrobe: () -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CosmeticCategory.SKINS) }
    var favoritesOnly by remember { mutableStateOf(false) }
    val favorites by store.favorites.collectAsState()
    val catalogue by catalogueRepository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(category) {
        catalogueRepository.resetAndLoad(category, query)
    }

    // Infinite scroll: near end → load more
    LaunchedEffect(listState, catalogue.hasMore, catalogue.loading) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd && catalogue.hasMore && !catalogue.loading) {
                    catalogueRepository.loadMore()
                }
            }
    }

    val displayItems = remember(catalogue.items, favoritesOnly, favorites) {
        if (favoritesOnly) catalogue.items.filter { it.id in favorites }
        else catalogue.items
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusColors.Background)
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("← Back", color = NexusColors.Primary) }
            TextButton(onClick = onOpenWardrobe) { Text("Wardrobe", color = NexusColors.Accent) }
        }
        Text("Nexus Cosmetics Universe", color = NexusColors.Primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Paginated sources · not a fixed hardcoded list. " + store.visibilityNote(),
            color = NexusColors.OnSurfaceMuted,
            fontSize = 11.sp
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            label = { Text("Search (username for Mojang · name for lists)") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = NexusColors.OnSurface,
                unfocusedTextColor = NexusColors.OnSurface,
                focusedBorderColor = NexusColors.Primary,
                unfocusedBorderColor = NexusColors.OnSurfaceMuted
            )
        )
        Row {
            Button(
                onClick = {
                    scope.launch { catalogueRepository.resetAndLoad(category, query) }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexusColors.Primary)
            ) {
                Text("Search", color = Color(0xFF052E16))
            }
            TextButton(onClick = {
                scope.launch { catalogueRepository.resetAndLoad(category, query) }
            }) {
                Text("Retry", color = NexusColors.Accent)
            }
        }

        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf(
                CosmeticCategory.SKINS,
                CosmeticCategory.CAPES,
                CosmeticCategory.HATS,
                CosmeticCategory.WINGS,
                CosmeticCategory.EMOTES,
                CosmeticCategory.GLASSES,
                CosmeticCategory.MASKS,
                CosmeticCategory.BACK_ACCESSORIES
            ).forEach { c ->
                FilterChip(
                    selected = category == c,
                    onClick = { category = c },
                    label = { Text(c.name, fontSize = 10.sp) }
                )
            }
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                label = { Text("Favorites", fontSize = 11.sp) }
            )
        }

        Text(
            "Sources: " + catalogueRepository.listSources().joinToString { it.displayName },
            color = NexusColors.OnSurfaceMuted,
            fontSize = 10.sp
        )

        catalogue.error?.let {
            Text(it, color = NexusColors.Danger, fontSize = 12.sp)
        }
        catalogue.sourceNotes.forEach {
            Text(it, color = NexusColors.Warning, fontSize = 10.sp)
        }

        Spacer(Modifier.height(6.dp))

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(displayItems, key = { it.id }) { item ->
                CosmeticRow(
                    item = item,
                    favorited = item.id in favorites,
                    onFavorite = { store.toggleFavorite(item.id) },
                    onEquip = {
                        store.markViewed(item.id)
                        store.equip(item.category, item.id)
                    }
                )
            }
            item {
                when {
                    catalogue.loading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = NexusColors.Primary)
                        }
                    }
                    catalogue.hasMore -> {
                        TextButton(
                            onClick = { scope.launch { catalogueRepository.loadMore() } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Load more", color = NexusColors.Primary)
                        }
                    }
                    displayItems.isEmpty() -> {
                        Text("No items. Try another category or search.", color = NexusColors.OnSurfaceMuted)
                    }
                    else -> {
                        Text("End of available pages for current sources.", color = NexusColors.OnSurfaceMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CosmeticRow(
    item: CosmeticItem,
    favorited: Boolean,
    onFavorite: () -> Unit,
    onEquip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NexusColors.Surface)
            .clickable(onClick = onEquip)
            .padding(12.dp)
    ) {
        Text(item.name, color = NexusColors.OnSurface, fontWeight = FontWeight.SemiBold)
        Text(
            "${item.category.name} · ${item.rarity.name} · ${item.id.take(40)}",
            color = NexusColors.OnSurfaceMuted,
            fontSize = 11.sp
        )
        Text(item.description, color = NexusColors.OnSurfaceMuted, fontSize = 12.sp)
        item.assetPath?.let {
            Text("Asset: ${it.take(60)}", color = NexusColors.OnSurfaceMuted, fontSize = 10.sp)
        }
        Row {
            TextButton(onClick = onFavorite) {
                Text(if (favorited) "★ Fav" else "☆ Fav", color = NexusColors.Primary)
            }
            TextButton(onClick = onEquip) {
                Text("Equip", color = NexusColors.Accent)
            }
        }
    }
}
