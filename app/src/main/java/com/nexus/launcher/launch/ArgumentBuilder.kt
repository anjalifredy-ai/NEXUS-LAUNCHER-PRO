package com.nexus.launcher.launch

import com.nexus.launcher.data.model.Account
import com.nexus.launcher.data.model.ElyByAccount
import com.nexus.launcher.data.model.MicrosoftAccount
import com.nexus.launcher.data.model.OfflineAccount
import com.nexus.launcher.data.model.VersionJson
import com.nexus.launcher.libraries.RuleEvaluator
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds full JVM + game argument list for Minecraft client launch.
 * Supports modern `arguments` and legacy `minecraftArguments`.
 * Optional local Nexus skin path via system property for client-side readers.
 */
@Singleton
class ArgumentBuilder @Inject constructor(
    private val ruleEvaluator: RuleEvaluator
) {
    fun buildArguments(
        versionJson: VersionJson,
        account: Account,
        mcDir: String,
        nativesDir: String,
        classpath: String,
        javaPath: String,
        memoryMb: Int,
        jvmArgs: String,
        renderer: String,
        width: Int,
        height: Int,
        localSkinPath: String? = null
    ): List<String> {
        val args = mutableListOf<String>()

        args.add(javaPath)
        args.add("-Xms${(memoryMb / 2).coerceAtLeast(256)}m")
        args.add("-Xmx${memoryMb}m")
        args.add("-XX:+UseG1GC")
        args.add("-XX:-UseAdaptiveSizePolicy")

        if (jvmArgs.isNotBlank()) {
            args.addAll(jvmArgs.split(" ").filter { it.isNotBlank() })
        }

        var classpathAdded = false
        versionJson.arguments?.jvm?.forEach { jvmArg ->
            if (ruleEvaluator.evaluateRules(jvmArg.rules)) {
                jvmArg.value.forEach { value ->
                    val processed = processPlaceholder(
                        value, versionJson, account, mcDir, nativesDir, classpath, width, height
                    )
                    if (processed == "-cp" || processed == "-classpath") {
                        args.add(processed)
                        args.add(classpath)
                        classpathAdded = true
                    } else if (processed.contains("\${classpath}") || processed == classpath) {
                        args.add(classpath)
                        classpathAdded = true
                    } else {
                        args.add(processed)
                    }
                }
            }
        }

        if (!classpathAdded) {
            args.add("-cp")
            args.add(classpath)
        }

        args.add("-Djava.library.path=$nativesDir")
        args.add("-Djna.boot.library.path=$nativesDir")
        args.add("-Dorg.lwjgl.system.sharedLibraryExtractPath=$nativesDir")
        args.add("-Dorg.lwjgl.opengl.libname=libGL.so")

        // Local cosmetics hint — only if file exists (offline / custom clients may read this)
        val skin = localSkinPath?.takeIf { File(it).exists() }
            ?: File(mcDir, "profiles/default/skin.png").takeIf { it.exists() }?.absolutePath
            ?: File(mcDir, "skin.png").takeIf { it.exists() }?.absolutePath
        if (skin != null) {
            args.add("-Dnexus.cosmetics.skin=$skin")
            args.add("-Dnexus.localSkin=$skin")
        }

        when (renderer.lowercase()) {
            "ltw", "egl" -> args.add("-Dorg.lwjgl.opengl.egl=true")
            "vulkan" -> args.add("-Dorg.lwjgl.vulkan=true")
            "gles" -> { }
            else -> { }
        }

        args.add(versionJson.mainClass)

        val legacy = versionJson.legacyArguments
        if (!legacy.isNullOrBlank() && versionJson.arguments == null) {
            legacy.split(" ").filter { it.isNotBlank() }.forEach { token ->
                args.add(
                    processPlaceholder(
                        token, versionJson, account, mcDir, nativesDir, classpath, width, height
                    )
                )
            }
        } else {
            versionJson.arguments?.game?.forEach { gameArg ->
                if (ruleEvaluator.evaluateRules(gameArg.rules)) {
                    gameArg.value.forEach { value ->
                        args.add(
                            processPlaceholder(
                                value, versionJson, account, mcDir, nativesDir, classpath, width, height
                            )
                        )
                    }
                }
            }
        }

        ensureArg(args, "--username", account.username)
        ensureArg(args, "--uuid", account.uuid)
        ensureArg(args, "--accessToken", account.accessToken)

        when (account) {
            is MicrosoftAccount -> {
                ensureArg(args, "--userType", "msa")
                if (!account.xuid.isNullOrBlank()) ensureArg(args, "--xuid", account.xuid!!)
            }
            is ElyByAccount -> ensureArg(args, "--userType", "elyby")
            is OfflineAccount -> ensureArg(args, "--userType", "legacy")
        }

        ensureArg(args, "--version", versionJson.id)
        ensureArg(args, "--gameDir", mcDir)
        ensureArg(args, "--assetsDir", "$mcDir/assets")
        ensureArg(args, "--assetIndex", versionJson.assetIndex?.id ?: versionJson.assets)
        ensureArg(args, "--versionType", versionJson.type)
        ensureArg(args, "--width", width.toString())
        ensureArg(args, "--height", height.toString())

        return args
    }

    private fun ensureArg(args: MutableList<String>, key: String, value: String) {
        val idx = args.indexOf(key)
        if (idx >= 0 && idx + 1 < args.size) {
            args[idx + 1] = value
        } else {
            args.add(key)
            args.add(value)
        }
    }

    private fun processPlaceholder(
        value: String,
        versionJson: VersionJson,
        account: Account,
        mcDir: String,
        nativesDir: String,
        classpath: String,
        width: Int,
        height: Int
    ): String {
        return value
            .replace("\${auth_player_name}", account.username)
            .replace("\${auth_uuid}", account.uuid)
            .replace("\${auth_access_token}", account.accessToken)
            .replace("\${auth_session}", "token:${account.accessToken}:${account.uuid}")
            .replace("\${user_type}", "msa")
            .replace("\${version_name}", versionJson.id)
            .replace("\${game_directory}", mcDir)
            .replace("\${game_assets}", "$mcDir/assets")
            .replace("\${assets_root}", "$mcDir/assets")
            .replace("\${assets_index_name}", versionJson.assetIndex?.id ?: versionJson.assets)
            .replace("\${version_type}", versionJson.type)
            .replace("\${natives_directory}", nativesDir)
            .replace("\${launcher_name}", "NexusLauncher")
            .replace("\${launcher_version}", "1.0.0")
            .replace("\${classpath}", classpath)
            .replace("\${user_properties}", "{}")
            .replace("\${resolution_width}", width.toString())
            .replace("\${resolution_height}", height.toString())
            .replace("\${library_directory}", "$mcDir/libraries")
            .replace("\${classpath_separator}", ":")
    }
}
