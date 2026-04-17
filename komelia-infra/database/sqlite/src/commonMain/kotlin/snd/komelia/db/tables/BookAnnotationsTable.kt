package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

object BookAnnotationsTable : Table("book_annotations") {
    val id = text("id")
    val bookId = text("book_id")
    val readerType = text("reader_type") // "EPUB3" or "COMIC"
    val locatorJson = text("locator_json").nullable()
    val selectedText = text("selected_text").nullable()
    val highlightColor = integer("highlight_color").nullable()
    val pageNumber = integer("page_number").nullable()
    val x = float("x").nullable()
    val y = float("y").nullable()
    val note = text("note").nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
