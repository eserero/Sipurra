package snd.komelia.ui.settings.imagereader.ncnn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import snd.komelia.image.UpscaleStatus
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.settings.model.NcnnUpscalerSettings

expect fun isNcnnSupported(): Boolean
expect fun globalNcnnUpscaleActivities(): StateFlow<Map<Int, UpscaleStatus>>

class NcnnSettingsState(
    private val settingsRepository: ImageReaderSettingsRepository,
    private val coroutineScope: CoroutineScope,
) {
    val ncnnUpscalerSettings = settingsRepository.getNcnnUpscalerSettings()
        .stateIn(coroutineScope, SharingStarted.Eagerly, NcnnUpscalerSettings())

    val globalUpscaleActivities = globalNcnnUpscaleActivities()

    suspend fun initialize() {
        // ...
    }

    fun onSettingsChange(settings: NcnnUpscalerSettings) {
        coroutineScope.launch { settingsRepository.putNcnnUpscalerSettings(settings) }
    }
}
