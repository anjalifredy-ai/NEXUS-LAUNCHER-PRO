package com.nexus.launcher.server

import com.nexus.launcher.server.model.ServerConfig
import com.nexus.launcher.server.model.ServerRuntimeStatus
import com.nexus.launcher.server.model.ServerSoftware
import com.nexus.launcher.server.model.ServerState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerManager @Inject constructor(
    private val store: ServerStore,
    private val vanillaInstaller: VanillaServerInstaller,
    private val paperInstaller: PaperServerInstaller,
    private val fabricInstaller: FabricServerInstaller,
    private val processManager: ServerProcessManager,
    private val ramAdvisor: ServerRamAdvisor,
    private val worldManager: ServerWorldManager,
    private val pluginManager: ServerPluginManager,
    private val viaVersionInstaller: ViaVersionInstaller
) {
    val servers: StateFlow<List<ServerConfig>> = store.servers
    val runtimeStatus: StateFlow<Map<String, ServerRuntimeStatus>> = processManager.status
    val consoleLines: SharedFlow<ServerProcessManager.ConsoleLine> = processManager.consoleLines

    suspend fun loadServers() = store.load()

    suspend fun createServer(
        name: String,
        minecraftVersion: String,
        software: ServerSoftware,
        memoryMb: Int,
        port: Int = 25565,
        motd: String = "A Nexus Minecraft Server",
        eulaAccepted: Boolean = false
    ): Result<ServerConfig> {
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Name required"))
        if (!ramAdvisor.isAllocationSafe(memoryMb)) {
            return Result.failure(IllegalArgumentException("Unsafe RAM allocation: ${memoryMb} MB"))
        }

        val id = store.createId()
        val dir = store.defaultServerDir(id)
        val config = ServerConfig(
            id = id,
            name = name.trim(),
            motd = motd,
            minecraftVersion = minecraftVersion,
            software = software,
            serverDir = dir.absolutePath,
            port = port,
            memoryMb = memoryMb,
            eulaAccepted = eulaAccepted
        )
        store.save(config)
        return Result.success(config)
    }

    data class InstallOutcome(
        val success: Boolean,
        val message: String,
        val jar: File? = null
    )

    suspend fun installSoftware(config: ServerConfig, installViaVersion: Boolean = false): InstallOutcome {
        val base = when (config.software) {
            ServerSoftware.VANILLA -> {
                val r = vanillaInstaller.install(config)
                InstallOutcome(r.success, r.error ?: "Vanilla installed", r.serverJar)
            }
            ServerSoftware.PAPER -> {
                val r = paperInstaller.install(config)
                InstallOutcome(
                    r.success,
                    r.error ?: "Paper build ${r.buildNumber} installed",
                    r.serverJar
                )
            }
            ServerSoftware.FABRIC -> {
                val r = fabricInstaller.install(config)
                InstallOutcome(
                    r.success,
                    r.error ?: "Fabric loader ${r.loaderVersion} installed",
                    r.serverJar
                )
            }
            else -> InstallOutcome(
                false,
                "${config.software.displayName} installer coming next. Vanilla / Paper / Fabric work now."
            )
        }
        if (base.success && installViaVersion && config.software == ServerSoftware.PAPER) {
            val via = viaVersionInstaller.installForCrossVersion(config)
            return base.copy(
                message = base.message + " | " + via.message
            )
        }
        return base
    }

    suspend fun installViaVersion(serverId: String): ViaVersionInstaller.Result {
        val config = store.getById(serverId)
            ?: return ViaVersionInstaller.Result(false, "Server not found")
        return viaVersionInstaller.installForCrossVersion(config)
    }

    suspend fun acceptEula(serverId: String): Result<ServerConfig> {
        val config = store.getById(serverId)
            ?: return Result.failure(IllegalArgumentException("Server not found"))
        val updated = config.copy(eulaAccepted = true)
        File(config.serverDir, "eula.txt").writeText("# Accepted in Nexus Launcher\neula=true\n")
        store.save(updated)
        return Result.success(updated)
    }

    suspend fun updateSettings(config: ServerConfig): Result<ServerConfig> {
        val propsFile = File(config.serverDir, "server.properties")
        ServerPropertiesWriter.applyConfig(propsFile, config)
        store.save(config)
        return Result.success(config)
    }

    suspend fun startServer(serverId: String): Result<Unit> {
        val config = store.getById(serverId)
            ?: return Result.failure(IllegalArgumentException("Server not found"))
        return processManager.start(config)
    }

    suspend fun stopServer(serverId: String, force: Boolean = false) {
        processManager.stop(serverId, force)
    }

    fun sendCommand(serverId: String, command: String): Boolean =
        processManager.sendCommand(serverId, command)

    suspend fun deleteServer(serverId: String, deleteFiles: Boolean = false) {
        processManager.stop(serverId, force = true)
        val config = store.getById(serverId)
        store.delete(serverId)
        if (deleteFiles && config != null) {
            File(config.serverDir).deleteRecursively()
        }
    }

    suspend fun listServerWorlds(serverId: String) =
        store.getById(serverId)?.let { worldManager.listServerWorlds(it) } ?: emptyList()

    suspend fun listLauncherSaves() = worldManager.listLauncherSaves()

    suspend fun importWorld(serverId: String, sourcePath: String, targetName: String? = null): Result<File> {
        val config = store.getById(serverId)
            ?: return Result.failure(IllegalArgumentException("Server not found"))
        return worldManager.importWorldToServer(config, File(sourcePath), targetName ?: File(sourcePath).name)
    }

    suspend fun setActiveWorld(serverId: String, worldName: String): Result<Unit> {
        val config = store.getById(serverId)
            ?: return Result.failure(IllegalArgumentException("Server not found"))
        val result = worldManager.setActiveWorld(config, worldName)
        if (result.isSuccess) store.save(config.copy(worldDir = worldName))
        return result
    }

    suspend fun listPlugins(serverId: String) =
        store.getById(serverId)?.let { pluginManager.list(it) } ?: emptyList()

    suspend fun installPluginFromUrl(serverId: String, url: String, fileName: String): Result<File> {
        val config = store.getById(serverId)
            ?: return Result.failure(IllegalArgumentException("Server not found"))
        return pluginManager.installFromUrl(config, url, fileName)
    }

    suspend fun togglePlugin(serverId: String, fileName: String, enabled: Boolean): Result<Boolean> {
        val config = store.getById(serverId)
            ?: return Result.failure(IllegalArgumentException("Server not found"))
        return pluginManager.setEnabled(config, fileName, enabled)
    }

    suspend fun deletePlugin(serverId: String, fileName: String): Result<Unit> {
        val config = store.getById(serverId)
            ?: return Result.failure(IllegalArgumentException("Server not found"))
        return pluginManager.delete(config, fileName)
    }

    fun ramAdvice(alsoPlaying: Boolean = false) = ramAdvisor.advise(alsoPlaying)

    fun statusOf(serverId: String): ServerRuntimeStatus =
        processManager.getStatus(serverId)
            ?: ServerRuntimeStatus(serverId, ServerState.STOPPED)

    suspend fun getById(serverId: String) = store.getById(serverId)
}
