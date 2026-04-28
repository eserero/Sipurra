package snd.komelia.transcription

data class TranscriptSegment(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val isFinal: Boolean,
    val chunkId: Long? = null,
)
