package com.nexus.launcher.jre

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JreHealthChecker @Inject constructor() {
    suspend fun isHealthy(jreDir: File, expectedVersion: Int): Boolean =
        withContext(Dispatchers.IO) {
            val javaBinary = File(jreDir, "bin/java")
            if (!javaBinary.exists()) return@withContext false

            return@withContext try {
                val process = ProcessBuilder(javaBinary.absolutePath, "-version")
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                output.contains("version \"$expectedVersion") ||
                    output.contains("version \"1.$expectedVersion")
            } catch (e: Exception) {
                false
            }
        }
}
