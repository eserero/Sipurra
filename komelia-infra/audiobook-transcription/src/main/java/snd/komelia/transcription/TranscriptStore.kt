package snd.komelia.transcription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

class TranscriptStore {
    private val nextId = AtomicLong(1)
    private val _segments = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    val segments: StateFlow<List<TranscriptSegment>> = _segments

    fun upsertLiveSegment(
        approxEndMs: Long,
        text: String,
        isFinal: Boolean,
        approxDurationMs: Long = 2_000L,
    ) {
        if (text.isBlank()) return

        val startMs = (approxEndMs - approxDurationMs).coerceAtLeast(0L)
        val endMs = approxEndMs.coerceAtLeast(startMs)

        _segments.update { old ->
            val mutable = old.toMutableList()

            if (!isFinal) {
                val idx = mutable.indexOfLast { !it.isFinal }
                if (idx >= 0) {
                    mutable[idx] = mutable[idx].copy(startMs = startMs, endMs = endMs, text = text)
                } else {
                    mutable += TranscriptSegment(nextId.getAndIncrement(), startMs, endMs, text, false)
                }
            } else {
                val idx = mutable.indexOfLast { !it.isFinal }
                if (idx >= 0) {
                    mutable[idx] = mutable[idx].copy(startMs = startMs, endMs = endMs, text = text, isFinal = true)
                } else {
                    mutable += TranscriptSegment(nextId.getAndIncrement(), startMs, endMs, text, true)
                }
            }

            // keep last 60 minutes of session
            val cutoff = (mutable.maxOfOrNull { it.endMs } ?: 0L) - 60 * 60_000L
            mutable.filter { it.endMs >= cutoff }
        }
    }

    fun visibleSegmentsForPlayback(playbackMs: Long): List<TranscriptSegment> {
        val all = _segments.value
        val spoken = all.filter { it.isFinal }
        val interim = all.lastOrNull { !it.isFinal }
        return if (interim != null) spoken + interim else spoken
    }

    fun nextId(): Long = nextId.getAndIncrement()

    fun addSegments(segments: List<TranscriptSegment>) {
        if (segments.isEmpty()) return
        _segments.update { old ->
            val mutable = (old + segments).toMutableList()
            // keep last 60 minutes of session
            val cutoff = (mutable.maxOfOrNull { it.endMs } ?: 0L) - 60 * 60_000L
            mutable.filter { it.endMs >= cutoff }
        }
    }

    fun clear() {
        _segments.value = emptyList()
    }
}
