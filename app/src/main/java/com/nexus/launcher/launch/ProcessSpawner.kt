package com.nexus.launcher.launch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessSpawner @Inject constructor() {
    suspend fun spawnProcess(args: List<String>): Process = withContext(Dispatchers.IO) {
        val env = System.getenv().toMutableMap()
        env["LD_LIBRARY_PATH"] = "/sdcard/NexusLauncher/.minecraft/natives:" + (env["LD_LIBRARY_PATH"] ?: "")
        env["LIBGL_ES"] = "1"
        env["LIBGL_FB"] = "1"

        val processBuilder = ProcessBuilder(args).apply {
            redirectErrorStream(true)
            environment().clear()
            environment().putAll(env)
            directory(File("/sdcard/NexusLauncher/.minecraft"))
        }

        processBuilder.start()
    }
}
