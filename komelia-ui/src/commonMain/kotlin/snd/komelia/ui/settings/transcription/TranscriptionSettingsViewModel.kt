package snd.komelia.ui.settings.transcription

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import snd.komelia.settings.TranscriptionSettingsRepository
import snd.komelia.settings.model.TranscriptionEngineType
import snd.komelia.settings.model.TranscriptionSettings
import snd.komelia.ui.LoadState
import snd.komelia.updates.UpdateProgress
import snd.komelia.updates.WhisperModelDownloader

class TranscriptionSettingsViewModel(
    private val settingsRepo: TranscriptionSettingsRepository,
    private val whisperDownloader: WhisperModelDownloader?,
) : StateScreenModel<LoadState<Unit>>(LoadState.Uninitialized) {

    val isWhisperAvailable: Boolean = whisperDownloader != null
    val engine = MutableStateFlow(TranscriptionEngineType.ML_KIT)
    val whisperLanguage = MutableStateFlow<String?>(null)
    val isWhisperModelDownloaded = MutableStateFlow(false)
    val downloadProgress = MutableStateFlow<UpdateProgress?>(null)

    suspend fun initialize() {
        if (state.value !is LoadState.Uninitialized) return
        val settings = settingsRepo.getSettings().first()
        engine.value = settings.engine
        whisperLanguage.value = settings.whisperLanguage

        whisperDownloader?.isModelDownloaded()
            ?.onEach { isWhisperModelDownloaded.value = it }
            ?.launchIn(screenModelScope)

        mutableState.value = LoadState.Success(Unit)
    }

    fun onEngineChange(newEngine: TranscriptionEngineType) {
        engine.value = newEngine
        screenModelScope.launch {
            settingsRepo.putSettings(TranscriptionSettings(engine = newEngine, whisperLanguage = whisperLanguage.value))
        }
    }

    fun onLanguageChange(lang: String?) {
        whisperLanguage.value = lang
        screenModelScope.launch {
            settingsRepo.putSettings(TranscriptionSettings(engine = engine.value, whisperLanguage = lang))
        }
    }

    fun onDownloadRequest() = whisperDownloader?.whisperBaseDownload() ?: emptyFlow<UpdateProgress>()
}
