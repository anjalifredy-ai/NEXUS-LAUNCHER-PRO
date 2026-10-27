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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.launcher.cosmetics.CosmeticCategory
import com.nexus.launcher.cosmetics.CosmeticItem
import com.nexus.launcher.cosmetics.CosmeticsStore
import com.nexus.launcher.ui.theme.NexusColors

@Composable
fun CosmeticsStoreScreen(
    store: CosmeticsStore,
    onOpenWardrobe: () -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<CosmeticCategory?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    val favorites by store.favorites.collectAsState()

    val items = remember(query, category, favoritesOnly, favorites) {
        store.search(
            query = query,
            category = category,
            favoritesOnly = favoritesOnly
        ) + if (category == null || category == CosmeticCategory.SKINS) {
            store.importedItems().filter {
                query.isBlank() || it.name.contains(query, true)
            }
        } else emptyList()
    }.distinctBy { it.id }

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
        Text("Nexus Store", color = NexusColors.Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            store.visibilityNote(),
            color = NexusColors.OnSurfaceMuted,
            fontSize = 11.sp
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            label = { Text("Search") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = NexusColors.OnSurface,
                unfocusedTextColor = NexusColors.OnSurface,
                focusedBorderColor = NexusColors.Primary,
                unfocusedBorderColor = NexusColors.OnSurfaceMuted
            )
        )

        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(
                selected = category == null,
                onClick = { category = null },
                label = { Text("All", fontSize = 11.sp) }
            )
            CosmeticCategory.entries.take(10).forEach { c ->
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

        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text("No items match.", color = NexusColors.OnSurfaceMuted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items, key = { it.id }) { item ->
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
            "${item.category.name} · ${item.rarity.name} · ${item.collection.name}" +
                if (item.animated) " · animated" else "",
            color = NexusColors.OnSurfaceMuted,
            fontSize = 11.sp
        )
        Text(item.description, color = NexusColors.OnSurfaceMuted, fontSize = 12.sp)
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
