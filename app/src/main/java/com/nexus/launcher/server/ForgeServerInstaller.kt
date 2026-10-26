package com.nexus.launcher.server

import com.nexus.launcher.network.DownloadQueueManager
import com.nexus.launcher.server.model.ServerConfig
import com.nexus.launcher.server.model.ServerSoftware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs Forge dedicated server using the official installer jar from files.minecraftforge.net metadata.
 *
 * Flow:
 * 1. Resolve recommended/latest Forge build for MC version from Maven metadata
 * 2. Download forge-{mc}-{forge}-installer.jar
 * 3. Run installer with --installServer (needs JRE already available)
 * 4. Result: run.bat/run.sh + libraries; we also symlink/copy a server.jar entry point when possible
 */
@Singleton
class ForgeServerInstaller @Inject constructor(
    private val downloadQueueManager: DownloadQueueManager,
    private val httpClient: OkHttpClient,
    private val javaRuntimeManager: com.nexus.launcher.jre.JavaRuntimeManager,
    private val javaResolver: ServerJavaResolver
) {
    data class InstallResult(
        val success: Boolean,
        val message: String,
        val forgeVersion: String? = null
    )

    suspend fun install(config: ServerConfig): InstallResult = withContext(Dispatchers.IO) {
        if (config.software != ServerSoftware.FORGE) {
            return@withContext InstallResult(false, "Not a Forge server")
        }

        val serverDir = File(config.serverDir).also { it.mkdirs() }
        File(serverDir, "mods").mkdirs()

        try {
            val forgeVer = resolveLatestForge(config.minecraftVersion)
                ?: return@withContext InstallResult(
                    false,
                    "No Forge build found for ${config.minecraftVersion}"
                )

            val installerName = "forge-${config.minecraftVersion}-$forgeVer-installer.jar"
            val installerUrl =
                "https://maven.minecraftforge.net/net/minecraftforge/forge/" +
                    "${config.minecraftVersion}-$forgeVer/$installerName"

            val installerJar = File(serverDir, installerName)
            downloadQueueManager.downloadFile(installerUrl, installerJar)

            if (!installerJar.exists() || installerJar.length() == 0L) {
                return@withContext InstallResult(false, "Forge installer download empty")
            }

            // Run installer --installServer
            val javaMajor = javaResolver.requiredJava(config.minecraftVersion).major
            val jreDir = javaRuntimeManager.getJrePath(javaMajor, autoInstall = true)
            val javaBin = File(jreDir, "bin/java").absolutePath

            val pb = ProcessBuilder(
                javaBin,
                "-jar",
                installerJar.absolutePath,
                "--installServer",
                serverDir.absolutePath
            ).directory(serverDir).redirectErrorStream(true)

            val process = pb.start()
            val log = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            Timber.d("Forge installer exit=$code log=${log.take(500)}")

            if (code != 0) {
                return@withContext InstallResult(
                    false,
                    "Forge installer failed (exit $code). ${log.lines().lastOrNull() ?: ""}"
                )
            }

            // Ensure a runnable server.jar or use run script args; copy unix_args if present
            ensureServerEntrypoint(serverDir, config.minecraftVersion, forgeVer)

            if (config.eulaAccepted) {
                File(serverDir, "eula.txt").writeText("# Nexus\neula=true\n")
            }
            val props = File(serverDir, "server.properties")
            if (!props.exists()) ServerPropertiesWriter.writeDefaults(props, config)

            InstallResult(true, "Forge $forgeVer installed", forgeVer)
        } catch (e: Exception) {
            InstallResult(false, e.message ?: "Forge install failed")
        }
    }

    private fun resolveLatestForge(mcVersion: String): String? {
        // Maven metadata
        val metaUrl =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml"
        val req = Request.Builder().url(metaUrl).build()
        return try {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val xml = resp.body?.string() ?: return null
                // versions look like 1.20.1-47.2.0
                val regex = Regex("<version>$mcVersion-([^<]+)</version>")
                val matches = regex.findAll(xml).map { it.groupValues[1] }.toList()
                matches.lastOrNull()
            }
        } catch (e: Exception) {
            Timber.e(e, "Forge metadata")
            null
        }
    }

    private fun ensureServerEntrypoint(serverDir: File, mc: String, forge: String) {
        // Modern Forge uses run.sh + unix_args.txt; also may place forge-…-shim.jar
        val candidates = listOf(
            File(serverDir, "forge-$mc-$forge-shim.jar"),
            File(serverDir, "forge-$mc-$forge-universal.jar"),
            File(serverDir, "server.jar")
        )
        val found = candidates.firstOrNull { it.exists() }
        if (found != null && found.name != "server.jar") {
            found.copyTo(File(serverDir, "server.jar"), overwrite = true)
        }
        // Marker for process manager: if only args file exists, write a note file
        val args = File(serverDir, "user_jvm_args.txt")
        if (!args.exists()) args.writeText("# Nexus Launcher\n")
    }
}
