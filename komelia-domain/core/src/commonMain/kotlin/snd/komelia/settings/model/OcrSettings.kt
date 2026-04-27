package snd.komelia.settings.model

import kotlinx.serialization.Serializable

@Serializable
data class OcrSettings(
    val enabled: Boolean = false,
    val selectedLanguage: OcrLanguage = OcrLanguage.LATIN,
    val engine: OcrEngine = OcrEngine.ML_KIT,
    val mergeBoxes: Boolean = true,
    val rapidOcrModel: RapidOcrModel = RapidOcrModel.ENGLISH_CHINESE,
)

@Serializable
enum class OcrLanguage {
    LATIN,
    CHINESE,
    DEVANAGARI,
    JAPANESE,
    KOREAN
}

@Serializable
enum class OcrEngine {
    ML_KIT,
    RAPID_OCR
}

@Serializable
enum class RapidOcrModel {
    ENGLISH_CHINESE,
    ENGLISH_ONLY,
    LATIN_MULTILINGUAL,
    JAPANESE,
    KOREAN,
    ARABIC,
    HEBREW
}
