package com.nexus.launcher.jre

import com.nexus.launcher.filesystem.FileSystemManager
import com.nexus.launcher.util.OsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves JRE path for major version 8 / 17 / 21.
 * If missing → auto-download + extract via [JreDownloader] (Liberica).
 */
@Singleton
class JavaRuntimeManager @Inject constructor(
    private val fileSystemManager: FileSystemManager,
    private val jreHealthChecker: JreHealthChecker,
    private val jreDownloader: JreDownloader
) {
    val downloadProgress: StateFlow<JreDownloader.Progress> = jreDownloader.progress

    /**
     * Returns JRE root directory. Auto-installs if not present or unhealthy.
     */
    suspend fun getJrePath(javaVersion: Int, autoInstall: Boolean = true): File =
        withContext(Dispatchers.IO) {
            val jreDir = File(fileSystemManager.jreDir, "jre-$javaVersion")
            val javaBinary = File(jreDir, "bin/java" + OsUtil.executableExtension())

            if (javaBinary.exists()) {
                if (!OsUtil.isWindows()) javaBinary.setExecutable(true)
                if (jreHealthChecker.isHealthy(jreDir, javaVersion)) {
                    return@withContext jreDir
                }
            }

            if (!autoInstall) {
                throw RuntimeException(
                    "JRE $javaVersion not installed. Enable auto-install or install manually."
                )
            }

            // AUTO INSTALL
            jreDownloader.ensureJre(javaVersion)
        }

    /** Pre-download a JRE without starting a server (UI can call this). */
    suspend fun installJre(javaVersion: Int): File = jreDownloader.ensureJre(javaVersion)

    fun getInstalledJres(): List<Int> {
        val root = fileSystemManager.jreDir
        if (!root.exists()) return emptyList()
        return root.listFiles { f -> f.isDirectory && f.name.startsWith("jre-") }
            ?.mapNotNull { it.name.removePrefix("jre-").toIntOrNull() }
            ?.filter { major ->
                File(root, "jre-$major/bin/java").exists()
            }
            ?: emptyList()
    }

    fun isInstalled(javaVersion: Int): Boolean {
        val bin = File(fileSystemManager.jreDir, "jre-$javaVersion/bin/java")
        return bin.exists()
    }
}
