package snd.komelia.db

import snd.komelia.settings.model.TranscriptionEngineType

data class TranscriptionSettings(
    val engine: TranscriptionEngineType = TranscriptionEngineType.ML_KIT,
    val whisperLanguage: String? = null,
)
