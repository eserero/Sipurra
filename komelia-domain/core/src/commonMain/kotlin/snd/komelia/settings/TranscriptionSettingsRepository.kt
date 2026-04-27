package snd.komelia.settings

import kotlinx.coroutines.flow.Flow
import snd.komelia.settings.model.TranscriptionSettings

interface TranscriptionSettingsRepository {
    fun getSettings(): Flow<TranscriptionSettings>
    suspend fun putSettings(settings: TranscriptionSettings)
}
