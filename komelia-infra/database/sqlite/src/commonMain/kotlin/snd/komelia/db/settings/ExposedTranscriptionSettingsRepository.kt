package snd.komelia.db.settings

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import snd.komelia.db.ExposedRepository
import snd.komelia.db.TranscriptionSettings
import snd.komelia.db.defaultBookId
import snd.komelia.db.tables.TranscriptionSettingsTable
import snd.komelia.settings.model.TranscriptionEngineType

class ExposedTranscriptionSettingsRepository(database: Database) : ExposedRepository(database) {

    suspend fun get(): TranscriptionSettings? {
        return transaction {
            TranscriptionSettingsTable.selectAll()
                .where { TranscriptionSettingsTable.bookId.eq(defaultBookId) }
                .firstOrNull()
                ?.let {
                    TranscriptionSettings(
                        engine = TranscriptionEngineType.valueOf(it[TranscriptionSettingsTable.transcriptionEngine]),
                        whisperLanguage = it[TranscriptionSettingsTable.whisperLanguage],
                    )
                }
        }
    }

    suspend fun save(settings: TranscriptionSettings) {
        transaction {
            TranscriptionSettingsTable.upsert {
                it[bookId] = defaultBookId
                it[transcriptionEngine] = settings.engine.name
                it[whisperLanguage] = settings.whisperLanguage
            }
        }
    }
}
