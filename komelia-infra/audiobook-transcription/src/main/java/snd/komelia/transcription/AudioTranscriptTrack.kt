package snd.komelia.transcription

import android.net.Uri

data class AudioTranscriptTrack(
    val uri: Uri,
    val bookOffsetMs: Long,
    val durationMs: Long,
)
