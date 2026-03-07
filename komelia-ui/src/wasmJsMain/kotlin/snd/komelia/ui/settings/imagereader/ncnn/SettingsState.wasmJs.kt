package snd.komelia.ui.settings.imagereader.ncnn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import snd.komelia.image.UpscaleStatus

actual fun isNcnnSupported() = false

actual fun globalNcnnUpscaleActivities(): StateFlow<Map<Int, UpscaleStatus>> =
    MutableStateFlow(emptyMap())
