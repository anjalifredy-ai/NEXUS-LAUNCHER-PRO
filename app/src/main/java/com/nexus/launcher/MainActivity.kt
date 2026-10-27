package com.nexus.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.launcher.filesystem.FileSystemManager
import com.nexus.launcher.jre.JavaRuntimeManager
import com.nexus.launcher.mods.ModrinthClient
import com.nexus.launcher.network.DownloadQueueManager
import com.nexus.launcher.server.ServerJavaResolver
import com.nexus.launcher.server.ServerManager
import com.nexus.launcher.ui.mods.ModrinthSearchScreen
import com.nexus.launcher.ui.server.CreateServerScreen
import com.nexus.launcher.ui.server.JreInstallBanner
import com.nexus.launcher.ui.server.ServerDetailScreen
import com.nexus.launcher.ui.server.ServerListScreen
import com.nexus.launcher.ui.theme.NexusColors
import com.nexus.launcher.ui.theme.NexusTheme
import com.nexus.launcher.ui.versions.VersionsScreen
import com.nexus.launcher.util.PermissionHelper
import com.nexus.launcher.version.ClientVersionInstaller
import com.nexus.launcher.version.FullClientInstaller
import com.nexus.launcher.version.VersionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var serverManager: ServerManager
    @Inject lateinit var javaResolver: ServerJavaResolver
    @Inject lateinit var versionManager: VersionManager
    @Inject lateinit var javaRuntimeManager: JavaRuntimeManager
    @Inject lateinit var fullClientInstaller: FullClientInstaller
    @Inject lateinit var clientVersionInstaller: ClientVersionInstaller
    @Inject lateinit var modrinthClient: ModrinthClient
    @Inject lateinit var downloadQueueManager: DownloadQueueManager
    @Inject lateinit var fileSystemManager: FileSystemManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        PermissionHelper.ensureLegacyStorage(this)
        PermissionHelper.requestNotifications(this)
        if (!PermissionHelper.hasManageExternalStorage()) {
            PermissionHelper.requestManageExternalStorage(this)
        }

        setContent {
            NexusTheme {
                val scope = rememberCoroutineScope()
                var route by remember { mutableStateOf("servers") }
                var selectedServerId by remember { mutableStateOf<String?>(null) }
                var mcVersions by remember {
                    mutableStateOf(listOf("1.21.1", "1.20.4", "1.19.4", "1.18.2", "1.16.5"))
                }

                LaunchedEffect(Unit) {
                    serverManager.loadServers()
                    try {
                        val manifest = versionManager.getVersionManifest()
                        val releases = manifest.versions.filter { it.type == "release" }.map { it.id }
                        if (releases.isNotEmpty()) mcVersions = releases
                    } catch (_: Exception) {
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        JreInstallBanner(javaRuntimeManager)

                        // Simple top nav
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            TextButton(onClick = { route = "servers" }) {
                                Text("Servers", color = if (route.startsWith("server") || route == "create" || route == "detail") NexusColors.Primary else NexusColors.OnSurfaceMuted, fontSize = 13.sp)
                            }
                            TextButton(onClick = { route = "versions" }) {
                                Text("Versions", color = if (route == "versions") NexusColors.Primary else NexusColors.OnSurfaceMuted, fontSize = 13.sp)
                            }
                            TextButton(onClick = { route = "mods" }) {
                                Text("Mods", color = if (route == "mods") NexusColors.Primary else NexusColors.OnSurfaceMuted, fontSize = 13.sp)
                            }
                        }

                        when {
                            route == "versions" -> VersionsScreen(
                                versionManager = versionManager,
                                fullClientInstaller = fullClientInstaller,
                                clientVersionInstaller = clientVersionInstaller,
                                onBack = { route = "servers" }
                            )
                            route == "mods" -> ModrinthSearchScreen(
                                modrinthClient = modrinthClient,
                                downloadQueueManager = downloadQueueManager,
                                modsDir = fileSystemManager.getModsDir("default"),
                                gameVersion = mcVersions.firstOrNull(),
                                onBack = { route = "servers" }
                            )
                            route == "create" -> CreateServerScreen(
                                serverManager = serverManager,
                                javaResolver = javaResolver,
                                availableMcVersions = mcVersions,
                                onCreated = { id ->
                                    selectedServerId = id
                                    route = "detail"
                                },
                                onCancel = { route = "servers" }
                            )
                            route == "detail" && selectedServerId != null -> ServerDetailScreen(
                                serverId = selectedServerId!!,
                                serverManager = serverManager,
                                onBack = { route = "servers" }
                            )
                            else -> ServerListScreen(
                                serverManager = serverManager,
                                onOpenServer = { id ->
                                    selectedServerId = id
                                    route = "detail"
                                },
                                onCreateServer = { route = "create" },
                                onStartServer = { id -> scope.launch { serverManager.startServer(id) } },
                                onStopServer = { id -> scope.launch { serverManager.stopServer(id) } }
                            )
                        }
                    }
                }
            }
        }
    }
}
