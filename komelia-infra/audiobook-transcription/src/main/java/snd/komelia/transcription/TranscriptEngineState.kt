package snd.komelia.transcription

sealed interface TranscriptEngineState {
    data object Idle : TranscriptEngineState
    data class Downloading(val progress: Float?) : TranscriptEngineState
    data class Active(val diagnostics: String = "") : TranscriptEngineState
    data class Error(val message: String) : TranscriptEngineState
    data object UnsupportedDevice : TranscriptEngineState
}
