package snd.komelia.db.annotations

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import snd.komelia.annotations.AnnotationLocation
import snd.komelia.annotations.BookAnnotation
import snd.komelia.annotations.BookAnnotationRepository
import snd.komelia.db.ExposedRepository
import snd.komelia.db.tables.BookAnnotationsTable
import snd.komga.client.book.KomgaBookId

class ExposedBookAnnotationRepository(database: Database) :
    ExposedRepository(database), BookAnnotationRepository {

    private val annotationsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun getAnnotations(bookId: KomgaBookId): Flow<List<BookAnnotation>> {
        return annotationsChanged.onStart { emit(Unit) }.map {
            transaction {
                BookAnnotationsTable.selectAll()
                    .where { BookAnnotationsTable.bookId eq bookId.value }
                    .orderBy(BookAnnotationsTable.createdAt, org.jetbrains.exposed.v1.core.SortOrder.ASC)
                    .mapNotNull { row ->
                        val readerType = row[BookAnnotationsTable.readerType]
                        val location: AnnotationLocation = when (readerType) {
                            "EPUB3" -> AnnotationLocation.EpubLocation(
                                locatorJson = row[BookAnnotationsTable.locatorJson] ?: return@mapNotNull null,
                                selectedText = row[BookAnnotationsTable.selectedText],
                            )
                            "COMIC" -> AnnotationLocation.ComicLocation(
                                page = row[BookAnnotationsTable.pageNumber] ?: return@mapNotNull null,
                                x = row[BookAnnotationsTable.x] ?: return@mapNotNull null,
                                y = row[BookAnnotationsTable.y] ?: return@mapNotNull null,
                            )
                            else -> return@mapNotNull null
                        }
                        BookAnnotation(
                            id = row[BookAnnotationsTable.id],
                            bookId = KomgaBookId(row[BookAnnotationsTable.bookId]),
                            location = location,
                            highlightColor = row[BookAnnotationsTable.highlightColor],
                            note = row[BookAnnotationsTable.note],
                            createdAt = row[BookAnnotationsTable.createdAt],
                        )
                    }
            }
        }
    }

    override suspend fun saveAnnotation(annotation: BookAnnotation) {
        transaction {
            BookAnnotationsTable.insert {
                it[id] = annotation.id
                it[bookId] = annotation.bookId.value
                it[highlightColor] = annotation.highlightColor
                it[note] = annotation.note
                it[createdAt] = annotation.createdAt
                when (val loc = annotation.location) {
                    is AnnotationLocation.EpubLocation -> {
                        it[readerType] = "EPUB3"
                        it[locatorJson] = loc.locatorJson
                        it[selectedText] = loc.selectedText
                    }
                    is AnnotationLocation.ComicLocation -> {
                        it[readerType] = "COMIC"
                        it[pageNumber] = loc.page
                        it[x] = loc.x
                        it[y] = loc.y
                    }
                }
            }
        }
        annotationsChanged.tryEmit(Unit)
    }

    override suspend fun deleteAnnotation(id: String) {
        transaction {
            BookAnnotationsTable.deleteWhere { BookAnnotationsTable.id eq id }
        }
        annotationsChanged.tryEmit(Unit)
    }
}
