package snd.komelia.bookmarks

import kotlinx.coroutines.flow.Flow
import snd.komga.client.book.KomgaBookId

interface EpubBookmarkRepository {
    fun getBookmarks(bookId: KomgaBookId): Flow<List<EpubBookmark>>
    suspend fun saveBookmark(bookmark: EpubBookmark)
    suspend fun deleteBookmark(id: String)
}
