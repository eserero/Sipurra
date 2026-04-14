package snd.komelia.audiobook

import snd.komga.client.book.KomgaBookId

interface AudioPositionRepository {
    suspend fun getPosition(bookId: KomgaBookId): AudioPosition?
    suspend fun savePosition(position: AudioPosition)
}
