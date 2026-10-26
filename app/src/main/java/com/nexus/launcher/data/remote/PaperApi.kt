package com.nexus.launcher.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * PaperMC REST API — https://api.papermc.io
 */
interface PaperApi {
    @GET("v2/projects/paper")
    suspend fun getProject(): Response<PaperProjectResponse>

    @GET("v2/projects/paper/versions/{version}")
    suspend fun getVersion(@Path("version") version: String): Response<PaperVersionResponse>

    @GET("v2/projects/paper/versions/{version}/builds/{build}")
    suspend fun getBuild(
        @Path("version") version: String,
        @Path("build") build: Int
    ): Response<PaperBuildResponse>
}

data class PaperProjectResponse(
    val project_id: String?,
    val project_name: String?,
    val versions: List<String>?
)

data class PaperVersionResponse(
    val version: String?,
    val builds: List<Int>?
)

data class PaperBuildResponse(
    val build: Int?,
    val channel: String?,
    val downloads: Map<String, PaperDownload>?,
    val promoted: Boolean? = false
)

data class PaperDownload(
    val name: String?,
    val sha256: String?
)
