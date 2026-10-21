package com.nexus.launcher.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface MojangApi {
    @GET("mc/game/version_manifest_v2.json")
    suspend fun getVersionManifest(): Response<VersionManifestResponse>

    @GET
    suspend fun downloadVersionJson(@Url url: String): Response<ResponseBody>

    @GET
    suspend fun downloadAssetIndex(@Url url: String): Response<ResponseBody>
}

data class VersionManifestResponse(
    val latest: LatestVersions,
    val versions: List<VersionEntry>
)

data class LatestVersions(
    val release: String,
    val snapshot: String
)

data class VersionEntry(
    val id: String,
    val type: String,
    val url: String,
    val time: String,
    val releaseTime: String,
    val sha1: String
)
