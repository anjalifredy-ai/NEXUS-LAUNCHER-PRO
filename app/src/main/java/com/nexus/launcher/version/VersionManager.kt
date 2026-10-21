package com.nexus.launcher.version

import com.nexus.launcher.data.model.VersionJson
import com.nexus.launcher.data.remote.MojangApi
import com.nexus.launcher.data.remote.VersionEntry
import com.nexus.launcher.data.remote.VersionManifestResponse
import com.nexus.launcher.filesystem.FileSystemManager
import com.nexus.launcher.filesystem.Sha1Verifier
import com.nexus.launcher.network.DownloadQueueManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VersionManager @Inject constructor(
    private val mojangApi: MojangApi,
    private val fileSystemManager: FileSystemManager,
    private val sha1Verifier: Sha1Verifier,
    private val downloadQueueManager: DownloadQueueManager,
    private val versionJsonParser: VersionJsonParser
) {
    private var cachedManifest: VersionManifestResponse? = null

    suspend fun getVersionManifest(forceRefresh: Boolean = false): VersionManifestResponse =
        withContext(Dispatchers.IO) {
            if (!forceRefresh && cachedManifest != null) {
                return@withContext cachedManifest!!
            }
            val response = mojangApi.getVersionManifest()
            if (!response.isSuccessful) {
                throw RuntimeException("Failed to fetch version manifest: ${response.code()}")
            }
            val body = response.body() ?: throw RuntimeException("Empty version manifest")
            cachedManifest = body
            body
        }

    suspend fun getAvailableVersions(): List<VersionEntry> {
        return getVersionManifest().versions
    }

    suspend fun getVersionDetails(versionId: String): VersionJson = withContext(Dispatchers.IO) {
        val versionDir = fileSystemManager.getVersionDir(versionId)
        val jsonFile = File(versionDir, "$versionId.json")

        if (jsonFile.exists()) {
            return@withContext versionJsonParser.parse(jsonFile.readText(), versionId)
        }

        val manifest = getVersionManifest()
        val entry = manifest.versions.find { it.id == versionId }
            ?: throw RuntimeException("Version not found: $versionId")

        val response = mojangApi.downloadVersionJson(entry.url)
        if (!response.isSuccessful) {
            throw RuntimeException("Failed to download version JSON: ${response.code()}")
        }
        val raw = response.body()?.string() ?: throw RuntimeException("Empty version JSON")
        jsonFile.writeText(raw)

        versionJsonParser.parse(raw, versionId)
    }
}
