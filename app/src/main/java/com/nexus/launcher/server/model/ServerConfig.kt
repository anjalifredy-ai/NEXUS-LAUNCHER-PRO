package com.nexus.launcher.server.model

/**
 * Persistent configuration for one on-device Minecraft Java server.
 * World and files stay local on the phone by default.
 */
data class ServerConfig(
    val id: String,
    val name: String,
    val description: String = "",
    val motd: String = "A Nexus Minecraft Server",
    val iconPath: String? = null,
    val minecraftVersion: String,
    val software: ServerSoftware,
    val softwareVersion: String? = null,
    val serverDir: String,
    val worldDir: String = "world",
    val port: Int = 25565,
    val bindAddress: String = "0.0.0.0",
    val maxPlayers: Int = 10,
    val memoryMb: Int = 1024,
    val onlineMode: Boolean = true,
    val whitelist: Boolean = false,
    val difficulty: String = "normal",
    val gamemode: String = "survival",
    val viewDistance: Int = 8,
    val simulationDistance: Int = 6,
    val pvp: Boolean = true,
    val hardcore: Boolean = false,
    val allowFlight: Boolean = false,
    val enableCommandBlocks: Boolean = false,
    val spawnProtection: Int = 16,
    val eulaAccepted: Boolean = false,
    val jvmArgs: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ServerSoftware(val displayName: String, val supportsPlugins: Boolean, val supportsMods: Boolean) {
    VANILLA("Vanilla", supportsPlugins = false, supportsMods = false),
    FABRIC("Fabric", supportsPlugins = false, supportsMods = true),
    PAPER("Paper", supportsPlugins = true, supportsMods = false),
    PURPUR("Purpur", supportsPlugins = true, supportsMods = false),
    SPIGOT("Spigot", supportsPlugins = true, supportsMods = false),
    FORGE("Forge", supportsPlugins = false, supportsMods = true),
    NEOFORGE("NeoForge", supportsPlugins = false, supportsMods = true),
    QUILT("Quilt", supportsPlugins = false, supportsMods = true)
}

enum class ServerState {
    STOPPED,
    INSTALLING,
    STARTING,
    ONLINE,
    STOPPING,
    FAILED
}

data class ServerRuntimeStatus(
    val serverId: String,
    val state: ServerState,
    val uptimeMs: Long = 0L,
    val playersOnline: Int = 0,
    val playersMax: Int = 0,
    val memoryUsedMb: Long = 0L,
    val memoryMaxMb: Long = 0L,
    val lastError: String? = null,
    val pid: Long? = null
)
