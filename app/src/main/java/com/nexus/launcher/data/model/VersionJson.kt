package com.nexus.launcher.data.model

data class VersionJson(
    val id: String,
    val inheritsFrom: String? = null,
    val mainClass: String,
    val assets: String,
    val assetIndex: AssetIndexInfo? = null,
    val downloads: VersionDownloads? = null,
    val libraries: List<Library> = emptyList(),
    val arguments: Arguments? = null,
    val legacyArguments: String? = null,
    val logging: LoggingInfo? = null,
    val javaVersion: JavaVersionInfo? = null,
    val type: String = "release",
    val time: String = "",
    val releaseTime: String = "",
    val minimumLauncherVersion: Int = 0
)

data class AssetIndexInfo(
    val id: String,
    val sha1: String,
    val size: Long,
    val totalSize: Long,
    val url: String
)

data class VersionDownloads(
    val client: DownloadArtifact? = null,
    val server: DownloadArtifact? = null,
    val client_mappings: DownloadArtifact? = null,
    val server_mappings: DownloadArtifact? = null
)

data class DownloadArtifact(
    val sha1: String,
    val size: Long,
    val url: String
)

data class Library(
    val name: String,
    val artifact: LibraryArtifact? = null,
    val classifiers: Map<String, LibraryArtifact> = emptyMap(),
    val natives: LibraryNatives? = null,
    val rules: List<LibraryRule>? = null,
    val exclude: List<String> = emptyList()
)

data class LibraryArtifact(
    val path: String,
    val sha1: String,
    val size: Long,
    val url: String
)

data class LibraryNatives(
    val linux: String? = null,
    val linuxArm64: String? = null,
    val windows: String? = null,
    val osx: String? = null
)

data class LibraryRule(
    val action: String,
    val osName: String? = null,
    val features: Map<String, Boolean>? = null
)

data class Arguments(
    val game: List<GameArgument> = emptyList(),
    val jvm: List<JvmArgument> = emptyList()
)

data class GameArgument(
    val value: List<String>,
    val rules: List<LibraryRule>? = null
)

data class JvmArgument(
    val value: List<String>,
    val rules: List<LibraryRule>? = null
)

data class LoggingInfo(
    val argument: String,
    val file: String,
    val type: String
)

data class JavaVersionInfo(
    val component: String,
    val majorVersion: Int
)
