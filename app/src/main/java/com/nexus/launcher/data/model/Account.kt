package com.nexus.launcher.data.model

sealed class Account {
    abstract val id: String
    abstract val username: String
    abstract val uuid: String
    abstract val accessToken: String
}

data class MicrosoftAccount(
    override val id: String,
    override val username: String,
    override val uuid: String,
    override val accessToken: String,
    val refreshToken: String,
    val xuid: String? = null,
    val expiresAt: Long = 0L
) : Account()

data class ElyByAccount(
    override val id: String,
    override val username: String,
    override val uuid: String,
    override val accessToken: String
) : Account()

data class OfflineAccount(
    override val id: String,
    override val username: String,
    override val uuid: String,
    override val accessToken: String = "0"
) : Account()
