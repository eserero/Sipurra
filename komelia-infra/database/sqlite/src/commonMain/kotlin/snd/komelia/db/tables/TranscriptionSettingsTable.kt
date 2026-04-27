package snd.komelia.db.tables

import org.jetbrains.exposed.v1.core.Table

object TranscriptionSettingsTable : Table("TranscriptionSettings") {
    val bookId = text("book_id")
    val transcriptionEngine = text("transcription_engine").default("ML_KIT")
    val whisperLanguage = text("whisper_language").nullable()

    override val primaryKey = PrimaryKey(bookId)
}
