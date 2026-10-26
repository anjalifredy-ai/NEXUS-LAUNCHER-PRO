package com.nexus.launcher.ui.server

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.launcher.server.ServerManager
import com.nexus.launcher.server.model.ServerConfig
import com.nexus.launcher.server.model.ServerRuntimeStatus
import com.nexus.launcher.server.model.ServerState
import com.nexus.launcher.ui.theme.NexusColors

/**
 * Server Hosting home — banner cards as specified in the master prompt.
 * Each card: icon area, name, MOTD, version, software, state, players, RAM, Start/Stop, Manage.
 */
@Composable
fun ServerListScreen(
    serverManager: ServerManager,
    onOpenServer: (String) -> Unit,
    onCreateServer: () -> Unit
) {
    val servers by serverManager.servers.collectAsState()
    val statusMap by serverManager.runtimeStatus.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusColors.Background)
    ) {
        Column(Modifier = Modifier.fillMaxSize()) {
            Text(
                text = "SERVER HOSTING",
                color = NexusColors.Primary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 8.dp)
            )
            Text(
                text = "Run Minecraft Java servers on this phone",
                color = NexusColors.OnSurfaceMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            if (servers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Dns,
                            contentDescription = null,
                            tint = NexusColors.OnSurfaceMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier = Modifier.height(12.dp))
                        Text("No servers yet", color = NexusColors.OnSurfaceMuted)
                        Spacer(Modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onCreateServer,
                            colors = ButtonDefaults.buttonColors(containerColor = NexusColors.Primary)
                        ) {
                            Text("Create server", color = Color(0xFF052E16))
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(servers, key = { it.id }) { server ->
                        ServerBannerCard(
                            config = server,
                            status = statusMap[server.id],
                            onClick = { onOpenServer(server.id) },
                            onStart = { /* wired by parent / ViewModel */ },
                            onStop = { }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onCreateServer,
            containerColor = NexusColors.Primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create", tint = Color(0xFF052E16))
        }
    }
}

@Composable
fun ServerBannerCard(
    config: ServerConfig,
    status: ServerRuntimeStatus?,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val state = status?.state ?: ServerState.STOPPED
    val stateColor = when (state) {
        ServerState.ONLINE -> NexusColors.Online
        ServerState.STARTING, ServerState.INSTALLING -> NexusColors.Warning
        ServerState.FAILED -> NexusColors.Danger
        else -> NexusColors.Offline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NexusColors.Surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            // Banner gradient (iconPath later)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF14532D),
                                Color(0xFF0F172A),
                                Color(0xFF1E3A5F)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NexusColors.BannerOverlay)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NexusColors.SurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Dns, null, tint = NexusColors.Primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            config.name,
                            color = NexusColors.OnSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            config.motd,
                            color = NexusColors.OnSurfaceMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        state.name,
                        color = stateColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "${config.software.displayName} · ${config.minecraftVersion}",
                            color = NexusColors.OnSurfaceMuted,
                            fontSize = 12.sp
                        )
                        Text(
                            "RAM ${config.memoryMb} MB · Port ${config.port}" +
                                if (status?.state == ServerState.ONLINE)
                                    " · ${status.playersOnline}/${config.maxPlayers}"
                                else "",
                            color = NexusColors.OnSurfaceMuted,
                            fontSize = 11.sp
                        )
                    }
                    Row {
                        if (state == ServerState.ONLINE || state == ServerState.STARTING) {
                            IconButton(onClick = onStop) {
                                Icon(Icons.Default.Stop, "Stop", tint = NexusColors.Danger)
                            }
                        } else {
                            IconButton(onClick = onStart) {
                                Icon(Icons.Default.PlayArrow, "Start", tint = NexusColors.Primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
