package com.nexus.launcher.launch

import com.nexus.launcher.auth.AuthManager
import com.nexus.launcher.filesystem.FileSystemManager
import com.nexus.launcher.jre.JavaRuntimeManager
import com.nexus.launcher.libraries.ClasspathBuilder
import com.nexus.launcher.libraries.LibraryManager
import com.nexus.launcher.settings.SettingsManager
import com.nexus.launcher.version.FullClientInstaller
import com.nexus.launcher.version.LibraryDownloader
import com.nexus.launcher.version.VersionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LaunchPipeline @Inject constructor(
    private val authManager: AuthManager,
    private val versionManager: VersionManager,
    private val libraryManager: LibraryManager,
    private val libraryDownloader: LibraryDownloader,
    private val classpathBuilder: ClasspathBuilder,
    private val javaRuntimeManager: JavaRuntimeManager,
    private val argumentBuilder: ArgumentBuilder,
    private val processSpawner: ProcessSpawner,
    private val processOutputHandler: ProcessOutputHandler,
    private val processLifecycleManager: ProcessLifecycleManager,
    private val settingsManager: SettingsManager,
    private val fileSystemManager: FileSystemManager,
    private val fullClientInstaller: FullClientInstaller
) {
    private val _launchState = MutableStateFlow<LaunchState>(LaunchState.Idle)
    val launchState: StateFlow<LaunchState> = _launchState.asStateFlow()

    private var currentProcess: Process? = null

    suspend fun launchMinecraft(versionId: String, autoInstall: Boolean = true): LaunchResult =
        withContext(Dispatchers.IO) {
            try {
                _launchState.value = LaunchState.Preparing("Validating account")
                val account = authManager.getActiveAccount()
                    ?: return@withContext LaunchResult.Error("No active account — login offline first")

                _launchState.value = LaunchState.Preparing("Loading version (resolved)")
                val versionJson = versionManager.getResolvedVersion(versionId)

                if (autoInstall) {
                    _launchState.value = LaunchState.Preparing("Ensuring client files")
                    fullClientInstaller.install(versionId, includeAssets = true).onFailure {
                        return@withContext LaunchResult.Error(it.message ?: "Client install failed")
                    }
                }

                _launchState.value = LaunchState.Preparing("Checking Java runtime")
                val javaVersion = versionJson.javaVersion?.majorVersion ?: 17
                val jrePath = javaRuntimeManager.getJrePath(javaVersion, autoInstall = true)

                _launchState.value = LaunchState.Preparing("Libraries")
                libraryDownloader.downloadLibraries(versionJson).onFailure {
                    // fallback older path
                    try {
                        libraryManager.downloadLibrariesStream(versionJson, fileSystemManager.minecraftDir)
                            .collect { }
                    } catch (e: Exception) {
                        return@withContext LaunchResult.Error(e.message ?: "Libraries failed")
                    }
                }

                _launchState.value = LaunchState.Preparing("Building classpath")
                val mcDir = fileSystemManager.minecraftDir
                val classpath = classpathBuilder.buildClasspath(versionJson, mcDir)

                _launchState.value = LaunchState.Preparing("Natives dir")
                val nativesDir = File(mcDir, "natives/${versionJson.id}").also { it.mkdirs() }

                _launchState.value = LaunchState.Preparing("Building arguments")
                val memoryMb = settingsManager.memoryMb.first()
                val jvmArgs = settingsManager.jvmArguments.first()
                val renderer = settingsManager.renderer.first()
                val width = settingsManager.windowWidth.first()
                val height = settingsManager.windowHeight.first()

                val arguments = argumentBuilder.buildArguments(
                    versionJson = versionJson,
                    account = account,
                    mcDir = mcDir.absolutePath,
                    nativesDir = nativesDir.absolutePath,
                    classpath = classpath,
                    javaPath = File(jrePath, "bin/java").absolutePath,
                    memoryMb = memoryMb,
                    jvmArgs = jvmArgs,
                    renderer = renderer,
                    width = width,
                    height = height
                )

                _launchState.value = LaunchState.Launching
                currentProcess = processSpawner.spawnProcess(arguments)
                processOutputHandler.start(currentProcess!!)

                processLifecycleManager.monitor(currentProcess!!) { exitCode ->
                    _launchState.value =
                        if (exitCode == 0) LaunchState.Completed else LaunchState.Crashed(exitCode)
                    currentProcess = null
                }

                _launchState.value = LaunchState.Running
                LaunchResult.Success
            } catch (e: Exception) {
                _launchState.value = LaunchState.Error(e.message ?: "Unknown error")
                LaunchResult.Error(e.message ?: "Unknown error")
            }
        }

    fun killMinecraft() {
        currentProcess?.destroyForcibly()
        currentProcess = null
        _launchState.value = LaunchState.Idle
    }
}

sealed class LaunchState {
    object Idle : LaunchState()
    data class Preparing(val step: String) : LaunchState()
    object Launching : LaunchState()
    object Running : LaunchState()
    object Completed : LaunchState()
    data class Crashed(val exitCode: Int) : LaunchState()
    data class Error(val message: String) : LaunchState()
}

sealed class LaunchResult {
    object Success : LaunchResult()
    data class Error(val message: String) : LaunchResult()
}
