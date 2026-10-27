package com.nexus.launcher.version

import com.nexus.launcher.filesystem.FileSystemManager
import com.nexus.launcher.filesystem.Sha1Verifier
import com.nexus.launcher.network.DownloadQueueManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads client version jar (+ ensures version JSON on disk) with SHA1 verify.
 */
@Singleton
class ClientVersionInstaller @Inject constructor(
    private val versionManager: VersionManager,
    private val fileSystemManager: FileSystemManager,
    private val downloadQueueManager: DownloadQueueManager,
    private val sha1Verifier: Sha1Verifier
) {
    sealed class Progress {
        object Idle : Progress()
        data class FetchingMeta(val versionId: String) : Progress()
        data class DownloadingJar(val versionId: String, val fraction: Float) : Progress()
        data class Verifying(val versionId: String) : Progress()
        data class Done(val versionId: String, val jar: File) : Progress()
        data class Error(val versionId: String, val message: String) : Progress()
    }

    private val _progress = MutableStateFlow<Progress>(Progress.Idle)
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    suspend fun installClientJar(versionId: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            _progress.value = Progress.FetchingMeta(versionId)
            val versionJson = versionManager.getVersionDetails(versionId)
            val client = versionJson.downloads?.client
                ?: return@withContext Result.failure(
                    IllegalStateException("No client download for $versionId")
                )

            val dir = fileSystemManager.getVersionDir(versionId)
            val jar = File(dir, "$versionId.jar")

            if (jar.exists() && !client.sha1.isNullOrBlank() && sha1Verifier.verify(jar, client.sha1)) {
                _progress.value = Progress.Done(versionId, jar)
                return@withContext Result.success(jar)
            }

            _progress.value = Progress.DownloadingJar(versionId, 0f)
            downloadQueueManager.downloadFileWithProgress(client.url, jar) { p ->
                _progress.value = Progress.DownloadingJar(versionId, p)
            }

            _progress.value = Progress.Verifying(versionId)
            if (!client.sha1.isNullOrBlank() && !sha1Verifier.verify(jar, client.sha1)) {
                jar.delete()
                val msg = "SHA1 mismatch for client jar"
                _progress.value = Progress.Error(versionId, msg)
                return@withContext Result.failure(IllegalStateException(msg))
            }

            _progress.value = Progress.Done(versionId, jar)
            Result.success(jar)
        } catch (e: Exception) {
            Timber.e(e, "Client install $versionId")
            _progress.value = Progress.Error(versionId, e.message ?: "Install failed")
            Result.failure(e)
        }
    }

    fun isClientInstalled(versionId: String): Boolean {
        val jar = File(fileSystemManager.getVersionDir(versionId), "$versionId.jar")
        return jar.exists() && jar.length() > 0
    }
}
