package com.nexus.launcher.voice

import com.nexus.launcher.server.model.ServerSoftware

/**
 * Compatibility matrix for Simple Voice Chat (preferred protocol).
 * Official project: Modrinth `simple-voice-chat`
 *
 * Client = Fabric/Forge/NeoForge/Quilt mod in profile mods folder.
 * Server = matching server mod/plugin for that software.
 *
 * Unsupported combos must show a clear message — never silent install wrong jar.
 */
object VoiceChatCompatibility {

    const val MODRINTH_PROJECT = "simple-voice-chat"
    const val DISPLAY_NAME = "Nexus Voice Chat (Simple Voice Chat)"

    enum class SupportLevel {
        FULL,           // client + server path known
        CLIENT_ONLY,    // can install client mod; server component unclear
        UNSUPPORTED
    }

    data class ClientSupport(
        val level: SupportLevel,
        val loaderLabel: String,
        val reason: String? = null
    )

    data class ServerSupport(
        val level: SupportLevel,
        val componentLabel: String,
        val reason: String? = null
    )

    /** Loaders where Simple Voice Chat publishes client mods. */
    fun clientSupport(loader: String, minecraftVersion: String): ClientSupport {
        val l = loader.lowercase()
        return when {
            l in listOf("fabric", "quilt") -> ClientSupport(
                SupportLevel.FULL,
                l.replaceFirstChar { it.uppercase() },
                null
            )
            l in listOf("forge", "neoforge") -> ClientSupport(
                SupportLevel.FULL,
                l.replaceFirstChar { it.uppercase() },
                null
            )
            l == "vanilla" -> ClientSupport(
                SupportLevel.UNSUPPORTED,
                "Vanilla",
                "Voice chat requires Fabric, Quilt, Forge, or NeoForge — not pure Vanilla."
            )
            else -> ClientSupport(
                SupportLevel.UNSUPPORTED,
                loader,
                "Loader $loader is not supported for Simple Voice Chat client."
            )
        }
    }

    fun serverSupport(software: ServerSoftware, minecraftVersion: String): ServerSupport {
        return when (software) {
            ServerSoftware.FABRIC, ServerSoftware.QUILT -> ServerSupport(
                SupportLevel.FULL,
                "Simple Voice Chat server mod",
                null
            )
            ServerSoftware.FORGE, ServerSoftware.NEOFORGE -> ServerSupport(
                SupportLevel.FULL,
                "Simple Voice Chat server mod",
                null
            )
            ServerSoftware.PAPER, ServerSoftware.PURPUR, ServerSoftware.SPIGOT -> ServerSupport(
                SupportLevel.UNSUPPORTED,
                "—",
                "Simple Voice Chat is a mod, not a Bukkit plugin. Use Fabric/Quilt/Forge server for voice, " +
                    "or install a separate plugin-based voice solution manually."
            )
            ServerSoftware.VANILLA -> ServerSupport(
                SupportLevel.UNSUPPORTED,
                "—",
                "Vanilla dedicated server has no mod loader — voice-chat server component cannot load."
            )
        }
    }

    fun noteAboutPublicServers(): String =
        "Public servers (Hypixel, etc.) only work if they run the same voice-chat protocol. " +
            "Most large networks do not. Voice works on servers that installed Simple Voice Chat."

    fun noteAboutAndroid(): String =
        "On Android, microphone needs RECORD_AUDIO permission. Simple Voice Chat runs inside " +
            "the Java game process; actual capture quality depends on the launcher audio bridge. " +
            "OTG keyboard PTT is supported by the mod; on-screen PTT is limited unless the mod exposes it."
}
