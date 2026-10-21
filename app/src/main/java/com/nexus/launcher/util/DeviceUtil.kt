package com.nexus.launcher.util

object DeviceUtil {
    fun isArm64(): Boolean {
        val arch = System.getProperty("os.arch") ?: ""
        return arch.contains("aarch64") || arch.contains("arm64")
    }

    fun isX86_64(): Boolean {
        val arch = System.getProperty("os.arch") ?: ""
        return arch.contains("x86_64") || arch.contains("amd64")
    }
}
