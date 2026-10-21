package com.nexus.launcher.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogCollector @Inject constructor(
    private val logPersistence: LogPersistence
) {
    private val _recentLines = MutableStateFlow<List<String>>(emptyList())
    val recentLines: StateFlow<List<String>> = _recentLines.asStateFlow()

    private val lines = mutableListOf<String>()
    private val maxLines = 1000

    fun collect(line: String) {
        synchronized(lines) {
            lines.add(line)
            if (lines.size > maxLines) {
                lines.removeAt(0)
            }
            _recentLines.value = lines.toList()
            logPersistence.appendLine(line)
        }
    }

    fun clear() {
        synchronized(lines) {
            lines.clear()
            _recentLines.value = emptyList()
        }
    }

    fun getFullLog(): String = synchronized(lines) {
        lines.joinToString("\n")
    }
}

@Singleton
class LogPersistence @Inject constructor() {
    private val logDir = File("/sdcard/NexusLauncher/.minecraft/logs")

    init {
        logDir.mkdirs()
    }

    fun appendLine(line: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val logFile = File(logDir, "$today.log")
        logFile.appendText(line + "\n")
    }

    fun saveCrashReport(fullReport: String) {
        val crashDir = File("/sdcard/NexusLauncher/.minecraft/crash-reports")
        crashDir.mkdirs()
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.US).format(Date())
        val crashFile = File(crashDir, "crash-$timestamp.txt")
        crashFile.writeText(fullReport)
    }
}
