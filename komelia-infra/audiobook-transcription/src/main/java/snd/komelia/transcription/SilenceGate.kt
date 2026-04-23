package snd.komelia.transcription

object SilenceGate {
    fun hasSpeech(bytes: ByteArray, threshold: Int = 300): Boolean {
        var i = 0
        while (i < bytes.size - 1) {
            val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xFF)).toShort().toInt()
            if (kotlin.math.abs(sample) > threshold) return true
            i += 2
        }
        return false
    }
}
