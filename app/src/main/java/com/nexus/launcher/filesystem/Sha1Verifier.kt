package com.nexus.launcher.filesystem

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Sha1Verifier @Inject constructor() {
    fun verifyFile(file: File, expectedSha1: String): Boolean {
        if (!file.exists()) return false
        val actualSha1 = calculateSha1(file)
        return actualSha1.equals(expectedSha1, ignoreCase = true)
    }

    fun calculateSha1(file: File): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                sha1.update(buffer, 0, read)
            }
        }
        return sha1.digest().joinToString("") { "%02x".format(it) }
    }

    fun calculateSha1String(input: String): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
        return sha1.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
