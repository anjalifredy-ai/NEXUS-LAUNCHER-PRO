package com.nexus.launcher.ui.voice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.launcher.ui.theme.NexusColors
import com.nexus.launcher.voice.VoiceChatCompatibility
import com.nexus.launcher.voice.VoiceChatManager
import kotlinx.coroutines.launch

/**
 * Launcher-side voice chat controls: install SVC mod, mic permission, honesty about limits.
 * In-game PTT / proximity / HUD are provided by Simple Voice Chat mod itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceChatScreen(
    voiceChatManager: VoiceChatManager,
    availableMcVersions: List<String>,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var mcVersion by remember { mutableStateOf(availableMcVersions.firstOrNull() ?: "1.21.1") }
    var loader by remember { mutableStateOf("fabric") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var versionExpanded by remember { mutableStateOf(false) }
    var loaderExpanded by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        message = if (granted) "Microphone permission granted"
        else "Permission denied — Minecraft still plays; voice needs mic in Android Settings"
    }

    val status = voiceChatManager.status()
    val clientSupport = VoiceChatCompatibility.clientSupport(loader, mcVersion)

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
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Back", color = NexusColors.Primary) }
        Text(
            VoiceChatCompatibility.DISPLAY_NAME,
            color = NexusColors.Primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Uses official Simple Voice Chat (Modrinth). Not a custom protocol.",
            color = NexusColors.OnSurfaceMuted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(16.dp))
        Text("Status", color = NexusColors.Primary, fontWeight = FontWeight.Bold)
        Text(
            "Mic permission: ${if (status.micPermissionGranted) "Granted" else "Not granted"}",
            color = if (status.micPermissionGranted) NexusColors.Online else NexusColors.Warning,
            fontSize = 13.sp
        )
        Text(
            "Client mod (default profile): ${if (status.clientInstalledForProfile) "Installed" else "Not installed"}",
            color = NexusColors.OnSurface,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(12.dp))
        if (!status.micPermissionGranted) {
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                colors = ButtonDefaults.buttonColors(containerColor = NexusColors.Primary)
            ) {
                Text("Request microphone", color = Color(0xFF052E16))
            }
            TextButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            ) {
                Text("Open Android app settings", color = NexusColors.Accent)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Install client mod", color = NexusColors.Primary, fontWeight = FontWeight.Bold)

        ExposedDropdownMenuBox(expanded = versionExpanded, onExpandedChange = { versionExpanded = it }) {
            OutlinedTextField(
                value = mcVersion,
                onValueChange = {},
                readOnly = true,
                label = { Text("Minecraft version") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(versionExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors = fieldColors
            )
            ExposedDropdownMenu(expanded = versionExpanded, onDismissRequest = { versionExpanded = false }) {
                availableMcVersions.take(30).forEach { v ->
                    DropdownMenuItem(
                        text = { Text(v) },
                        onClick = {
                            mcVersion = v
                            versionExpanded = false
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = loaderExpanded, onExpandedChange = { loaderExpanded = it }) {
            OutlinedTextField(
                value = loader,
                onValueChange = {},
                readOnly = true,
                label = { Text("Loader") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(loaderExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors = fieldColors
            )
            ExposedDropdownMenu(expanded = loaderExpanded, onDismissRequest = { loaderExpanded = false }) {
                listOf("fabric", "quilt", "forge", "neoforge").forEach { l ->
                    DropdownMenuItem(
                        text = { Text(l) },
                        onClick = {
                            loader = l
                            loaderExpanded = false
                        }
                    )
                }
            }
        }

        Text(
            clientSupport.reason
                ?: "Support: ${clientSupport.level} (${clientSupport.loaderLabel})",
            color = if (clientSupport.level == VoiceChatCompatibility.SupportLevel.UNSUPPORTED)
                NexusColors.Danger else NexusColors.OnSurfaceMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Button(
            onClick = {
                busy = true
                message = null
                scope.launch {
                    val r = voiceChatManager.enableForClientProfile(mcVersion, loader)
                    message = r.message
                    busy = false
                }
            },
            enabled = !busy &&
                clientSupport.level != VoiceChatCompatibility.SupportLevel.UNSUPPORTED,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NexusColors.Primary)
        ) {
            Text(if (busy) "Installing…" else "Install Simple Voice Chat", color = Color(0xFF052E16))
        }
        TextButton(
            onClick = {
                scope.launch {
                    message = voiceChatManager.disableForClientProfile().message
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Uninstall from default profile", color = NexusColors.Danger)
        }

        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = NexusColors.Accent, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))
        Text("How it works", color = NexusColors.Primary, fontWeight = FontWeight.Bold)
        Text(
            "• PTT, mute, proximity, HUD = inside Simple Voice Chat in-game settings\n" +
                "• ${voiceChatManager.publicServerNote()}\n" +
                "• ${voiceChatManager.androidNote()}\n" +
                "• Hosted Nexus servers: enable voice from server detail if Fabric/Forge/Quilt",
            color = NexusColors.OnSurfaceMuted,
            fontSize = 12.sp
        )
    }
}
