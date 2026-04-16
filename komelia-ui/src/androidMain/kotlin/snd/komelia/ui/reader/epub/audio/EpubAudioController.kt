package snd.komelia.ui.reader.epub.audio

import kotlinx.coroutines.flow.StateFlow
import snd.komelia.settings.model.Epub3NativeSettings

interface EpubAudioController {
    val isPlaying: StateFlow<Boolean>
    val volume: StateFlow<Float>
    val elapsedSeconds: StateFlow<Double>
    val totalDurationSeconds: StateFlow<Double>

    fun togglePlayPause()
    fun seekToNext()
    fun seekToPrev()
    fun setVolume(v: Float)
    fun applyAudioSettings(settings: Epub3NativeSettings)
    fun release()

    /**
     * Seeks relative to the current global playback position.
     * Positive = forward, negative = backward. Crosses track boundaries.
     * Default no-op — only meaningful for folder-mode audiobooks.
     */
    fun seekRelative(deltaSeconds: Double) {}
}
