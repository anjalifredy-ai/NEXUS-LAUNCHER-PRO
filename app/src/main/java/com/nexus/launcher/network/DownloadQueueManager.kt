package com.nexus.launcher.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadQueueManager @Inject constructor(
    private val httpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ConcurrentLinkedQueue<DownloadTask>()
    private val activeDownloads = AtomicInteger(0)
    private val maxConcurrent = 5

    private val _downloadProgress = MutableSharedFlow<DownloadProgressEvent>(extraBufferCapacity = 100)
    val downloadProgress: SharedFlow<DownloadProgressEvent> = _downloadProgress.asSharedFlow()

    private val _queueState = MutableStateFlow<DownloadQueueState>(DownloadQueueState.Idle)
    val queueState: StateFlow<DownloadQueueState> = _queueState.asStateFlow()

    fun enqueueDownload(url: String, targetFile: File, onComplete: () -> Unit = {}) {
        val task = DownloadTask(url, targetFile, onComplete)
        queue.add(task)
        processQueue()
    }

    fun downloadFile(url: String, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Download failed: ${response.code}")
            }
            response.body?.byteStream()?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    suspend fun downloadFileWithProgress(
        url: String,
        targetFile: File,
        onProgress: (Float) -> Unit
    ) {
        targetFile.parentFile?.mkdirs()
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Download failed: ${response.code}")
            }
            val body = response.body ?: throw RuntimeException("Empty response body")
            val totalBytes = body.contentLength()
            val totalRead = AtomicInteger(0)

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead.addAndGet(read)
                        if (totalBytes > 0) {
                            val progress = totalRead.get().toFloat() / totalBytes
                            onProgress(progress)
                        }
                    }
                }
            }
        }
    }

    fun downloadFileWithProgressStream(url: String, targetFile: File): Flow<Float> = flow {
        targetFile.parentFile?.mkdirs()
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Download failed: ${response.code}")
            }
            val body = response.body ?: throw RuntimeException("Empty response")
            val totalBytes = body.contentLength()
            var totalRead = 0L

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (totalBytes > 0) {
                            emit(totalRead.toFloat() / totalBytes)
                        }
                    }
                }
            }
        }
        emit(1.0f)
    }

    private fun processQueue() {
        scope.launch {
            while (queue.isNotEmpty() && activeDownloads.get() < maxConcurrent) {
                val task = queue.poll() ?: break
                activeDownloads.incrementAndGet()
                _queueState.value = DownloadQueueState.Active(queue.size, activeDownloads.get())

                scope.launch {
                    try {
                        downloadFile(task.url, task.targetFile)
                        task.onComplete()
                        _downloadProgress.tryEmit(
                            DownloadProgressEvent(task.url, 1.0f, true)
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Download failed: ${task.url}")
                        _downloadProgress.tryEmit(
                            DownloadProgressEvent(task.url, -1.0f, false, e.message)
                        )
                    } finally {
                        activeDownloads.decrementAndGet()
                        _queueState.value = DownloadQueueState.Active(queue.size, activeDownloads.get())
                    }
                }
            }
            if (queue.isEmpty()) {
                _queueState.value = DownloadQueueState.Idle
            }
        }
    }
}

data class DownloadTask(
    val url: String,
    val targetFile: File,
    val onComplete: () -> Unit
)

data class DownloadProgressEvent(
    val url: String,
    val progress: Float,
    val completed: Boolean,
    val error: String? = null
)

sealed class DownloadQueueState {
    object Idle : DownloadQueueState()
    data class Active(val pendingCount: Int, val activeCount: Int) : DownloadQueueState()
}
