package com.nexus.launcher.version

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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class AssetProgress(val done: Int, val total: Int)

/**
 * Downloads asset index + objects for a version (resources).
 */
@Singleton
class AssetDownloader @Inject constructor(
    private val fileSystemManager: FileSystemManager,
    private val downloadQueueManager: DownloadQueueManager,
    private val sha1Verifier: Sha1Verifier,
    private val httpClient: OkHttpClient
) {
    private val _progress = MutableStateFlow(AssetProgress(0, 0))
    val progress: StateFlow<AssetProgress> = _progress.asStateFlow()

    suspend fun downloadAssets(version: VersionJson): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val indexInfo = version.assetIndex
                ?: return@withContext Result.success(0)

            val indexesDir = File(fileSystemManager.assetsDir, "indexes").also { it.mkdirs() }
            val objectsDir = File(fileSystemManager.assetsDir, "objects").also { it.mkdirs() }
            val indexFile = File(indexesDir, "${indexInfo.id}.json")

            if (!indexFile.exists() || indexFile.length() == 0L) {
                downloadQueueManager.downloadFile(indexInfo.url, indexFile)
            }
            if (!indexInfo.sha1.isNullOrBlank() && !sha1Verifier.verify(indexFile, indexInfo.sha1)) {
                // re-download once
                downloadQueueManager.downloadFile(indexInfo.url, indexFile)
            }

            val root = JSONObject(indexFile.readText())
            val objects = root.optJSONObject("objects") ?: return@withContext Result.success(0)
            val keys = objects.keys().asSequence().toList()
            _progress.value = AssetProgress(0, keys.size)

            var done = 0
            val sem = Semaphore(6)
            coroutineScope {
                keys.map { name ->
                    async {
                        sem.withPermit {
                            val obj = objects.getJSONObject(name)
                            val hash = obj.getString("hash")
                            val prefix = hash.substring(0, 2)
                            val dest = File(objectsDir, "$prefix/$hash")
                            if (!dest.exists() || dest.length() == 0L) {
                                dest.parentFile?.mkdirs()
                                val url = "https://resources.download.minecraft.net/$prefix/$hash"
                                downloadQueueManager.downloadFile(url, dest)
                            }
                            done++
                            if (done % 25 == 0 || done == keys.size) {
                                _progress.value = AssetProgress(done, keys.size)
                            }
                        }
                    }
                }.awaitAll()
            }
            _progress.value = AssetProgress(keys.size, keys.size)
            Result.success(keys.size)
        } catch (e: Exception) {
            Timber.e(e, "Asset download failed")
            Result.failure(e)
        }
    }
}
