package com.nexus.launcher.cosmetics.source

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight reachability check for catalogue sources (no full page fetch).
 */
@Singleton
class SourceHealthChecker @Inject constructor(
    private val httpClient: OkHttpClient
) {
    data class Report(
        val sourceId: String,
        val ok: Boolean,
        val detail: String
    )

    suspend fun checkAll(): List<Report> {
        return listOf(
            check("local", true, "Built-in catalogue"),
            checkUrl("mineskin", "https://api.mineskin.org/get/list/0"),
            checkUrl("mojang_username", "https://api.mojang.com/")
        )
    }

    private fun check(id: String, ok: Boolean, detail: String) = Report(id, ok, detail)

    private fun checkUrl(id: String, url: String): Report {
        return try {
            val req = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "NexusLauncherPro/1.0")
                .build()
            // Some APIs reject HEAD — fall back to GET with short timeout client
            val client = httpClient.newBuilder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful || resp.code in listOf(405, 403, 401)) {
                    // 405 = HEAD not allowed but host up
                    Report(id, true, "HTTP ${resp.code}")
                } else {
                    // retry GET lightly
                    val get = Request.Builder()
                        .url(url)
                        .get()
                        .header("User-Agent", "NexusLauncherPro/1.0")
                        .build()
                    client.newCall(get).execute().use { r2 ->
                        Report(id, r2.isSuccessful, "HTTP ${r2.code}")
                    }
                }
            }
        } catch (e: Exception) {
            Report(id, false, e.message ?: "unreachable")
        }
    }
}
