package com.nexus.launcher.ui.mods

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.launcher.mods.ModrinthClient
import com.nexus.launcher.mods.ModrinthSearchHit
import com.nexus.launcher.network.DownloadQueueManager
import com.nexus.launcher.ui.theme.NexusColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ModrinthSearchScreen(
    modrinthClient: ModrinthClient,
    downloadQueueManager: DownloadQueueManager,
    modsDir: File,
    gameVersion: String? = null,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<ModrinthSearchHit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = NexusColors.OnSurface,
        unfocusedTextColor = NexusColors.OnSurface,
        focusedBorderColor = NexusColors.Primary,
        unfocusedBorderColor = NexusColors.OnSurfaceMuted
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusColors.Background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back", color = NexusColors.Primary) }
            Text("Modrinth", color = NexusColors.Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                label = { Text("Search mods") },
                singleLine = true,
                colors = fieldColors
            )
            Button(
                onClick = {
                    busy = true
                    status = null
                    scope.launch {
                        hits = withContext(Dispatchers.IO) {
                            modrinthClient.search(query.ifBlank { "fabric" })
                        }
                        busy = false
                    }
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = NexusColors.Primary)
            ) {
                Text(if (busy) "…" else "Go", color = Color(0xFF052E16))
            }
        }
        status?.let {
            Text(it, color = NexusColors.Accent, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }

        LazyColumn(
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(hits, key = { it.projectId }) { hit ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(hit.title, color = NexusColors.OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(
                        hit.description,
                        color = NexusColors.OnSurfaceMuted,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("${hit.downloads} downloads · ${hit.categories.joinToString()}",
                        color = NexusColors.OnSurfaceMuted, fontSize = 11.sp)
                    TextButton(
                        onClick = {
                            scope.launch {
                                status = "Resolving ${hit.title}…"
                                val file = withContext(Dispatchers.IO) {
                                    modrinthClient.latestPrimaryFile(hit.projectId, gameVersion)
                                }
                                if (file == null) {
                                    status = "No file for ${hit.title}"
                                    return@launch
                                }
                                modsDir.mkdirs()
                                val dest = File(modsDir, file.fileName)
                                status = "Downloading ${file.fileName}…"
                                withContext(Dispatchers.IO) {
                                    downloadQueueManager.downloadFile(file.downloadUrl, dest)
                                }
                                status = "Saved ${file.fileName}"
                            }
                        }
                    ) {
                        Text("Download latest", color = NexusColors.Primary)
                    }
                }
            }
        }
    }
}
