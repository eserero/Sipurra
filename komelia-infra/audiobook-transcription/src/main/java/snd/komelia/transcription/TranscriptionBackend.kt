package snd.komelia.transcription

import kotlinx.coroutines.flow.StateFlow

interface TranscriptionBackend {
    val state: StateFlow<TranscriptEngineState>

    suspend fun start()
    suspend fun onPcmChunk(bytes: ByteArray, bookTimeMs: Long, durationMs: Long)
    fun onSeek(newPositionMs: Long)
    fun stop()
}
