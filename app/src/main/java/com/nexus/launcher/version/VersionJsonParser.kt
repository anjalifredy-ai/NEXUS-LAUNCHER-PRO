package com.nexus.launcher.version

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.nexus.launcher.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VersionJsonParser @Inject constructor(
    private val gson: Gson
) {
    fun parse(rawJson: String, versionId: String): VersionJson {
        val root = JsonParser.parseString(rawJson).asJsonObject

        val id = root.get("id")?.asString ?: versionId
        val inheritsFrom = root.get("inheritsFrom")?.asString
        val mainClass = root.get("mainClass")?.asString ?: "net.minecraft.client.main.Main"
        val assets = root.get("assets")?.asString ?: id
        val type = root.get("type")?.asString ?: "release"
        val time = root.get("time")?.asString ?: ""
        val releaseTime = root.get("releaseTime")?.asString ?: ""
        val minimumLauncherVersion = root.get("minimumLauncherVersion")?.asInt ?: 0

        val assetIndex = root.getAsJsonObject("assetIndex")?.let {
            AssetIndexInfo(
                id = it.get("id").asString,
                sha1 = it.get("sha1").asString,
                size = it.get("size").asLong,
                totalSize = it.get("totalSize")?.asLong ?: 0L,
                url = it.get("url").asString
            )
        }

        val downloads = root.getAsJsonObject("downloads")?.let { dl ->
            VersionDownloads(
                client = dl.getAsJsonObject("client")?.let { parseArtifact(it) },
                server = dl.getAsJsonObject("server")?.let { parseArtifact(it) }
            )
        }

        val libraries = mutableListOf<Library>()
        root.getAsJsonArray("libraries")?.forEach { elem ->
            val libObj = elem.asJsonObject
            val name = libObj.get("name").asString
            val downloadsObj = libObj.getAsJsonObject("downloads")

            val artifact = downloadsObj?.getAsJsonObject("artifact")?.let { parseLibraryArtifact(it) }

            val classifiers = mutableMapOf<String, LibraryArtifact>()
            downloadsObj?.getAsJsonObject("classifiers")?.entrySet()?.forEach { (key, value) ->
                classifiers[key] = parseLibraryArtifact(value.asJsonObject)
            }

            val natives = libObj.getAsJsonObject("natives")?.let {
                LibraryNatives(
                    linux = it.get("linux")?.asString,
                    windows = it.get("windows")?.asString,
                    osx = it.get("osx")?.asString
                )
            }

            val rules = mutableListOf<LibraryRule>()
            libObj.getAsJsonArray("rules")?.forEach { ruleElem ->
                val ruleObj = ruleElem.asJsonObject
                rules.add(
                    LibraryRule(
                        action = ruleObj.get("action").asString,
                        osName = ruleObj.getAsJsonObject("os")?.get("name")?.asString
                    )
                )
            }

            libraries.add(
                Library(
                    name = name,
                    artifact = artifact,
                    classifiers = classifiers,
                    natives = natives,
                    rules = if (rules.isEmpty()) null else rules
                )
            )
        }

        val javaVersion = root.getAsJsonObject("javaVersion")?.let {
            JavaVersionInfo(
                component = it.get("component").asString,
                majorVersion = it.get("majorVersion").asInt
            )
        }

        return VersionJson(
            id = id,
            inheritsFrom = inheritsFrom,
            mainClass = mainClass,
            assets = assets,
            assetIndex = assetIndex,
            downloads = downloads,
            libraries = libraries,
            arguments = null,
            legacyArguments = root.get("minecraftArguments")?.asString,
            logging = null,
            javaVersion = javaVersion,
            type = type,
            time = time,
            releaseTime = releaseTime,
            minimumLauncherVersion = minimumLauncherVersion
        )
    }

    private fun parseArtifact(obj: com.google.gson.JsonObject): DownloadArtifact {
        return DownloadArtifact(
            sha1 = obj.get("sha1").asString,
            size = obj.get("size").asLong,
            url = obj.get("url").asString
        )
    }

    private fun parseLibraryArtifact(obj: com.google.gson.JsonObject): LibraryArtifact {
        return LibraryArtifact(
            path = obj.get("path").asString,
            sha1 = obj.get("sha1").asString,
            size = obj.get("size").asLong,
            url = obj.get("url").asString
        )
    }
}
