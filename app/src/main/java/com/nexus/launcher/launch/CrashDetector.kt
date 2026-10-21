package com.nexus.launcher.launch

import com.nexus.launcher.logging.LogPersistence
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashDetector @Inject constructor(
    private val logPersistence: LogPersistence
) {
    private val _crashEvents = MutableSharedFlow<CrashEvent>(extraBufferCapacity = 10)
    val crashEvents: SharedFlow<CrashEvent> = _crashEvents.asSharedFlow()

    fun checkForCrash(currentLine: String, recentLines: List<String>) {
        val crashIndicators = listOf(
            "Process crashed with exit code",
            "Minecraft has crashed!",
            "Shutting down",
            "Failed to start game",
            "java.lang.OutOfMemoryError",
            "UnsatisfiedLinkError"
        )

        if (crashIndicators.any { currentLine.contains(it) }) {
            val fullReport = recentLines.joinToString("\n")
            logPersistence.saveCrashReport(fullReport)

            _crashEvents.tryEmit(
                CrashEvent(
                    fullReport = fullReport,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }
}

data class CrashEvent(
    val fullReport: String,
    val timestamp: Long
)
