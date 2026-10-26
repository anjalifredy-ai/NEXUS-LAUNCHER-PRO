package com.nexus.launcher.ui.server

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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.launcher.server.ServerJavaResolver
import com.nexus.launcher.server.ServerManager
import com.nexus.launcher.server.model.ServerSoftware
import com.nexus.launcher.ui.theme.NexusColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateServerScreen(
    serverManager: ServerManager,
    javaResolver: ServerJavaResolver,
    availableMcVersions: List<String>,
    onCreated: (String) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val advice = remember { serverManager.ramAdvice(alsoPlaying = false) }

    var name by remember { mutableStateOf("My Nexus Server") }
    var software by remember { mutableStateOf(ServerSoftware.PAPER) }
    var mcVersion by remember { mutableStateOf(availableMcVersions.firstOrNull() ?: "1.21.1") }
    var memory by remember { mutableFloatStateOf(advice.recommendedServerMb.toFloat()) }
    var port by remember { mutableStateOf("25565") }
    var motd by remember { mutableStateOf("A Nexus Minecraft Server") }
    var eula by remember { mutableStateOf(false) }
    var crossVersion by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    var softwareExpanded by remember { mutableStateOf(false) }
    var versionExpanded by remember { mutableStateOf(false) }

    val javaForVersion = javaResolver.requiredJava(mcVersion)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = NexusColors.OnSurface,
        unfocusedTextColor = NexusColors.OnSurface,
        focusedBorderColor = NexusColors.Primary,
        unfocusedBorderColor = NexusColors.OnSurfaceMuted,
        cursorColor = NexusColors.Primary,
        focusedLabelColor = NexusColors.Primary,
        unfocusedLabelColor = NexusColors.OnSurfaceMuted
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Create server", color = NexusColors.Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Server name") },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = motd,
            onValueChange = { motd = it },
            label = { Text("MOTD") },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        ExposedDropdownMenuBox(expanded = softwareExpanded, onExpandedChange = { softwareExpanded = it }) {
            OutlinedTextField(
                value = software.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Software") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(softwareExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors = fieldColors
            )
            ExposedDropdownMenu(expanded = softwareExpanded, onDismissRequest = { softwareExpanded = false }) {
                listOf(ServerSoftware.VANILLA, ServerSoftware.PAPER, ServerSoftware.FABRIC).forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.displayName) },
                        onClick = {
                            software = s
                            softwareExpanded = false
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

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
                availableMcVersions.take(40).forEach { v ->
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
        Text(
            "Java runtime: ${javaForVersion.label} (auto)",
            color = NexusColors.Accent,
            fontSize = 13.sp
        )
        Text(
            "For 1.21.1 + 1.21.3 clients on one server, use Paper + ViaVersion.",
            color = NexusColors.OnSurfaceMuted,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(16.dp))
        Text("RAM: ${memory.toInt()} MB", color = NexusColors.OnSurface)
        Text(
            "Free ~${advice.availableMb} MB · recommended ${advice.recommendedServerMb} · max ${advice.maximumSafeServerMb}",
            color = NexusColors.OnSurfaceMuted,
            fontSize = 12.sp
        )
        Slider(
            value = memory,
            onValueChange = { memory = it },
            valueRange = advice.minimumServerMb.toFloat()..advice.maximumSafeServerMb.toFloat()
        )
        advice.warning?.let { Text(it, color = NexusColors.Warning, fontSize = 12.sp) }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
            label = { Text("Port") },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors,
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { eula = !eula }) {
            Text(
                if (eula) "✓ Minecraft EULA accepted" else "Accept Minecraft EULA (required)",
                color = if (eula) NexusColors.Primary else NexusColors.Warning
            )
        }
        if (software == ServerSoftware.PAPER) {
            TextButton(onClick = { crossVersion = !crossVersion }) {
                Text(
                    if (crossVersion) "✓ ViaVersion (multi-client versions)"
                    else "Install ViaVersion for multi-version clients",
                    color = if (crossVersion) NexusColors.Primary else NexusColors.OnSurfaceMuted
                )
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = NexusColors.Danger, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (!eula) {
                    error = "Accept the Minecraft EULA first"
                    return@Button
                }
                busy = true
                error = null
                scope.launch {
                    val created = serverManager.createServer(
                        name = name,
                        minecraftVersion = mcVersion,
                        software = software,
                        memoryMb = memory.toInt(),
                        port = port.toIntOrNull() ?: 25565,
                        motd = motd,
                        eulaAccepted = true
                    )
                    created.fold(
                        onSuccess = { cfg ->
                            val install = serverManager.installSoftware(
                                cfg,
                                installViaVersion = crossVersion && software == ServerSoftware.PAPER
                            )
                            if (!install.success) {
                                error = install.message
                                busy = false
                                return@launch
                            }
                            onCreated(cfg.id)
                        },
                        onFailure = {
                            error = it.message
                            busy = false
                        }
                    )
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NexusColors.Primary)
        ) {
            Text(if (busy) "Installing…" else "Create & install", color = Color(0xFF052E16))
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = NexusColors.OnSurfaceMuted)
        }
    }
}
