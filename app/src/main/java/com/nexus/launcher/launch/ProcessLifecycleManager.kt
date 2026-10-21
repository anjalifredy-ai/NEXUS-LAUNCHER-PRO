package com.nexus.launcher.launch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessLifecycleManager @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun monitor(process: Process, onExit: (Int) -> Unit) {
        scope.launch {
            val exitCode = process.waitFor()
            onExit(exitCode)
        }
    }
}
