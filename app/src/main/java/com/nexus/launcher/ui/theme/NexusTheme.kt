package com.nexus.launcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object NexusColors {
    val Background = Color(0xFF0B0F14)
    val Surface = Color(0xFF121820)
    val SurfaceVariant = Color(0xFF1A2330)
    val Primary = Color(0xFF4ADE80)
    val PrimaryDim = Color(0xFF22C55E)
    val Accent = Color(0xFF38BDF8)
    val Danger = Color(0xFFEF4444)
    val Warning = Color(0xFFFBBF24)
    val OnSurface = Color(0xFFE8EEF5)
    val OnSurfaceMuted = Color(0xFF94A3B8)
    val Online = Color(0xFF4ADE80)
    val Offline = Color(0xFF64748B)
    val BannerOverlay = Color(0xCC0B0F14)
}

private val NexusDarkScheme = darkColorScheme(
    primary = NexusColors.Primary,
    onPrimary = Color(0xFF052E16),
    secondary = NexusColors.Accent,
    background = NexusColors.Background,
    surface = NexusColors.Surface,
    surfaceVariant = NexusColors.SurfaceVariant,
    onBackground = NexusColors.OnSurface,
    onSurface = NexusColors.OnSurface,
    error = NexusColors.Danger
)

@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexusDarkScheme,
        content = content
    )
}
