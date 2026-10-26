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

/**
 * Orchestrator for on-device server hosting.
 * Create → install → start → console → stop.
 */
@Singleton
class ServerManager @Inject constructor(
    private val store: ServerStore,
    private val vanillaInstaller: VanillaServerInstaller,
    private val processManager: ServerProcessManager,
    private val ramAdvisor: ServerRamAdvisor
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
            return Result.failure(
                IllegalArgumentException("Unsafe RAM allocation: ${memoryMb} MB")
            )
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

    suspend fun installSoftware(config: ServerConfig): VanillaServerInstaller.InstallResult {
        return when (config.software) {
            ServerSoftware.VANILLA -> vanillaInstaller.install(config)
            else -> VanillaServerInstaller.InstallResult(
                false, null,
                "${config.software.displayName} installer not implemented yet. Vanilla works now."
            )
        }
    }

    suspend fun acceptEula(serverId: String): Result<ServerConfig> {
        val config = store.getById(serverId)
            ?: return Result.failure(IllegalArgumentException("Server not found"))
        val updated = config.copy(eulaAccepted = true)
        // Write eula.txt immediately
        File(config.serverDir, "eula.txt").writeText(
            "# Accepted by user in Nexus Launcher\neula=true\n"
        )
        store.save(updated)
        return Result.success(updated)
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

    fun ramAdvice(alsoPlaying: Boolean = false) = ramAdvisor.advise(alsoPlaying)

    fun statusOf(serverId: String): ServerRuntimeStatus =
        processManager.getStatus(serverId)
            ?: ServerRuntimeStatus(serverId, ServerState.STOPPED)
}
