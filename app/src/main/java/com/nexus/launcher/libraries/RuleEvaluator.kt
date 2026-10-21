package com.nexus.launcher.libraries

import com.nexus.launcher.data.model.LibraryRule
import com.nexus.launcher.util.OsUtil
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleEvaluator @Inject constructor() {
    fun evaluateRules(rules: List<LibraryRule>?): Boolean {
        if (rules.isNullOrEmpty()) return true

        var allowed = false
        rules.forEach { rule ->
            val ruleMatches = matchesOs(rule) && matchesFeatures(rule)
            if (ruleMatches) {
                allowed = rule.action == "allow"
            }
        }
        return allowed
    }

    private fun matchesOs(rule: LibraryRule): Boolean {
        val osName = rule.osName ?: return true
        return when (osName) {
            "linux" -> OsUtil.isLinux()
            "windows" -> OsUtil.isWindows()
            "osx" -> OsUtil.isMac()
            else -> true
        }
    }

    private fun matchesFeatures(rule: LibraryRule): Boolean {
        val features = rule.features ?: return true
        return features.values.all { !it }
    }
}
