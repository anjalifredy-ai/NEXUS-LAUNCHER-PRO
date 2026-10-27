package com.nexus.launcher.version

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FullClientInstaller @Inject constructor(
    private val versionManager: VersionManager,
    private val clientVersionInstaller: ClientVersionInstaller,
    private val libraryDownloader: LibraryDownloader,
    private val assetDownloader: AssetDownloader
) {
    sealed class Stage {
        object Idle : Stage()
        data class Meta(val id: String) : Stage()
        data class ClientJar(val id: String, val fraction: Float) : Stage()
        data class Libraries(val id: String, val done: Int, val total: Int) : Stage()
        data class Assets(val id: String, val done: Int, val total: Int) : Stage()
        data class Done(val id: String) : Stage()
        data class Error(val id: String, val message: String) : Stage()
    }

    private val _stage = MutableStateFlow<Stage>(Stage.Idle)
    val stage: StateFlow<Stage> = _stage.asStateFlow()

    suspend fun install(versionId: String, includeAssets: Boolean = true): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                _stage.value = Stage.Meta(versionId)
                // Prefer resolved (inheritance) so Fabric/Forge get vanilla libs+assets
                val details = versionManager.getResolvedVersion(versionId)

                val jarResult = clientVersionInstaller.installClientJar(versionId)
                jarResult.onFailure {
                    // Inherited versions may only have parent client jar
                    if (details.downloads?.client != null) {
                        _stage.value = Stage.Error(versionId, it.message ?: "Client jar failed")
                        return@withContext Result.failure(it)
                    }
                    Timber.w(it, "Client jar skip for inherited version")
                }

                _stage.value = Stage.Libraries(versionId, 0, details.libraries.size)
                val libResult = libraryDownloader.downloadLibraries(details)
                libResult.onFailure {
                    _stage.value = Stage.Error(versionId, it.message ?: "Libraries failed")
                    return@withContext Result.failure(it)
                }

                if (includeAssets) {
                    _stage.value = Stage.Assets(versionId, 0, 0)
                    assetDownloader.downloadAssets(details).onFailure {
                        Timber.w(it, "Assets incomplete")
                    }
                }

                _stage.value = Stage.Done(versionId)
                Result.success(Unit)
            } catch (e: Exception) {
                _stage.value = Stage.Error(versionId, e.message ?: "Install failed")
                Result.failure(e)
            }
        }
}
