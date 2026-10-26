package com.nexus.launcher.server

import com.nexus.launcher.server.model.ServerConfig
import java.io.File
import java.util.Properties

/**
 * Reads/writes real server.properties — all standard Vanilla/Paper keys.
 */
object ServerPropertiesWriter {

    fun writeDefaults(file: File, config: ServerConfig) {
        val text = buildString {
            appendLine("# Nexus Launcher generated server.properties")
            appendLine("motd=${config.motd}")
            appendLine("server-port=${config.port}")
            appendLine("server-ip=")
            appendLine("max-players=${config.maxPlayers}")
            appendLine("online-mode=${config.onlineMode}")
            appendLine("white-list=${config.whitelist}")
            appendLine("enforce-whitelist=${config.whitelist}")
            appendLine("difficulty=${config.difficulty}")
            appendLine("gamemode=${config.gamemode}")
            appendLine("view-distance=${config.viewDistance}")
            appendLine("simulation-distance=${config.simulationDistance}")
            appendLine("pvp=${config.pvp}")
            appendLine("hardcore=${config.hardcore}")
            appendLine("allow-flight=${config.allowFlight}")
            appendLine("enable-command-block=${config.enableCommandBlocks}")
            appendLine("spawn-protection=${config.spawnProtection}")
            appendLine("level-name=${config.worldDir}")
            appendLine("spawn-monsters=true")
            appendLine("spawn-animals=true")
            appendLine("spawn-npcs=true")
            appendLine("allow-nether=true")
            appendLine("enable-status=true")
            appendLine("force-gamemode=false")
            appendLine("generate-structures=true")
            appendLine("max-world-size=29999984")
            appendLine("network-compression-threshold=256")
            appendLine("op-permission-level=4")
            appendLine("player-idle-timeout=0")
            appendLine("prevent-proxy-connections=false")
            appendLine("rate-limit=0")
            appendLine("snooper-enabled=false")
            appendLine("sync-chunk-writes=true")
            appendLine("use-native-transport=true")
        }
        file.writeText(text)
    }

    fun load(file: File): Properties {
        val props = Properties()
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        }
        return props
    }

    fun save(file: File, props: Properties) {
        file.outputStream().use { out ->
            props.store(out, "Nexus Launcher server.properties")
        }
    }

    /** Apply ServerConfig toggles into existing properties file (preserve unknown keys). */
    fun applyConfig(file: File, config: ServerConfig) {
        val props = load(file)
        props.setProperty("motd", config.motd)
        props.setProperty("server-port", config.port.toString())
        props.setProperty("max-players", config.maxPlayers.toString())
        props.setProperty("online-mode", config.onlineMode.toString())
        props.setProperty("white-list", config.whitelist.toString())
        props.setProperty("enforce-whitelist", config.whitelist.toString())
        props.setProperty("difficulty", config.difficulty)
        props.setProperty("gamemode", config.gamemode)
        props.setProperty("view-distance", config.viewDistance.toString())
        props.setProperty("simulation-distance", config.simulationDistance.toString())
        props.setProperty("pvp", config.pvp.toString())
        props.setProperty("hardcore", config.hardcore.toString())
        props.setProperty("allow-flight", config.allowFlight.toString())
        props.setProperty("enable-command-block", config.enableCommandBlocks.toString())
        props.setProperty("spawn-protection", config.spawnProtection.toString())
        props.setProperty("level-name", config.worldDir)
        save(file, props)
    }

    fun readBool(props: Properties, key: String, default: Boolean): Boolean =
        props.getProperty(key)?.toBooleanStrictOrNull() ?: default

    fun readInt(props: Properties, key: String, default: Int): Int =
        props.getProperty(key)?.toIntOrNull() ?: default

    fun readString(props: Properties, key: String, default: String): String =
        props.getProperty(key) ?: default
}
