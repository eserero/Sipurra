package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

object EpubBookmarksTable : Table("epub_bookmarks") {
    val id = text("id")
    val bookId = text("book_id")
    val locatorJson = text("locator_json")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
