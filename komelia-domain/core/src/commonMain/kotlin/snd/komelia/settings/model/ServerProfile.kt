package snd.komelia.settings.model

import kotlinx.datetime.Instant

data class ServerProfile(
    val id: Long = 0,
    val name: String,
    val url: String,
    val username: String,
    val lastActive: Instant? = null
)
