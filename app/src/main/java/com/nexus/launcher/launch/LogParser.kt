package com.nexus.launcher.launch

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogParser @Inject constructor() {

    enum class LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL
    }

    data class LogLine(
        val level: LogLevel,
        val timestamp: String?,
        val source: String?,
        val message: String
    )

    fun parseLine(line: String): LogLine {
        val regex = Regex("""\[(\d{2}:\d{2}:\d{2})\] \[([^/]+)/([^\]]+)\]:?\s*(.*)""")
        val match = regex.find(line)

        return if (match != null) {
            val (time, thread, level, message) = match.destructured
            LogLine(
                level = parseLogLevel(level.split("/").last()),
                timestamp = time,
                source = thread,
                message = message
            )
        } else {
            LogLine(
                level = LogLevel.INFO,
                timestamp = null,
                source = null,
                message = line
            )
        }
    }

    private fun parseLogLevel(levelStr: String): LogLevel {
        return when (levelStr.uppercase()) {
            "TRACE" -> LogLevel.TRACE
            "DEBUG" -> LogLevel.DEBUG
            "INFO" -> LogLevel.INFO
            "WARN", "WARNING" -> LogLevel.WARN
            "ERROR", "SEVERE" -> LogLevel.ERROR
            "FATAL" -> LogLevel.FATAL
            else -> LogLevel.INFO
        }
    }
}
