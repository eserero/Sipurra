package snd.komelia.db

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import snd.komelia.settings.model.Epub3NativeSettings
import snd.komelia.settings.model.EpubReaderType
import snd.komelia.settings.model.TtsuReaderSettings

@Serializable
data class EpubReaderSettings(
    val readerType: EpubReaderType = EpubReaderType.EPUB3_READER,
    val komgaReaderSettings: JsonObject = buildJsonObject { },
    val ttsuReaderSettings: TtsuReaderSettings = TtsuReaderSettings(),
    val epub3NativeSettings: Epub3NativeSettings = Epub3NativeSettings(),
    val epubCacheSizeLimitMb: Long = 2048L,
)