package snd.komelia.ui.settings.imagereader.ncnn

import snd.komelia.image.AndroidNcnnUpscaler
import snd.komelia.image.UpscaleStatus
import kotlinx.coroutines.flow.StateFlow

actual fun isNcnnSupported() = true

actual fun globalNcnnUpscaleActivities(): StateFlow<Map<Int, UpscaleStatus>> =
    AndroidNcnnUpscaler.globalUpscaleActivities
