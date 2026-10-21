package com.nexus.launcher.libraries

import com.nexus.launcher.data.model.VersionJson
import com.nexus.launcher.util.OsUtil
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClasspathBuilder @Inject constructor() {
    fun buildClasspath(versionJson: VersionJson, mcDir: File): String {
        val classpathEntries = mutableListOf<String>()

        versionJson.downloads?.client?.let {
            val clientJar = File(mcDir, "versions/${versionJson.id}/${versionJson.id}.jar")
            if (clientJar.exists()) {
                classpathEntries.add(clientJar.absolutePath)
            }
        }

        versionJson.libraries.forEach { lib ->
            lib.artifact?.let { artifact ->
                val libFile = File(mcDir, "libraries/${artifact.path}")
                if (libFile.exists()) {
                    classpathEntries.add(libFile.absolutePath)
                }
            }
        }

        return classpathEntries.joinToString(OsUtil.classpathSeparator())
    }
}
