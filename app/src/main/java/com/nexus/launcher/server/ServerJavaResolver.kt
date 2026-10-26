package com.nexus.launcher.server

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps Minecraft server version → required major Java version.
 * User picks Java once (8 / 17 / 21); all MC versions that need that Java run on it.
 *
 * Note: Java 21 can run 1.17+ servers. Older servers (1.16 and below) need Java 8/17.
 * Cross-client protocol (1.21.1 joining 1.21.x) is handled by ViaVersion on Paper — see ViaVersionInstaller.
 */
@Singleton
class ServerJavaResolver @Inject constructor() {

    enum class JavaMajor(val major: Int, val label: String) {
        JAVA_8(8, "Java 8"),
        JAVA_17(17, "Java 17"),
        JAVA_21(21, "Java 21")
    }

    /** Recommended Java for a Minecraft version string like "1.21.1" or "1.16.5". */
    fun requiredJava(minecraftVersion: String): JavaMajor {
        val parts = minecraftVersion.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0

        return when {
            major >= 2 -> JavaMajor.JAVA_21
            minor >= 20 -> JavaMajor.JAVA_21   // 1.20+
            minor >= 17 -> JavaMajor.JAVA_17   // 1.17–1.19
            else -> JavaMajor.JAVA_8           // 1.16 and below
        }
    }

    /** All Java majors the UI should offer. */
    fun availableRuntimes(): List<JavaMajor> = JavaMajor.entries

    /**
     * MC versions that can run on the selected Java without forcing a different JRE.
     * Java 21 → 1.20+
     * Java 17 → 1.17–1.19 (and can also run some older with care)
     * Java 8  → 1.16 and below
     */
    fun versionsCompatibleWith(java: JavaMajor, allVersions: List<String>): List<String> {
        return allVersions.filter { requiredJava(it) == java || canRunOn(it, java) }
    }

    private fun canRunOn(mc: String, java: JavaMajor): Boolean {
        val need = requiredJava(mc)
        // Newer Java can run older bytecode in many cases, but we keep strict defaults
        // except: Java 21 is preferred for all 1.20+
        return when (java) {
            JavaMajor.JAVA_21 -> need == JavaMajor.JAVA_21 || need == JavaMajor.JAVA_17
            JavaMajor.JAVA_17 -> need == JavaMajor.JAVA_17 || need == JavaMajor.JAVA_8
            JavaMajor.JAVA_8 -> need == JavaMajor.JAVA_8
        }
    }
}
