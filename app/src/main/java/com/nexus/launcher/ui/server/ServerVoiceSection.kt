package com.nexus.launcher.ui.server

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.launcher.server.model.ServerConfig
import com.nexus.launcher.ui.theme.NexusColors
import com.nexus.launcher.voice.VoiceChatCompatibility
import com.nexus.launcher.voice.VoiceChatManager
import kotlinx.coroutines.launch

/** Server-side Simple Voice Chat install for hosted Fabric/Forge/Quilt servers. */
@Composable
fun ServerVoiceSection(
    config: ServerConfig,
    voiceChatManager: VoiceChatManager
) {
    val scope = rememberCoroutineScope()
    val support = VoiceChatCompatibility.serverSupport(config.software, config.minecraftVersion)
    var msg by remember { mutableStateOf<String?>(null) }
    val installed = voiceChatManager.serverVoiceInstalled(config)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Voice chat (server)", color = NexusColors.Primary, fontWeight = FontWeight.Bold)
        Text(
            support.reason ?: "Component: ${support.componentLabel}",
            color = if (support.level == VoiceChatCompatibility.SupportLevel.UNSUPPORTED)
                NexusColors.Warning else NexusColors.OnSurfaceMuted,
            fontSize = 12.sp
        )
        Text(
            if (installed) "Status: installed in mods/" else "Status: not installed",
            color = if (installed) NexusColors.Online else NexusColors.OnSurfaceMuted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        if (support.level != VoiceChatCompatibility.SupportLevel.UNSUPPORTED) {
            Button(
                onClick = {
                    scope.launch {
                        msg = voiceChatManager.enableForHostedServer(config.id).message
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexusColors.Primary)
            ) {
                Text(
                    if (installed) "Reinstall voice component" else "Install voice on this server",
                    color = Color(0xFF052E16)
                )
            }
            TextButton(
                onClick = {
                    scope.launch {
                        msg = voiceChatManager.disableForHostedServer(config.id).message
                    }
                }
            ) {
                Text("Remove voice component", color = NexusColors.Danger)
            }
        }
        msg?.let {
            Text(it, color = NexusColors.Accent, fontSize = 12.sp)
        }
        Text(
            "UDP voice port may be required for remote clients. LAN same-Wi‑Fi only unless you configure networking. " +
                "No automatic router port-forward.",
            color = NexusColors.OnSurfaceMuted,
            fontSize = 11.sp
        )
    }
}
