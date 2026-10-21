package com.nexus.launcher.launch

import com.nexus.launcher.logging.LogCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessOutputHandler @Inject constructor(
    private val logCollector: LogCollector,
    private val logParser: LogParser,
    private val crashDetector: CrashDetector
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var outputJob: Job? = null

    private val _logLines = MutableStateFlow<List<String>>(emptyList())
    val logLines: StateFlow<List<String>> = _logLines.asStateFlow()

    fun start(process: Process) {
        outputJob?.cancel()
        _logLines.value = emptyList()

        outputJob = scope.launch {
            val reader = process.inputStream.bufferedReader()
            val lines = mutableListOf<String>()

            try {
                var line = reader.readLine()
                while (line != null) {
                    lines.add(line)
                    if (lines.size > 500) {
                        lines.removeAt(0)
                    }
                    _logLines.value = lines.toList()

                    logCollector.collect(line)

                    val parsed = logParser.parseLine(line)
                    if (parsed.level == LogParser.LogLevel.ERROR) {
                        crashDetector.checkForCrash(line, lines)
                    }

                    Timber.tag("Minecraft").d(line)
                    line = reader.readLine()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error reading process output")
            }
        }
    }

    fun stop() {
        outputJob?.cancel()
    }
}
