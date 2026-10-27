package com.nexus.launcher.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.nexus.launcher.filesystem.FileSystemManager
import com.nexus.launcher.server.ServerManager
import com.nexus.launcher.server.model.ServerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates Nexus Voice Chat = Simple Voice Chat mod install + Android mic permission state.
 * Does not reimplement UDP voice protocol.
 */
@Singleton
class VoiceChatManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installer: VoiceChatInstaller,
    private val fileSystemManager: FileSystemManager,
    private val serverManager: ServerManager
) {
    data class Status(
        val micPermissionGranted: Boolean,
        val clientInstalledForProfile: Boolean,
        val lastMessage: String?
    )

    private val _lastMessage = MutableStateFlow<String?>(null)
    val lastMessage: StateFlow<String?> = _lastMessage.asStateFlow()

    fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun profileModsDir(profileId: String = "default"): File =
        fileSystemManager.getModsDir(profileId)

    fun isClientInstalled(profileId: String = "default"): Boolean =
        installer.isInstalled(profileModsDir(profileId))

    fun status(profileId: String = "default"): Status = Status(
        micPermissionGranted = hasMicPermission(),
        clientInstalledForProfile = isClientInstalled(profileId),
        lastMessage = _lastMessage.value
    )

    suspend fun enableForClientProfile(
        minecraftVersion: String,
        loader: String,
        profileId: String = "default"
    ): VoiceChatInstaller.InstallResult {
        val result = installer.installClientMod(
            minecraftVersion,
            loader,
            profileModsDir(profileId)
        )
        _lastMessage.value = result.message
        return result
    }

    suspend fun disableForClientProfile(profileId: String = "default"): VoiceChatInstaller.InstallResult {
        val result = installer.uninstallFrom(profileModsDir(profileId))
        _lastMessage.value = result.message
        return result
    }

    suspend fun enableForHostedServer(serverId: String): VoiceChatInstaller.InstallResult {
        val config = serverManager.getById(serverId)
            ?: return VoiceChatInstaller.InstallResult(false, "Server not found")
        val support = VoiceChatCompatibility.serverSupport(config.software, config.minecraftVersion)
        if (support.level == VoiceChatCompatibility.SupportLevel.UNSUPPORTED) {
            val msg = support.reason ?: "Unsupported"
            _lastMessage.value = msg
            return VoiceChatInstaller.InstallResult(false, msg)
        }
        val result = installer.installServerComponent(config)
        _lastMessage.value = result.message
        return result
    }

    suspend fun disableForHostedServer(serverId: String): VoiceChatInstaller.InstallResult {
        val config = serverManager.getById(serverId)
            ?: return VoiceChatInstaller.InstallResult(false, "Server not found")
        val modsDir = File(config.serverDir, "mods")
        val result = installer.uninstallFrom(modsDir)
        _lastMessage.value = result.message
        return result
    }

    fun serverVoiceInstalled(config: ServerConfig): Boolean =
        installer.isInstalled(File(config.serverDir, "mods"))

    fun publicServerNote(): String = VoiceChatCompatibility.noteAboutPublicServers()

    fun androidNote(): String = VoiceChatCompatibility.noteAboutAndroid()
}
