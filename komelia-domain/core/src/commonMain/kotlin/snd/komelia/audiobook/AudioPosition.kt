package snd.komelia.audiobook

import snd.komga.client.book.KomgaBookId

data class AudioPosition(
    val bookId: KomgaBookId,
    val trackIndex: Int,
    val positionSeconds: Double,
    val savedAt: Long,
)
