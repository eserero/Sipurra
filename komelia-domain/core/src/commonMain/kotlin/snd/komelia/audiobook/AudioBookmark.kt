package snd.komelia.audiobook

import snd.komga.client.book.KomgaBookId

data class AudioBookmark(
    val id: String,
    val bookId: KomgaBookId,
    val trackIndex: Int,
    val positionSeconds: Double,
    val trackTitle: String,
    val createdAt: Long,
)
