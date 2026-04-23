package snd.komelia.transcription

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "MlKitPipeWriter"

class MlKitAudioPipeWriter(scope: CoroutineScope) {

    private val pipe = ParcelFileDescriptor.createPipe()
    val readSide: ParcelFileDescriptor = pipe[0]
    private val writeSide = pipe[1]
    private val output = FileOutputStream(writeSide.fileDescriptor)

    private val _audioSentUntilMs = AtomicLong(0L)
    val audioSentUntilMs: Long get() = _audioSentUntilMs.get()

    private val writeChannel = Channel<Pair<ByteArray, Long>>(capacity = Channel.BUFFERED)

    init {
        scope.launch(Dispatchers.IO) {
            // WAV header with unknown data length — tells ML Kit the stream is 16kHz mono 16-bit PCM
            output.write(buildStreamingWavHeader())
            output.flush()

            for ((bytes, bookMs) in writeChannel) {
                output.write(bytes)
                output.flush()
                _audioSentUntilMs.set(bookMs)
            }
            Log.d(TAG, "Write loop ended, closing pipe write side")
            runCatching { output.close() }
            runCatching { writeSide.close() }
        }
    }

    private fun buildStreamingWavHeader(): ByteArray {
        val sampleRate = 16_000
        val channels: Short = 1
        val bitsPerSample: Short = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign: Short = (channels * bitsPerSample / 8).toShort()
        val dataSize = 0x7FFFFFFF // unknown streaming length
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(dataSize + 36)
            put("WAVE".toByteArray()); put("fmt ".toByteArray())
            putInt(16); putShort(1); putShort(channels)
            putInt(sampleRate); putInt(byteRate)
            putShort(blockAlign); putShort(bitsPerSample)
            put("data".toByteArray()); putInt(dataSize)
        }.array()
    }

    fun writePcm(pcm: ByteArray, sampleRate: Int, channelCount: Int, bookTimeMs: Long) {
        val converted = Pcm16MonoResampler.convertTo16kMono(
            pcm, Pcm16MonoResampler.InputFormat(sampleRate, channelCount)
        )
        val sent = writeChannel.trySend(Pair(converted, bookTimeMs))
        if (sent.isFailure) Log.w(TAG, "trySend failed at bookMs=${bookTimeMs}ms (channel full?)")
    }

    fun close() {
        Log.d(TAG, "close() called")
        writeChannel.close()
    }
}
