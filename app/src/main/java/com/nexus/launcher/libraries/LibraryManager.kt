package com.nexus.launcher.libraries

import com.nexus.launcher.data.model.Library
import com.nexus.launcher.data.model.VersionJson
import com.nexus.launcher.filesystem.Sha1Verifier
import com.nexus.launcher.network.DownloadQueueManager
import com.nexus.launcher.util.OsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryManager @Inject constructor(
    private val nativeLibraryExtractor: NativeLibraryExtractor,
    private val ruleEvaluator: RuleEvaluator,
    private val sha1Verifier: Sha1Verifier,
    private val downloadQueueManager: DownloadQueueManager
) {
    fun filterLibrariesForPlatform(libraries: List<Library>): List<Library> {
        return libraries.filter { lib -> ruleEvaluator.evaluateRules(lib.rules) }
    }

    fun downloadLibrariesStream(
        versionJson: VersionJson,
        mcDir: File
    ): Flow<LibraryDownloadProgress> = flow {
        val libs = filterLibrariesForPlatform(versionJson.libraries)
        val total = libs.size
        var processed = 0

        libs.forEach { lib ->
            lib.artifact?.let { artifact ->
                val targetFile = File(mcDir, "libraries/${artifact.path}")
                if (!targetFile.exists() || targetFile.length() != artifact.size ||
                    !sha1Verifier.verifyFile(targetFile, artifact.sha1)
                ) {
                    targetFile.parentFile?.mkdirs()
                    downloadQueueManager.downloadFile(artifact.url, targetFile)
                }
            }

            val nativeClassifier = when {
                OsUtil.isLinuxArm64() -> lib.natives?.linuxArm64 ?: lib.natives?.linux
                OsUtil.isLinux() -> lib.natives?.linux
                else -> null
            }

            nativeClassifier?.let { classifierKey ->
                val actualKey = classifierKey.replace("${arch}", "1")
                lib.classifiers[actualKey]?.let { nativeArtifact ->
                    val targetFile = File(mcDir, "libraries/${nativeArtifact.path}")
                    if (!targetFile.exists() || !sha1Verifier.verifyFile(targetFile, nativeArtifact.sha1)) {
                        targetFile.parentFile?.mkdirs()
                        downloadQueueManager.downloadFile(nativeArtifact.url, targetFile)
                    }
                    nativeLibraryExtractor.extractNatives(targetFile, mcDir, lib.exclude)
                }
            }

            processed++
            emit(LibraryDownloadProgress(processed, total, lib.name))
        }
    }.flowOn(Dispatchers.IO)
}

data class LibraryDownloadProgress(
    val processed: Int,
    val total: Int,
    val currentLibrary: String
)
