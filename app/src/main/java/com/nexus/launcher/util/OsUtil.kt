package com.nexus.launcher.util

object OsUtil {
    fun isLinux(): Boolean = true // Android is Linux-based
    fun isLinuxArm64(): Boolean = System.getProperty("os.arch")?.contains("aarch64") == true ||
            System.getProperty("os.arch")?.contains("arm64") == true
    fun isWindows(): Boolean = false
    fun isMac(): Boolean = false

    fun classpathSeparator(): String = ":"

    fun executableExtension(): String = ""
}
