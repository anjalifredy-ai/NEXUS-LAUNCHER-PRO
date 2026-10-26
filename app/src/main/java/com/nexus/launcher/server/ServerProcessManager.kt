package com.nexus.launcher.server

import com.nexus.launcher.jre.JavaRuntimeManager
import com.nexus.launcher.server.model.ServerConfig
import com.nexus.launcher.server.model.ServerRuntimeStatus
import com.nexus.launcher.server.model.ServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs at most one active Minecraft Java server process on-device by default.
 * Real ProcessBuilder. Real stdout. ONLINE only after readiness markers.
 * Java major from ServerJavaResolver (8 / 17 / 21).
 */
@Singleton
class ServerProcessManager @Inject constructor(
    private val javaRuntimeManager: JavaRuntimeManager,
    private val ramAdvisor: ServerRamAdvisor,
    private val javaResolver: ServerJavaResolver
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val processes = ConcurrentHashMap<String, Process>()
    private val writers = ConcurrentHashMap<String, BufferedWriter>()
    private val startTimes = ConcurrentHashMap<String, Long>()
    private val outputJobs = ConcurrentHashMap<String, Job>()

    private val _status = MutableStateFlow<Map<String, ServerRuntimeStatus>>(emptyMap())
    val status: StateFlow<Map<String, ServerRuntimeStatus>> = _status.asStateFlow()

    private val _consoleLines = MutableSharedFlow<ConsoleLine>(extraBufferCapacity = 256)
    val consoleLines: SharedFlow<ConsoleLine> = _consoleLines.asSharedFlow()

    data class ConsoleLine(
        val serverId: String,
        val line: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    suspend fun start(config: ServerConfig): Result<Unit> = withContext(Dispatchers.IO) {
        if (processes.containsKey(config.id)) {
            return@withContext Result.failure(IllegalStateException("Server already running"))
        }
        if (processes.isNotEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Another server is already running. Stop it first.")
            )
        }
        if (!config.eulaAccepted) {
            return@withContext Result.failure(
                IllegalStateException("Minecraft EULA not accepted.")
            )
        }
        if (!ramAdvisor.isAllocationSafe(config.memoryMb)) {
            return@withContext Result.failure(
                IllegalStateException("Requested ${config.memoryMb} MB outside safe range.")
            )
        }

        val serverDir = File(config.serverDir)
        val jar = File(serverDir, "server.jar")
        if (!jar.exists()) {
            return@withContext Result.failure(IllegalStateException("server.jar not found. Install first."))
        }

        updateStatus(config.id, ServerState.STARTING, config.memoryMb)

        try {
            val javaMajor = javaResolver.requiredJava(config.minecraftVersion).major
            val jreDir = javaRuntimeManager.getJrePath(javaMajor)
            val javaBin = File(jreDir, "bin/java").absolutePath

            val args = mutableListOf(
                javaBin,
                "-Xms${(config.memoryMb / 2).coerceAtLeast(256)}m",
                "-Xmx${config.memoryMb}m",
                "-XX:+UseG1GC",
                "-XX:+ParallelRefProcEnabled",
                "-Djava.awt.headless=true"
            )
            if (config.jvmArgs.isNotBlank()) {
                args.addAll(config.jvmArgs.split(" ").filter { it.isNotBlank() })
            }
            args.add("-jar")
            args.add(jar.absolutePath)
            args.add("nogui")

            val process = ProcessBuilder(args)
                .directory(serverDir)
                .redirectErrorStream(true)
                .start()

            processes[config.id] = process
            startTimes[config.id] = System.currentTimeMillis()
            writers[config.id] = BufferedWriter(OutputStreamWriter(process.outputStream))

            outputJobs[config.id] = scope.launch {
                try {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            _consoleLines.tryEmit(ConsoleLine(config.id, line))
                            if (isReadyMarker(line)) {
                                updateStatus(config.id, ServerState.ONLINE, config.memoryMb)
                            }
                            if (line.contains("Failed to start", ignoreCase = true) ||
                                line.contains("OutOfMemoryError", ignoreCase = true)
                            ) {
                                updateStatus(config.id, ServerState.FAILED, config.memoryMb, error = line)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Server output read error")
                } finally {
                    val code = try {
                        process.waitFor()
                    } catch (_: Exception) {
                        -1
                    }
                    cleanup(config.id)
                    updateStatus(
                        config.id,
                        if (code == 0) ServerState.STOPPED else ServerState.FAILED,
                        config.memoryMb,
                        error = if (code != 0) "Exit code $code" else null
                    )
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            cleanup(config.id)
            updateStatus(config.id, ServerState.FAILED, config.memoryMb, error = e.message)
            Result.failure(e)
        }
    }

    suspend fun stop(serverId: String, force: Boolean = false) = withContext(Dispatchers.IO) {
        val process = processes[serverId] ?: return@withContext
        updateStatus(serverId, ServerState.STOPPING, _status.value[serverId]?.memoryMaxMb?.toInt() ?: 0)
        try {
            if (!force) {
                sendCommand(serverId, "stop")
                var waited = 0
                while (process.isAlive && waited < 15000) {
                    Thread.sleep(500)
                    waited += 500
                }
            }
            if (process.isAlive) process.destroyForcibly()
        } catch (e: Exception) {
            Timber.e(e, "Stop failed")
            process.destroyForcibly()
        } finally {
            cleanup(serverId)
            updateStatus(serverId, ServerState.STOPPED, 0)
        }
    }

    fun sendCommand(serverId: String, command: String): Boolean {
        val writer = writers[serverId] ?: return false
        return try {
            writer.write(command)
            writer.newLine()
            writer.flush()
            true
        } catch (e: Exception) {
            Timber.e(e, "Command send failed")
            false
        }
    }

    fun getStatus(serverId: String): ServerRuntimeStatus? = _status.value[serverId]

    fun isAnyRunning(): Boolean = processes.isNotEmpty()

    private fun isReadyMarker(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("done (") ||
            lower.contains("for help, type") ||
            (lower.contains("started") && lower.contains("server"))
    }

    private fun cleanup(serverId: String) {
        try {
            writers.remove(serverId)?.close()
        } catch (_: Exception) {
        }
        processes.remove(serverId)
        startTimes.remove(serverId)
        outputJobs.remove(serverId)?.cancel()
    }

    private fun updateStatus(
        serverId: String,
        state: ServerState,
        memoryMaxMb: Int,
        error: String? = null
    ) {
        val uptime = startTimes[serverId]?.let { System.currentTimeMillis() - it } ?: 0L
        val map = _status.value.toMutableMap()
        map[serverId] = ServerRuntimeStatus(
            serverId = serverId,
            state = state,
            uptimeMs = uptime,
            memoryMaxMb = memoryMaxMb.toLong(),
            lastError = error,
            pid = processes[serverId]?.pid()
        )
        _status.value = map
    }
}
