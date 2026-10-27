package com.nexus.launcher.ui.server

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.launcher.server.LanInfo
import com.nexus.launcher.server.ServerManager
import com.nexus.launcher.server.ServerPluginInfo
import com.nexus.launcher.server.WorldFolder
import com.nexus.launcher.server.model.ServerConfig
import com.nexus.launcher.server.model.ServerState
import com.nexus.launcher.ui.theme.NexusColors
import com.nexus.launcher.voice.VoiceChatManager
import kotlinx.coroutines.launch

enum class ServerTab { Overview, Console, Settings, Worlds, Plugins }

@Composable
fun ServerDetailScreen(
    serverId: String,
    serverManager: ServerManager,
    voiceChatManager: VoiceChatManager? = null,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val statusMap by serverManager.runtimeStatus.collectAsState()
    val status = statusMap[serverId]
    var config by remember { mutableStateOf<ServerConfig?>(null) }
    var tab by remember { mutableStateOf(ServerTab.Overview) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(serverId) {
        config = serverManager.getById(serverId)
    }

    val cfg = config
    if (cfg == null) {
        Box(Modifier.fillMaxSize().background(NexusColors.Background), contentAlignment = Alignment.Center) {
            Text("Loading…", color = NexusColors.OnSurfaceMuted)
        }
        return
    }

    val state = status?.state ?: ServerState.STOPPED
    val stateColor = when (state) {
        ServerState.ONLINE -> NexusColors.Online
        ServerState.STARTING, ServerState.INSTALLING -> NexusColors.Warning
        ServerState.FAILED -> NexusColors.Danger
        else -> NexusColors.Offline
    }
    val lan = remember(cfg.port) { serverManager.lanInfo(cfg.port) }

    Column(Modifier = Modifier.fillMaxSize().background(NexusColors.Background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NexusColors.OnSurface)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(cfg.name, color = NexusColors.OnSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "${cfg.software.displayName} · ${cfg.minecraftVersion} · ${state.name}",
                    color = stateColor,
                    fontSize = 12.sp
                )
            }
            if (state == ServerState.ONLINE || state == ServerState.STARTING) {
                IconButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            serverManager.stopServer(serverId)
                            busy = false
                        }
                    },
                    enabled = !busy
                ) {
                    Icon(Icons.Default.Stop, "Stop", tint = NexusColors.Danger)
                }
            } else {
                IconButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            message = null
                            message = serverManager.startServer(serverId).exceptionOrNull()?.message
                            busy = false
                        }
                    },
                    enabled = !busy
                ) {
                    Icon(Icons.Default.PlayArrow, "Start", tint = NexusColors.Primary)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusColors.Surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatChip("RAM", "${cfg.memoryMb} MB")
            StatChip("Port", cfg.port.toString())
            StatChip(
                "Uptime",
                if (state == ServerState.ONLINE && status != null) formatUptime(status.uptimeMs) else "—"
            )
            StatChip(
                "Players",
                if (state == ServerState.ONLINE) "${status?.playersOnline ?: 0}/${cfg.maxPlayers}" else "—"
            )
        }

        message?.let {
            Text(it, color = NexusColors.Danger, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ServerTab.entries.forEach { t ->
                FilterChip(
                    selected = tab == t,
                    onClick = { tab = t },
                    label = { Text(t.name, fontSize = 12.sp) }
                )
            }
        }

        when (tab) {
            ServerTab.Overview -> OverviewTab(cfg, state, message, lan, voiceChatManager)
            ServerTab.Console -> ConsoleTab(serverId, serverManager)
            ServerTab.Settings -> SettingsTab(cfg, serverManager) { config = it }
            ServerTab.Worlds -> WorldsTab(serverId, serverManager)
            ServerTab.Plugins -> PluginsTab(serverId, serverManager)
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = NexusColors.OnSurfaceMuted, fontSize = 10.sp)
        Text(value, color = NexusColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatUptime(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val h = m / 60
    return when {
        h > 0 -> "${h}h ${m % 60}m"
        m > 0 -> "${m}m ${s % 60}s"
        else -> "${s}s"
    }
}

@Composable
private fun OverviewTab(
    cfg: ServerConfig,
    state: ServerState,
    message: String?,
    lan: LanInfo,
    voiceChatManager: VoiceChatManager?
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("LAN join", color = NexusColors.Primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    lan.joinAddress ?: "No LAN IP",
                    color = if (lan.joinAddress != null) NexusColors.Accent else NexusColors.Danger,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(lan.note, color = NexusColors.OnSurfaceMuted, fontSize = 12.sp)
            }
            if (lan.joinAddress != null) {
                IconButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("server", lan.joinAddress))
                        copied = true
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, "Copy", tint = NexusColors.Primary)
                }
            }
        }
        if (copied) Text("Copied", color = NexusColors.Online, fontSize = 12.sp)

        Spacer(Modifier.height(16.dp))
        if (voiceChatManager != null) {
            ServerVoiceSection(config = cfg, voiceChatManager = voiceChatManager)
        }

        Spacer(Modifier.height(12.dp))
        Text("Overview", color = NexusColors.Primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        InfoRow("MOTD", cfg.motd)
        InfoRow("Directory", cfg.serverDir)
        InfoRow("World", cfg.worldDir)
        InfoRow("Online-mode", cfg.onlineMode.toString())
        InfoRow("State", state.name)
        message?.let { InfoRow("Last error", it) }
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(k, color = NexusColors.OnSurfaceMuted, modifier = Modifier.width(110.dp), fontSize = 13.sp)
        Text(v, color = NexusColors.OnSurface, fontSize = 13.sp)
    }
}

@Composable
private fun ConsoleTab(serverId: String, serverManager: ServerManager) {
    val lines = remember { mutableStateListOf<String>() }
    var command by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(serverId) {
        serverManager.consoleLines.collect { line ->
            if (line.serverId == serverId) {
                lines.add(line.line)
                if (lines.size > 500) lines.removeAt(0)
            }
        }
    }
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF0A0A0A)).padding(8.dp)
        ) {
            items(lines) { line ->
                Text(
                    line,
                    color = when {
                        line.contains("ERROR", true) -> NexusColors.Danger
                        line.contains("WARN", true) -> NexusColors.Warning
                        else -> Color(0xFFB0BEC5)
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(NexusColors.Surface).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("command") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NexusColors.OnSurface,
                    unfocusedTextColor = NexusColors.OnSurface,
                    focusedBorderColor = NexusColors.Primary,
                    unfocusedBorderColor = NexusColors.OnSurfaceMuted
                )
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (command.isNotBlank()) {
                        serverManager.sendCommand(serverId, command.trim())
                        command = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexusColors.Primary)
            ) {
                Text("Send", color = Color(0xFF052E16))
            }
        }
    }
}

@Composable
private fun SettingsTab(
    cfg: ServerConfig,
    serverManager: ServerManager,
    onUpdated: (ServerConfig) -> Unit
) {
    val scope = rememberCoroutineScope()
    var local by remember(cfg.id) { mutableStateOf(cfg) }
    var savedMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("Settings → server.properties", color = NexusColors.Primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        ToggleRow("Online mode", local.onlineMode) { local = local.copy(onlineMode = it) }
        ToggleRow("Whitelist", local.whitelist) { local = local.copy(whitelist = it) }
        ToggleRow("PvP", local.pvp) { local = local.copy(pvp = it) }
        ToggleRow("Hardcore", local.hardcore) { local = local.copy(hardcore = it) }
        ToggleRow("Allow flight", local.allowFlight) { local = local.copy(allowFlight = it) }
        ToggleRow("Command blocks", local.enableCommandBlocks) {
            local = local.copy(enableCommandBlocks = it)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    val r = serverManager.updateSettings(local)
                    r.onSuccess {
                        onUpdated(it)
                        savedMsg = "Saved"
                    }
                    r.onFailure { savedMsg = it.message }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NexusColors.Primary)
        ) {
            Text("Save", color = Color(0xFF052E16))
        }
        savedMsg?.let { Text(it, color = NexusColors.Accent, fontSize = 12.sp) }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = NexusColors.OnSurface, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = NexusColors.PrimaryDim)
        )
    }
}

@Composable
private fun WorldsTab(serverId: String, serverManager: ServerManager) {
    val scope = rememberCoroutineScope()
    var serverWorlds by remember { mutableStateOf<List<WorldFolder>>(emptyList()) }
    var saves by remember { mutableStateOf<List<WorldFolder>>(emptyList()) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(serverId) {
        serverWorlds = serverManager.listServerWorlds(serverId)
        saves = serverManager.listLauncherSaves()
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Server worlds", color = NexusColors.Primary, fontWeight = FontWeight.Bold)
        serverWorlds.forEach { w ->
            Text("${w.name} (${w.sizeBytes / 1024} KB)", color = NexusColors.OnSurface, fontSize = 13.sp)
        }
        if (serverWorlds.isEmpty()) Text("None", color = NexusColors.OnSurfaceMuted)
        Spacer(Modifier.height(12.dp))
        Text("Import saves", color = NexusColors.Primary, fontWeight = FontWeight.Bold)
        saves.forEach { w ->
            TextButton(onClick = {
                scope.launch {
                    val r = serverManager.importWorld(serverId, w.path)
                    msg = if (r.isSuccess) {
                        serverWorlds = serverManager.listServerWorlds(serverId)
                        "Imported ${w.name}"
                    } else r.exceptionOrNull()?.message
                }
            }) { Text("Import ${w.name}", color = NexusColors.Accent) }
        }
        msg?.let { Text(it, color = NexusColors.Accent, fontSize = 12.sp) }
    }
}

@Composable
private fun PluginsTab(serverId: String, serverManager: ServerManager) {
    val scope = rememberCoroutineScope()
    var plugins by remember { mutableStateOf<List<ServerPluginInfo>>(emptyList()) }
    var url by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("plugin.jar") }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(serverId) {
        plugins = serverManager.listPlugins(serverId)
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Plugins / Mods", color = NexusColors.Primary, fontWeight = FontWeight.Bold)
        plugins.forEach { p ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(p.fileName, color = NexusColors.OnSurface, fontSize = 13.sp)
                Switch(
                    checked = p.enabled,
                    onCheckedChange = { en ->
                        scope.launch {
                            serverManager.togglePlugin(serverId, p.fileName, en)
                            plugins = serverManager.listPlugins(serverId)
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = NexusColors.PrimaryDim)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Jar URL") },
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
                scope.launch {
                    val r = serverManager.installPluginFromUrl(serverId, url, fileName)
                    msg = if (r.isSuccess) {
                        plugins = serverManager.listPlugins(serverId)
                        "Installed"
                    } else r.exceptionOrNull()?.message
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = NexusColors.Primary)
        ) {
            Text("Install", color = Color(0xFF052E16))
        }
        msg?.let { Text(it, color = NexusColors.Accent, fontSize = 12.sp) }
    }
}
