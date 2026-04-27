package snd.komelia.ui.settings.imagereader.rapidocr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.updates.RapidOcrModelDownloader
import snd.komelia.updates.UpdateProgress

class RapidOcrSettingsState(
    private val rapidOcrModelDownloader: RapidOcrModelDownloader?,
    private val settingsRepository: ImageReaderSettingsRepository,
    private val coroutineScope: CoroutineScope,
) {
    val isDownloaded = MutableStateFlow(false)
    val rapidOcrModelsUrl = MutableStateFlow("")

    suspend fun initialize() {
        isDownloaded.value = rapidOcrModelDownloader?.isDownloaded() ?: false
        rapidOcrModelsUrl.value = settingsRepository.getRapidOcrModelsUrl().first()
    }

    fun onRapidOcrModelsUrlChange(url: String) {
        rapidOcrModelsUrl.value = url
        coroutineScope.launch { settingsRepository.putRapidOcrModelsUrl(url) }
    }

    fun downloadFlow(): Flow<UpdateProgress> {
        val downloader = rapidOcrModelDownloader ?: return emptyFlow()
        return downloader.download(rapidOcrModelsUrl.value)
            .onCompletion { isDownloaded.value = downloader.isDownloaded() }
    }
}

expect fun isRapidOcrSupported(): Boolean
