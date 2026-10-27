package com.nexus.launcher.version

import com.nexus.launcher.data.model.Arguments
import com.nexus.launcher.data.model.Library
import com.nexus.launcher.data.model.VersionJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves inheritsFrom chains (Fabric/Forge/Quilt version JSONs inherit from vanilla).
 * Child fields override parent; libraries are concatenated (child first for overrides).
 */
@Singleton
class VersionInheritanceResolver @Inject constructor(
    private val versionManager: VersionManager
) {
    suspend fun resolve(versionId: String): VersionJson = withContext(Dispatchers.IO) {
        val chain = mutableListOf<VersionJson>()
        var currentId: String? = versionId
        val seen = mutableSetOf<String>()

        while (currentId != null) {
            if (!seen.add(currentId)) {
                throw IllegalStateException("Inheritance cycle at $currentId")
            }
            val node = versionManager.getVersionDetails(currentId)
            chain.add(node)
            currentId = node.inheritsFrom
        }

        // chain[0] = leaf (e.g. fabric-loader), chain.last() = root vanilla
        mergeChain(chain)
    }

    fun mergeChain(chain: List<VersionJson>): VersionJson {
        require(chain.isNotEmpty())
        if (chain.size == 1) return chain[0]

        // Start from root (oldest parent), apply children on top
        var merged = chain.last()
        for (i in chain.size - 2 downTo 0) {
            merged = mergeChildOverParent(child = chain[i], parent = merged)
        }
        return merged
    }

    private fun mergeChildOverParent(child: VersionJson, parent: VersionJson): VersionJson {
        val libMap = LinkedHashMap<String, Library>()
        // Parent libs first, then child overwrites same name
        parent.libraries.forEach { libMap[it.name] = it }
        child.libraries.forEach { libMap[it.name] = it }

        return VersionJson(
            id = child.id,
            inheritsFrom = null, // fully resolved
            mainClass = child.mainClass.ifBlank { parent.mainClass },
            assets = if (child.assets.isNotBlank() && child.assets != child.id) child.assets else parent.assets,
            assetIndex = child.assetIndex ?: parent.assetIndex,
            downloads = VersionDownloadsMerge(child.downloads, parent.downloads),
            libraries = libMap.values.toList(),
            arguments = mergeArguments(child.arguments, parent.arguments),
            legacyArguments = child.legacyArguments ?: parent.legacyArguments,
            logging = child.logging ?: parent.logging,
            javaVersion = child.javaVersion ?: parent.javaVersion,
            type = child.type.ifBlank { parent.type },
            time = child.time.ifBlank { parent.time },
            releaseTime = child.releaseTime.ifBlank { parent.releaseTime },
            minimumLauncherVersion = maxOf(child.minimumLauncherVersion, parent.minimumLauncherVersion)
        )
    }

    private fun VersionDownloadsMerge(
        child: com.nexus.launcher.data.model.VersionDownloads?,
        parent: com.nexus.launcher.data.model.VersionDownloads?
    ): com.nexus.launcher.data.model.VersionDownloads? {
        if (child == null) return parent
        if (parent == null) return child
        return com.nexus.launcher.data.model.VersionDownloads(
            client = child.client ?: parent.client,
            server = child.server ?: parent.server,
            client_mappings = child.client_mappings ?: parent.client_mappings,
            server_mappings = child.server_mappings ?: parent.server_mappings
        )
    }

    private fun mergeArguments(child: Arguments?, parent: Arguments?): Arguments? {
        if (child == null) return parent
        if (parent == null) return child
        return Arguments(
            game = parent.game + child.game,
            jvm = parent.jvm + child.jvm
        )
    }
}
