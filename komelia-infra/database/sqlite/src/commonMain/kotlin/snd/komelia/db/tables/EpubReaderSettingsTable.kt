package snd.komelia.db.tables

import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.json
import snd.komelia.db.JsonDbDefault
import snd.komelia.settings.model.Epub3NativeSettings
import snd.komelia.settings.model.TtsuReaderSettings

object EpubReaderSettingsTable : Table("EpubReaderSettings") {
    val bookId = text("book_id")
    val readerType = text("reader_type")
    val komgaSettingsJson = json<JsonObject>("komga_settings_json", JsonDbDefault)
    val ttsuSettingsJson = json<TtsuReaderSettings>("ttsu_settings_json", JsonDbDefault)
    val epub3NativeSettingsJson = json<Epub3NativeSettings>("epub3_native_settings_json", JsonDbDefault).default(Epub3NativeSettings())
    val topMargin = float("epub3_top_margin").default(56f)
    val bottomMargin = float("epub3_bottom_margin").default(66f)

    override val primaryKey = PrimaryKey(bookId)
}
