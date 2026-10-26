package com.nexus.launcher.server

import com.nexus.launcher.jre.JavaRuntimeManager
import com.nexus.launcher.network.DownloadQueueManager
import com.nexus.launcher.server.model.ServerConfig
import com.nexus.launcher.server.model.ServerSoftware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NeoForge server install via maven.neoforged.net installer jar.
 */
@Singleton
class NeoForgeServerInstaller @Inject constructor(
    private val downloadQueueManager: DownloadQueueManager,
    private val httpClient: OkHttpClient,
    private val javaRuntimeManager: JavaRuntimeManager,
    private val javaResolver: ServerJavaResolver
) {
    data class InstallResult(val success: Boolean, val message: String, val version: String? = null)

    suspend fun install(config: ServerConfig): InstallResult = withContext(Dispatchers.IO) {
        if (config.software != ServerSoftware.NEOFORGE) {
            return@withContext InstallResult(false, "Not NeoForge")
        }

        val serverDir = File(config.serverDir).also { it.mkdirs() }
        File(serverDir, "mods").mkdirs()

        try {
            val neoVer = resolveLatestNeoForge(config.minecraftVersion)
                ?: return@withContext InstallResult(
                    false,
                    "No NeoForge for ${config.minecraftVersion}"
                )

            // NeoForge installer coordinates vary; common pattern:
            // https://maven.neoforged.net/releases/net/neoforged/neoforge/{ver}/neoforge-{ver}-installer.jar
            val installerName = "neoforge-$neoVer-installer.jar"
            val url =
                "https://maven.neoforged.net/releases/net/neoforged/neoforge/$neoVer/$installerName"

            val installerJar = File(serverDir, installerName)
            downloadQueueManager.downloadFile(url, installerJar)

            if (!installerJar.exists() || installerJar.length() == 0L) {
                return@withContext InstallResult(false, "NeoForge installer empty")
            }

            val javaMajor = javaResolver.requiredJava(config.minecraftVersion).major
            val jreDir = javaRuntimeManager.getJrePath(javaMajor, autoInstall = true)
            val javaBin = File(jreDir, "bin/java").absolutePath

            val process = ProcessBuilder(
                javaBin, "-jar", installerJar.absolutePath,
                "--installServer", serverDir.absolutePath
            ).directory(serverDir).redirectErrorStream(true).start()

            val log = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            if (code != 0) {
                return@withContext InstallResult(false, "NeoForge installer exit $code: ${log.takeLast(200)}")
            }

            // Prefer any produced jar as server.jar
            serverDir.listFiles()
                ?.filter { it.name.endsWith(".jar") && it.name.contains("neoforge", true) }
                ?.maxByOrNull { it.length() }
                ?.copyTo(File(serverDir, "server.jar"), overwrite = true)

            if (config.eulaAccepted) {
                File(serverDir, "eula.txt").writeText("# Nexus\neula=true\n")
            }
            val props = File(serverDir, "server.properties")
            if (!props.exists()) ServerPropertiesWriter.writeDefaults(props, config)

            InstallResult(true, "NeoForge $neoVer installed", neoVer)
        } catch (e: Exception) {
            InstallResult(false, e.message ?: "NeoForge install failed")
        }
    }

    private fun resolveLatestNeoForge(mcVersion: String): String? {
        // Use promotions index when available
        val url = "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge"
        return try {
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    // Fallback: try common beta-style version from metadata XML
                    return resolveFromMetadata(mcVersion)
                }
                val body = resp.body?.string() ?: return resolveFromMetadata(mcVersion)
                // versions often start with MC minor e.g. 21.1.x for 1.21.1
                val versions = Regex("\"([0-9.]+)\"").findAll(body).map { it.groupValues[1] }.toList()
                val mcMinor = mcVersion.removePrefix("1.")
                versions.lastOrNull { it.startsWith(mcMinor) || it.startsWith(mcVersion) }
                    ?: versions.lastOrNull()
            }
        } catch (e: Exception) {
            Timber.e(e)
            resolveFromMetadata(mcVersion)
        }
    }

    private fun resolveFromMetadata(mcVersion: String): String? {
        val meta =
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
        return try {
            httpClient.newCall(Request.Builder().url(meta).build()).execute().use { resp ->
                val xml = resp.body?.string() ?: return null
                val all = Regex("<version>([^<]+)</version>").findAll(xml).map { it.groupValues[1] }.toList()
                val mcMinor = mcVersion.removePrefix("1.")
                all.lastOrNull { it.startsWith(mcMinor) } ?: all.lastOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }
}
