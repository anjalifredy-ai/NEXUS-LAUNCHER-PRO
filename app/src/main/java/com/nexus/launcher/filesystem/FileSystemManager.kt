package com.nexus.launcher.filesystem

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileSystemManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val minecraftDir: File
        get() = File(Environment.getExternalStorageDirectory(), "NexusLauncher/.minecraft").also { it.mkdirs() }

    val versionsDir: File
        get() = File(minecraftDir, "versions").also { it.mkdirs() }

    val librariesDir: File
        get() = File(minecraftDir, "libraries").also { it.mkdirs() }

    val assetsDir: File
        get() = File(minecraftDir, "assets").also { it.mkdirs() }

    val nativesDir: File
        get() = File(minecraftDir, "natives").also { it.mkdirs() }

    val savesDir: File
        get() = File(minecraftDir, "saves").also { it.mkdirs() }

    fun getModsDir(profileId: String): File =
        File(minecraftDir, "profiles/$profileId/mods").also { it.mkdirs() }

    val resourcePacksDir: File
        get() = File(minecraftDir, "resourcepacks").also { it.mkdirs() }

    val shadersDir: File
        get() = File(minecraftDir, "shaderpacks").also { it.mkdirs() }

    val jreDir: File
        get() = File(Environment.getExternalStorageDirectory(), "NexusLauncher/runtime").also { it.mkdirs() }

    val logsDir: File
        get() = File(minecraftDir, "logs").also { it.mkdirs() }

    val crashReportsDir: File
        get() = File(minecraftDir, "crash-reports").also { it.mkdirs() }

    val screenshotsDir: File
        get() = File(minecraftDir, "screenshots").also { it.mkdirs() }

    fun getVersionDir(versionId: String): File =
        File(versionsDir, versionId).also { it.mkdirs() }

    fun getProfileDir(profileId: String): File =
        File(minecraftDir, "profiles/$profileId").also { it.mkdirs() }

    fun getTotalSize(): Long = minecraftDir.walkTopDown()
        .filter { it.isFile }
        .map { it.length() }
        .sum()

    fun clearCache() {
        File(minecraftDir, "cache").deleteRecursively()
    }
}
