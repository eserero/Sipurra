package snd.komelia.transcription

internal object WhisperJni {
    init { System.loadLibrary("whisper_jni") }

    external fun loadModel(modelPath: String): Long
    external fun transcribeChunk(ctx: Long, pcmFloat: FloatArray, offsetMs: Long, language: String?): Array<WhisperResult>
    external fun freeContext(ctx: Long)
}

data class WhisperResult(val startMs: Long, val endMs: Long, val text: String)
