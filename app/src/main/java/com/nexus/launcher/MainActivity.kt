package com.nexus.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nexus.launcher.server.ServerJavaResolver
import com.nexus.launcher.server.ServerManager
import com.nexus.launcher.ui.server.CreateServerScreen
import com.nexus.launcher.ui.server.ServerListScreen
import com.nexus.launcher.ui.theme.NexusTheme
import com.nexus.launcher.version.VersionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var serverManager: ServerManager
    @Inject lateinit var javaResolver: ServerJavaResolver
    @Inject lateinit var versionManager: VersionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NexusTheme {
                var route by remember { mutableStateOf("servers") }
                var selectedServerId by remember { mutableStateOf<String?>(null) }
                var mcVersions by remember { mutableStateOf(listOf("1.21.1", "1.20.4", "1.19.4", "1.18.2", "1.16.5")) }

                LaunchedEffect(Unit) {
                    serverManager.loadServers()
                    try {
                        val manifest = versionManager.fetchVersionManifest()
                        val releases = manifest.versions
                            .filter { it.type == "release" }
                            .map { it.id }
                        if (releases.isNotEmpty()) mcVersions = releases
                    } catch (_: Exception) {
                        // keep fallback list
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    when (route) {
                        "create" -> CreateServerScreen(
                            serverManager = serverManager,
                            javaResolver = javaResolver,
                            availableMcVersions = mcVersions,
                            onCreated = {
                                selectedServerId = it
                                route = "servers"
                            },
                            onCancel = { route = "servers" }
                        )
                        else -> ServerListScreen(
                            serverManager = serverManager,
                            onOpenServer = { id ->
                                selectedServerId = id
                                // Detail screen next batch
                            },
                            onCreateServer = { route = "create" }
                        )
                    }
                }
            }
        }
    }
}
