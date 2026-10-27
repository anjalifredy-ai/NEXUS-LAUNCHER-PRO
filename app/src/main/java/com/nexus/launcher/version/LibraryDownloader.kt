package com.nexus.launcher.version

import com.nexus.launcher.data.model.Library
import com.nexus.launcher.data.model.VersionJson
import com.nexus.launcher.filesystem.FileSystemManager
import com.nexus.launcher.filesystem.Sha1Verifier
import com.nexus.launcher.network.DownloadQueueManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class LibraryProgress(
    val done: Int,
    val total: Int,
    val currentName: String = ""
)

@Singleton
class LibraryDownloader @Inject constructor(
    private val fileSystemManager: FileSystemManager,
    private val downloadQueueManager: DownloadQueueManager,
    private val sha1Verifier: Sha1Verifier
) {
    private val _progress = MutableStateFlow(LibraryProgress(0, 0))
    val progress: StateFlow<LibraryProgress> = _progress.asStateFlow()

    suspend fun downloadLibraries(version: VersionJson): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val needed = version.libraries.filter { lib ->
                rulesAllow(lib) && artifactPath(lib) != null
            }
            _progress.value = LibraryProgress(0, needed.size)
            var completed = 0
            val semaphore = Semaphore(4)

            coroutineScope {
                needed.map { lib ->
                    async {
                        semaphore.withPermit {
                            downloadOne(lib)
                            completed++
                            _progress.value = LibraryProgress(completed, needed.size, lib.name)
                        }
                    }
                }.awaitAll()
            }
            Result.success(needed.size)
        } catch (e: Exception) {
            Timber.e(e, "Library download failed")
            Result.failure(e)
        }
    }

    private suspend fun downloadOne(lib: Library) {
        val path = artifactPath(lib) ?: return
        val url = artifactUrl(lib) ?: return
        val target = File(fileSystemManager.librariesDir, path)
        val sha = lib.artifact?.sha1
        if (target.exists() && target.length() > 0) {
            if (sha.isNullOrBlank() || sha1Verifier.verify(target, sha)) return
        }
        target.parentFile?.mkdirs()
        downloadQueueManager.downloadFile(url, target)
        if (!sha.isNullOrBlank() && !sha1Verifier.verify(target, sha)) {
            target.delete()
            throw IllegalStateException("SHA1 fail: ${lib.name}")
        }
    }

    private fun artifactPath(lib: Library): String? {
        lib.artifact?.path?.takeIf { it.isNotBlank() }?.let { return it }
        val parts = lib.name.split(":")
        if (parts.size < 3) return null
        val group = parts[0].replace('.', '/')
        val artifact = parts[1]
        val ver = parts[2]
        return "$group/$artifact/$ver/$artifact-$ver.jar"
    }

    private fun artifactUrl(lib: Library): String? {
        lib.artifact?.url?.takeIf { it.isNotBlank() }?.let { return it }
        val path = artifactPath(lib) ?: return null
        return "https://libraries.minecraft.net/$path"
    }

    private fun rulesAllow(lib: Library): Boolean {
        val rules = lib.rules ?: return true
        var allowed = true
        for (rule in rules) {
            val osName = rule.osName
            when (rule.action) {
                "allow" -> {
                    if (osName == "windows" || osName == "osx") allowed = false
                    if (osName == null || osName == "linux") allowed = true
                }
                "disallow" -> {
                    if (osName == null || osName == "linux") allowed = false
                }
            }
        }
        return allowed
    }

    fun classpathJars(version: VersionJson): List<File> {
        return version.libraries.mapNotNull { lib ->
            if (!rulesAllow(lib)) return@mapNotNull null
            val path = artifactPath(lib) ?: return@mapNotNull null
            File(fileSystemManager.librariesDir, path).takeIf { it.exists() }
        }
    }
}
