package com.nexus.launcher.server

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Safe RAM recommendations for on-device Java server hosting.
 * Never allocate all device RAM — leave headroom for Android, launcher, and optional client.
 */
@Singleton
class ServerRamAdvisor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Advice(
        val totalDeviceMb: Long,
        val availableMb: Long,
        val recommendedServerMb: Int,
        val minimumServerMb: Int,
        val maximumSafeServerMb: Int,
        val warning: String?
    )

    fun advise(alsoPlayingClient: Boolean = false): Advice {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)

        val totalMb = info.totalMem / (1024 * 1024)
        val availMb = info.availMem / (1024 * 1024)

        // Reserve ~1.5GB for system + launcher; more if client will run simultaneously
        val reserveMb = if (alsoPlayingClient) 2048L else 1536L
        val usable = (availMb - reserveMb).coerceAtLeast(512)

        val recommended = when {
            usable >= 3072 -> 1536
            usable >= 2048 -> 1024
            usable >= 1536 -> 768
            else -> 512
        }.coerceAtMost(usable.toInt())

        val maxSafe = (usable * 0.7).toInt().coerceAtLeast(512).coerceAtMost(4096)
        val min = 512

        val warning = when {
            alsoPlayingClient && totalMb < 4096 ->
                "Playing and hosting on the same phone will compete for RAM. Expect lower performance."
            availMb < 1024 ->
                "Low available memory. Close other apps before starting a server."
            recommended < 1024 ->
                "Device has limited free RAM. Use a low view-distance and few players."
            else -> null
        }

        return Advice(
            totalDeviceMb = totalMb,
            availableMb = availMb,
            recommendedServerMb = recommended,
            minimumServerMb = min,
            maximumSafeServerMb = maxSafe,
            warning = warning
        )
    }

    fun isAllocationSafe(requestedMb: Int, alsoPlayingClient: Boolean = false): Boolean {
        val advice = advise(alsoPlayingClient)
        return requestedMb in advice.minimumServerMb..advice.maximumSafeServerMb
    }
}
