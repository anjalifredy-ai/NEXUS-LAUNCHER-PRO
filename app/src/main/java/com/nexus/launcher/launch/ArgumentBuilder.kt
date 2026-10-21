package com.nexus.launcher.launch

import com.nexus.launcher.data.model.Account
import com.nexus.launcher.data.model.ElyByAccount
import com.nexus.launcher.data.model.MicrosoftAccount
import com.nexus.launcher.data.model.OfflineAccount
import com.nexus.launcher.data.model.VersionJson
import com.nexus.launcher.libraries.RuleEvaluator
import javax.inject.Inject
import javax.inject.Singleton

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
        height: Int
    ): List<String> {
        val args = mutableListOf<String>()

        args.add(javaPath)
        args.add("-Xms${memoryMb}m")
        args.add("-Xmx${memoryMb}m")

        if (jvmArgs.isNotBlank()) {
            args.addAll(jvmArgs.split(" ").filter { it.isNotBlank() })
        }

        versionJson.arguments?.jvm?.forEach { jvmArg ->
            if (ruleEvaluator.evaluateRules(jvmArg.rules)) {
                jvmArg.value.forEach { value ->
                    args.add(processPlaceholder(value, versionJson, account, mcDir, nativesDir, classpath, width, height))
                }
            }
        }

        args.add("-Dorg.lwjgl.system.sharedLibs=$nativesDir")
        args.add("-Dorg.lwjgl.opengl.libgl4es.path=$nativesDir/libGL.so")
        args.add("-Dorg.lwjgl.openal.libpath=$nativesDir/libopenal.so")

        when (renderer) {
            "ltw" -> args.add("-Dorg.lwjgl.opengl.egl=true")
            "vulkan" -> args.add("-Dorg.lwjgl.opengl.renderer=vulkan")
            "gles" -> args.add("-Dorg.lwjgl.opengl.renderer=gles")
            else -> args.add("-Dorg.lwjgl.opengl.renderer=gl4es")
        }

        args.add(versionJson.mainClass)

        versionJson.arguments?.game?.forEach { gameArg ->
            if (ruleEvaluator.evaluateRules(gameArg.rules)) {
                gameArg.value.forEach { value ->
                    args.add(processPlaceholder(value, versionJson, account, mcDir, nativesDir, classpath, width, height))
                }
            }
        }

        args.add("--username")
        args.add(account.username)
        args.add("--uuid")
        args.add(account.uuid)
        args.add("--accessToken")
        args.add(account.accessToken)

        when (account) {
            is MicrosoftAccount -> {
                args.add("--userType")
                args.add("msa")
                args.add("--xuid")
                args.add(account.xuid ?: "")
            }
            is ElyByAccount -> {
                args.add("--userType")
                args.add("elyby")
            }
            is OfflineAccount -> {
                args.add("--userType")
                args.add("offline")
            }
        }

        args.add("--version")
        args.add(versionJson.id)
        args.add("--gameDir")
        args.add(mcDir)
        args.add("--assetsDir")
        args.add("$mcDir/assets")
        args.add("--assetIndex")
        args.add(versionJson.assets)
        args.add("--versionType")
        args.add(versionJson.type)
        args.add("--width")
        args.add(width.toString())
        args.add("--height")
        args.add(height.toString())

        return args
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
            .replace("${auth_player_name}", account.username)
            .replace("${auth_uuid}", account.uuid)
            .replace("${auth_access_token}", account.accessToken)
            .replace("${auth_session}", "token:${account.accessToken}:${account.uuid}")
            .replace("${user_type}", "msa")
            .replace("${version_name}", versionJson.id)
            .replace("${game_directory}", mcDir)
            .replace("${game_assets}", "$mcDir/assets")
            .replace("${assets_root}", "$mcDir/assets")
            .replace("${assets_index_name}", versionJson.assets)
            .replace("${version_type}", versionJson.type)
            .replace("${natives_directory}", nativesDir)
            .replace("${classpath}", classpath)
            .replace("${user_properties}", "{}")
            .replace("${resolution_width}", width.toString())
            .replace("${resolution_height}", height.toString())
            .replace("${library_directory}", "$mcDir/libraries")
    }
}
