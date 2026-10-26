package com.nexus.launcher.ui.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.launcher.jre.JavaRuntimeManager
import com.nexus.launcher.jre.JreDownloader
import com.nexus.launcher.ui.theme.NexusColors

/** Shows live JRE auto-install progress when a download is active. */
@Composable
fun JreInstallBanner(javaRuntimeManager: JavaRuntimeManager) {
    val progress by javaRuntimeManager.downloadProgress.collectAsState()

    when (val p = progress) {
        is JreDownloader.Progress.Idle -> {}
        is JreDownloader.Progress.Resolving -> {
            BannerBox("Finding Java ${p.javaMajor} package…")
        }
        is JreDownloader.Progress.Downloading -> {
            BannerBox(
                "Downloading Java ${p.javaMajor}… ${(p.fraction * 100).toInt()}%",
                progress = p.fraction
            )
        }
        is JreDownloader.Progress.Extracting -> {
            BannerBox("Extracting Java ${p.javaMajor}…")
        }
        is JreDownloader.Progress.Done -> {
            BannerBox("Java ${p.javaMajor} ready", accent = true)
        }
        is JreDownloader.Progress.Error -> {
            BannerBox("Java ${p.javaMajor} failed: ${p.message}", error = true)
        }
    }
}

@Composable
private fun BannerBox(
    text: String,
    progress: Float? = null,
    accent: Boolean = false,
    error: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    error -> NexusColors.Danger.copy(alpha = 0.15f)
                    accent -> NexusColors.Primary.copy(alpha = 0.12f)
                    else -> NexusColors.SurfaceVariant
                }
            )
            .padding(12.dp)
    ) {
        Text(
            text,
            color = when {
                error -> NexusColors.Danger
                accent -> NexusColors.Primary
                else -> NexusColors.OnSurface
            },
            fontSize = 13.sp
        )
        if (progress != null) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = NexusColors.Primary,
                trackColor = NexusColors.Surface
            )
        }
    }
}
