package com.nexus.launcher.ui.cosmetics

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.launcher.cosmetics.CosmeticAssetCache
import com.nexus.launcher.cosmetics.CosmeticsStore
import com.nexus.launcher.ui.theme.NexusColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun WardrobeScreen(
    store: CosmeticsStore,
    assetCache: CosmeticAssetCache,
    onOpenStore: () -> Unit,
    onBack: () -> Unit
) {
    val loadout by store.loadout.collectAsState()
    val outfits by store.outfits.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var outfitName by remember { mutableStateOf("PvP") }
    var message by remember { mutableStateOf<String?>(null) }
    var cacheBytes by remember { mutableStateOf(assetCache.cacheSizeBytes()) }

    val pickSkin = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val tmp = File(context.cacheDir, "skin_import.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tmp).use { output -> input.copyTo(output) }
                } ?: return@withContext Result.failure(IllegalStateException("Cannot read file"))
                store.importSkin(tmp)
            }
            message = result.fold(
                onSuccess = { "Imported & equipped: ${it.name}" },
                onFailure = { it.message }
            )
            cacheBytes = assetCache.cacheSizeBytes()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row {
            TextButton(onClick = onBack) { Text("← Back", color = NexusColors.Primary) }
            TextButton(onClick = onOpenStore) { Text("Store", color = NexusColors.Accent) }
        }
        Text("Wardrobe", color = NexusColors.Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Preview is text-based until 3D renderer is wired. Loadout is persisted.",
            color = NexusColors.OnSurfaceMuted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(12.dp))
        Text("Equipped", color = NexusColors.Primary, fontWeight = FontWeight.Bold)
        LoadoutLine("Skin", loadout.skinId)
        LoadoutLine("Cape", loadout.capeId)
        LoadoutLine("Hat / head", loadout.hatId)
        LoadoutLine("Back", loadout.backId)
        LoadoutLine("Wings", loadout.wingsId)
        Text(
            "Emotes: ${loadout.emoteSlots.mapIndexed { i, id -> "${i + 1}:${id ?: "—"}" }.joinToString(" ")}",
            color = NexusColors.OnSurfaceMuted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { pickSkin.launch("image/png") },
            colors = ButtonDefaults.buttonColors(containerColor = NexusColors.Primary)
        ) {
            Text("Import skin PNG", color = Color(0xFF052E16))
        }
        TextButton(onClick = {
            store.resetLoadout()
            message = "Loadout reset"
        }) {
            Text("Reset loadout", color = NexusColors.Danger)
        }

        Spacer(Modifier.height(16.dp))
        Text("Asset cache", color = NexusColors.Primary, fontWeight = FontWeight.Bold)
        Text(
            "Size: ${cacheBytes / 1024} KB",
            color = NexusColors.OnSurfaceMuted,
            fontSize = 13.sp
        )
        TextButton(
            onClick = {
                val n = assetCache.clearCache()
                cacheBytes = assetCache.cacheSizeBytes()
                message = "Cleared $n cached files"
            }
        ) {
            Text("Clear cosmetic cache", color = NexusColors.Warning)
        }

        Spacer(Modifier.height(16.dp))
        Text("Saved outfits", color = NexusColors.Primary, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = outfitName,
            onValueChange = { outfitName = it },
            label = { Text("Outfit name") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = NexusColors.OnSurface,
                unfocusedTextColor = NexusColors.OnSurface,
                focusedBorderColor = NexusColors.Primary,
                unfocusedBorderColor = NexusColors.OnSurfaceMuted
            )
        )
        Button(
            onClick = {
                store.saveOutfit(outfitName)
                message = "Saved outfit $outfitName"
            },
            colors = ButtonDefaults.buttonColors(containerColor = NexusColors.Primary)
        ) {
            Text("Save current outfit", color = Color(0xFF052E16))
        }

        outfits.forEach { o ->
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    store.applyOutfit(o.id)
                    message = "Applied ${o.name}"
                }) {
                    Text(o.name, color = NexusColors.Accent)
                }
                TextButton(onClick = { store.deleteOutfit(o.id) }) {
                    Text("Delete", color = NexusColors.Danger)
                }
            }
        }

        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = NexusColors.Accent, fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            store.visibilityNote(),
            color = NexusColors.OnSurfaceMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun LoadoutLine(label: String, id: String?) {
    Text(
        "$label: ${id ?: "—"}",
        color = NexusColors.OnSurface,
        fontSize = 14.sp,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
