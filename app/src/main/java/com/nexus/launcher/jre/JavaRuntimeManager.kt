package com.nexus.launcher.jre

import com.nexus.launcher.filesystem.FileSystemManager
import com.nexus.launcher.util.DeviceUtil
import com.nexus.launcher.util.OsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JavaRuntimeManager @Inject constructor(
    private val fileSystemManager: FileSystemManager,
    private val jreHealthChecker: JreHealthChecker
) {
    suspend fun getJrePath(javaVersion: Int): File = withContext(Dispatchers.IO) {
        val jreDir = File(fileSystemManager.jreDir, "jre-$javaVersion")
        val javaBinary = File(jreDir, "bin/java" + OsUtil.executableExtension())

        if (jreHealthChecker.isHealthy(jreDir, javaVersion)) {
            return@withContext jreDir
        }

        // TODO: download + extract via JreDownloader (next batch)
        // For now return path so launch pipeline can proceed once JRE is present
        if (!javaBinary.exists()) {
            throw RuntimeException("JRE $javaVersion not installed. Download required.")
        }

        if (!OsUtil.isWindows()) {
            javaBinary.setExecutable(true)
        }
        jreDir
    }

    fun getInstalledJres(): List<Int> {
        val root = fileSystemManager.jreDir
        if (!root.exists()) return emptyList()
        return root.listFiles { f -> f.isDirectory && f.name.startsWith("jre-") }
            ?.mapNotNull { it.name.removePrefix("jre-").toIntOrNull() }
            ?: emptyList()
    }
}
