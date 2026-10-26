package com.nexus.launcher.server

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nexus.launcher.filesystem.FileSystemManager
import com.nexus.launcher.server.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists server configs as JSON under NexusLauncher/servers/index.json.
 * One source of truth for the server list UI.
 */
@Singleton
class ServerStore @Inject constructor(
    private val fileSystemManager: FileSystemManager,
    private val gson: Gson
) {
    private val _servers = MutableStateFlow<List<ServerConfig>>(emptyList())
    val servers: StateFlow<List<ServerConfig>> = _servers.asStateFlow()

    private val indexFile: File
        get() = File(serversRoot(), "index.json")

    private fun serversRoot(): File =
        File(fileSystemManager.minecraftDir.parentFile, "servers").also { it.mkdirs() }

    suspend fun load() = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) {
            _servers.value = emptyList()
            return@withContext
        }
        try {
            val type = object : TypeToken<List<ServerConfig>>() {}.type
            val list: List<ServerConfig> = gson.fromJson(indexFile.readText(), type) ?: emptyList()
            _servers.value = list
        } catch (e: Exception) {
            _servers.value = emptyList()
        }
    }

    suspend fun save(config: ServerConfig) = withContext(Dispatchers.IO) {
        val updated = _servers.value.toMutableList()
        val idx = updated.indexOfFirst { it.id == config.id }
        if (idx >= 0) {
            updated[idx] = config.copy(updatedAt = System.currentTimeMillis())
        } else {
            updated.add(config)
        }
        persist(updated)
    }

    suspend fun delete(serverId: String) = withContext(Dispatchers.IO) {
        val updated = _servers.value.filterNot { it.id == serverId }
        persist(updated)
        // Do not auto-delete world files here — require explicit user confirmation elsewhere
    }

    suspend fun getById(serverId: String): ServerConfig? =
        _servers.value.find { it.id == serverId }

    fun createId(): String = UUID.randomUUID().toString()

    fun defaultServerDir(serverId: String): File =
        File(serversRoot(), serverId).also { it.mkdirs() }

    private fun persist(list: List<ServerConfig>) {
        indexFile.parentFile?.mkdirs()
        indexFile.writeText(gson.toJson(list))
        _servers.value = list
    }
}
