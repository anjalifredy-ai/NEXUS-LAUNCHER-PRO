package com.nexus.launcher.ui.versions

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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.launcher.data.remote.VersionEntry
import com.nexus.launcher.ui.theme.NexusColors
import com.nexus.launcher.version.ClientVersionInstaller
import com.nexus.launcher.version.FullClientInstaller
import com.nexus.launcher.version.VersionManager
import kotlinx.coroutines.launch

@Composable
fun VersionsScreen(
    versionManager: VersionManager,
    fullClientInstaller: FullClientInstaller,
    clientVersionInstaller: ClientVersionInstaller,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var releases by remember { mutableStateOf<List<VersionEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingList by remember { mutableStateOf(true) }
    var installingId by remember { mutableStateOf<String?>(null) }
    val stage by fullClientInstaller.stage.collectAsState()

    LaunchedEffect(Unit) {
        loadingList = true
        try {
            releases = versionManager.getAvailableVersions().filter { it.type == "release" }.take(40)
        } catch (e: Exception) {
            error = e.message
        }
        loadingList = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusColors.Background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back", color = NexusColors.Primary) }
            Text(
                "Versions",
                color = NexusColors.Primary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            "Install client jar + libraries + assets",
            color = NexusColors.OnSurfaceMuted,
            fontSize = 13.sp
        )

        when (val s = stage) {
            is FullClientInstaller.Stage.Meta -> StatusLine("Fetching meta ${s.id}…")
            is FullClientInstaller.Stage.ClientJar -> StatusLine("Client jar ${s.id}")
            is FullClientInstaller.Stage.Libraries ->
                StatusLine("Libraries ${s.done}/${s.total}", s.done.toFloat() / s.total.coerceAtLeast(1))
            is FullClientInstaller.Stage.Assets ->
                StatusLine("Assets ${s.done}/${s.total}")
            is FullClientInstaller.Stage.Done -> StatusLine("Done: ${s.id}", accent = true)
            is FullClientInstaller.Stage.Error -> StatusLine(s.message, error = true)
            else -> {}
        }

        error?.let { Text(it, color = NexusColors.Danger, fontSize = 12.sp) }

        if (loadingList) {
            Text("Loading manifest…", color = NexusColors.OnSurfaceMuted)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(releases, key = { it.id }) { v ->
                    val installed = clientVersionInstaller.isClientInstalled(v.id)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(v.id, color = NexusColors.OnSurface, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (installed) "Client jar present" else "Not installed",
                                color = if (installed) NexusColors.Online else NexusColors.OnSurfaceMuted,
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = {
                                installingId = v.id
                                scope.launch {
                                    fullClientInstaller.install(v.id, includeAssets = true)
                                    installingId = null
                                }
                            },
                            enabled = installingId == null,
                            colors = ButtonDefaults.buttonColors(containerColor = NexusColors.Primary)
                        ) {
                            Text(
                                if (installingId == v.id) "…" else if (installed) "Reinstall" else "Install",
                                color = Color(0xFF052E16)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, progress: Float? = null, accent: Boolean = false, error: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text,
            color = when {
                error -> NexusColors.Danger
                accent -> NexusColors.Online
                else -> NexusColors.Accent
            },
            fontSize = 12.sp
        )
        if (progress != null) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = NexusColors.Primary,
                trackColor = NexusColors.Surface
            )
        }
    }
}
