package com.nexus.launcher.libraries

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeLibraryExtractor @Inject constructor() {
    suspend fun extractNatives(
        jarFile: File,
        mcDir: File,
        exclude: List<String> = emptyList()
    ) = withContext(Dispatchers.IO) {
        val nativesDir = File(mcDir, "natives/${jarFile.nameWithoutExtension}")
        nativesDir.mkdirs()

        ZipFile(jarFile).use { zip ->
            zip.entries().asSequence()
                .filter { entry ->
                    !entry.isDirectory &&
                    entry.name.endsWith(".so") &&
                    exclude.none { pattern -> matchesPattern(entry.name, pattern) }
                }
                .forEach { entry ->
                    val outFile = File(nativesDir, File(entry.name).name)
                    if (!outFile.exists() || outFile.length() != entry.size) {
                        zip.getInputStream(entry).use { input ->
                            outFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
        }
    }

    private fun matchesPattern(name: String, pattern: String): Boolean {
        val regex = pattern.replace(".", "\\.").replace("*", ".*")
        return Regex(regex).matches(name)
    }
}
