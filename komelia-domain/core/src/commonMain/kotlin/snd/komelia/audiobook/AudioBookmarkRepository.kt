package snd.komelia.audiobook

import kotlinx.coroutines.flow.Flow
import snd.komga.client.book.KomgaBookId

interface AudioBookmarkRepository {
    fun getBookmarks(bookId: KomgaBookId): Flow<List<AudioBookmark>>
    suspend fun saveBookmark(bookmark: AudioBookmark)
    suspend fun deleteBookmark(id: String)
}
