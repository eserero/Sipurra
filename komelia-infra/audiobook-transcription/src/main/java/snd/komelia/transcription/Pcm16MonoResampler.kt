package snd.komelia.transcription

import kotlin.math.roundToInt

object Pcm16MonoResampler {

    data class InputFormat(val sampleRate: Int, val channelCount: Int)

    fun convertTo16kMono(input: ByteArray, format: InputFormat): ByteArray {
        require(format.channelCount >= 1)
        val inputSamples = bytesToShorts(input)
        val mono = if (format.channelCount == 1) inputSamples
                   else downmixToMono(inputSamples, format.channelCount)
        return if (format.sampleRate == 16_000) shortsToBytes(mono)
               else shortsToBytes(resampleLinear(mono, format.sampleRate, 16_000))
    }

    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val out = ShortArray(bytes.size / 2)
        var i = 0; var j = 0
        while (i < bytes.size - 1) {
            out[j++] = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xFF)).toShort()
            i += 2
        }
        return out
    }

    private fun shortsToBytes(samples: ShortArray): ByteArray {
        val out = ByteArray(samples.size * 2); var j = 0
        for (s in samples) {
            out[j++] = (s.toInt() and 0xFF).toByte()
            out[j++] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun downmixToMono(interleaved: ShortArray, channels: Int): ShortArray {
        val frames = interleaved.size / channels
        val out = ShortArray(frames); var src = 0
        for (i in 0 until frames) {
            var sum = 0
            repeat(channels) { sum += interleaved[src++].toInt() }
            out[i] = (sum / channels).toShort()
        }
        return out
    }

    private fun resampleLinear(input: ShortArray, inRate: Int, outRate: Int): ShortArray {
        if (input.isEmpty()) return input
        val outLength = ((input.size.toDouble() * outRate) / inRate).roundToInt().coerceAtLeast(1)
        val out = ShortArray(outLength)
        for (i in 0 until outLength) {
            val srcPos = i.toDouble() * inRate / outRate
            val left = srcPos.toInt().coerceIn(0, input.lastIndex)
            val right = (left + 1).coerceIn(0, input.lastIndex)
            val frac = srcPos - left
            val sample = input[left] * (1.0 - frac) + input[right] * frac
            out[i] = sample.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }
}
