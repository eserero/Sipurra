package snd.komelia.db.bookmarks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import snd.komelia.bookmarks.EpubBookmark
import snd.komelia.bookmarks.EpubBookmarkRepository
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.EpubBookmarksTable
import snd.komga.client.book.KomgaBookId

class ExposedEpubBookmarkRepository(database: Database) : ExposedRepository(database), EpubBookmarkRepository {

    private val bookmarksChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun getBookmarks(bookId: KomgaBookId): Flow<List<EpubBookmark>> {
        return bookmarksChanged.onStart { emit(Unit) }.map {
            transaction {
                EpubBookmarksTable.selectAll()
                    .where { EpubBookmarksTable.bookId eq bookId.value }
                    .orderBy(EpubBookmarksTable.createdAt, org.jetbrains.exposed.v1.core.SortOrder.ASC)
                    .map {
                        EpubBookmark(
                            id = it[EpubBookmarksTable.id],
                            bookId = KomgaBookId(it[EpubBookmarksTable.bookId]),
                            locatorJson = it[EpubBookmarksTable.locatorJson],
                            createdAt = it[EpubBookmarksTable.createdAt]
                        )
                    }
            }
        }
    }

    override suspend fun saveBookmark(bookmark: EpubBookmark) {
        transaction {
            EpubBookmarksTable.insert {
                it[id] = bookmark.id
                it[bookId] = bookmark.bookId.value
                it[locatorJson] = bookmark.locatorJson
                it[createdAt] = bookmark.createdAt
            }
        }
        bookmarksChanged.tryEmit(Unit)
    }

    override suspend fun deleteBookmark(id: String) {
        transaction {
            EpubBookmarksTable.deleteWhere { EpubBookmarksTable.id eq id }
        }
        bookmarksChanged.tryEmit(Unit)
    }
}
