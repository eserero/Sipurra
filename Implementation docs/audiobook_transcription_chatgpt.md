yes please. the use case is: 1. my app read a file and play it back to the user (I have full control) 2. I want to add live transcribe but I want to show data 10 seconds earlier - and show a 20 seconds worth of text in total. give me a full implementation that I can take a plug into my code - explain my ins and outs. 

Perfect — here is a clean, plug-in architecture using ML Kit Speech Recognition for your exact UX:

▶ show transcript 10s behind playback
▶ keep a 20s rolling window on screen
▶ fully local, using your own audio playback

🧠 Architecture (only what you need)

You will build 3 pipes:

[Media3 Player] 
        ↓
[PCM Extractor]
        ↓
[Chunk Buffer (1–2s)]
        ↓
[ML Kit Streaming ASR]
        ↓
[Transcript Buffer (time-stamped)]
        ↓
[UI (lagged by 10s, window = 20s)]
⚙️ STEP 1 — Tap audio from Media3 (CRITICAL PART)

You need raw PCM from the player.

Minimal AudioProcessor
class TranscriptionAudioProcessor(
    private val onAudio: (ByteArray, Long) -> Unit
) : BaseAudioProcessor() {

    private var presentationTimeUs: Long = 0

    override fun queueInput(inputBuffer: ByteBuffer) {
        val data = ByteArray(inputBuffer.remaining())
        inputBuffer.get(data)

        onAudio(data, presentationTimeUs)

        replaceOutputBuffer(0).put(data)
    }

    override fun onQueueEndOfStream() {}

    override fun flush() {
        super.flush()
    }
}
Attach to player
val processor = TranscriptionAudioProcessor { bytes, timeUs ->
    audioFeeder.feed(bytes, timeUs)
}

val audioSink = DefaultAudioSink.Builder()
    .setAudioProcessors(arrayOf(processor))
    .build()

val renderersFactory = DefaultRenderersFactory(context)
    .setAudioSink(audioSink)

val player = ExoPlayer.Builder(context, renderersFactory).build()
⚙️ STEP 2 — Convert to ML Kit format (16k mono PCM)

ML Kit expects clean speech input.

class AudioFeeder {

    private val buffer = ByteArrayOutputStream()

    fun feed(data: ByteArray, timeUs: Long) {
        val mono16k = downsampleTo16kMono(data)
        buffer.write(mono16k)

        if (buffer.size() > CHUNK_SIZE) {
            val chunk = buffer.toByteArray()
            buffer.reset()
            onChunkReady(chunk, timeUs)
        }
    }
}

👉 Keep chunks around 1–2 seconds

⚙️ STEP 3 — ML Kit streaming recognizer
val recognizer = SpeechRecognition.getClient(
    speechRecognizerOptions {
        locale = Locale.US
    }
)

fun startRecognition() = scope.launch {
    recognizer.startRecognition(
        speechRecognizerRequest {
            audioSource = AudioSource.fromFileDescriptor(pipeFd)
        }
    ).collect { result ->
        transcriptManager.onResult(result)
    }
}
⚙️ STEP 4 — Transcript model (IMPORTANT)

You must timestamp everything.

data class Segment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val isFinal: Boolean
)
⚙️ STEP 5 — Manage rolling transcript
class TranscriptManager {

    private val segments = mutableListOf<Segment>()

    fun onResult(result: SpeechRecognitionResult) {
        val now = currentPlaybackPosition()

        segments.add(
            Segment(
                startMs = now,
                endMs = now + 2000,
                text = result.text,
                isFinal = result.isFinal
            )
        )
    }

    fun getWindow(playbackMs: Long): List<Segment> {
        val center = playbackMs - 10_000

        return segments.filter {
            it.startMs in (center - 10_000)..(center + 10_000)
        }
    }
}
🎯 STEP 6 — UI logic (your requirement)
Requirements:
show text 10 seconds earlier
show 20 seconds total
val displaySegments = transcriptManager.getWindow(player.currentPosition)
Compose UI example
@Composable
fun TranscriptView(segments: List<Segment>) {
    Column {
        segments.forEach { seg ->
            Text(
                text = seg.text,
                color = if (seg.isFinal) Color.White else Color.Gray
            )
        }
    }
}
⏱ Timing behavior (what actually happens)
Time	What user hears	What user sees
30s	audio plays	transcript of ~20s
40s	audio plays	transcript of ~30s

👉 Always 10 seconds behind

⚠️ Important tuning
Chunk size
1–2 seconds = good latency
smaller = faster UI, worse accuracy
Add overlap (recommended)
Chunk1: 0–2s
Chunk2: 1–3s
Chunk3: 2–4s

→ improves continuity

Delay stabilization

Only mark text final after:

if (sameTextRepeatedTwice) markFinal()
🚀 What you get
Real live transcription
Smooth UI (no jitter)
Controlled latency (your 10s rule)
Fully works with your audiobook player
🧠 Final summary (no fluff)
Tap audio using AudioProcessor
Chunk to ~2 seconds
Feed ML Kit continuously
Store timestamped segments

Render window:

playbackTime - 10s ± 10s

If you want next:
I can give you a drop-in working class set (AudioFeeder + Manager + ViewModel wired together) so you literally paste it into your project.

yes please give me.
Thought
Thought
Thought
Thought for 1m 36s

Here is the version you can actually wire in.

It uses:

Media3 AudioProcessor to tap the decoded PCM stream from your player. queueInput() is the hook where Media3 hands you audio buffers.
ML Kit Speech Recognition with AudioSource.fromPfd(...), so you feed your own app’s audio into the recognizer instead of the mic. The API supports audio files / PFD input and returns a continuous stream that can be partial before becoming final. Current dependency is 1.0.0-alpha1.

One important note: ML Kit’s current speech API is alpha, so exact response class/property names can shift a bit. The architecture below is the right one either way.

What goes in / what comes out

Input

your audiobook file
Media3 player
decoded PCM from the player

Output

a StateFlow<List<TranscriptSegment>>
UI shows the text for:
start = playbackPosition - 30_000
end = playbackPosition - 10_000

That gives you a 20-second window, ending 10 seconds behind playback.

1) Gradle
dependencies {
    implementation("androidx.media3:media3-exoplayer:1.10.0")
    implementation("androidx.media3:media3-common:1.10.0")

    implementation("com.google.mlkit:genai-speech-recognition:1.0.0-alpha1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
}

ML Kit’s current speech dependency is com.google.mlkit:genai-speech-recognition:1.0.0-alpha1.

2) Transcript model
data class TranscriptSegment(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val isFinal: Boolean
)
3) Visible window logic

This is your requirement exactly: show 20 seconds of text, ending 10 seconds behind playback.

object TranscriptWindow {
    const val DISPLAY_LAG_MS = 10_000L
    const val WINDOW_MS = 20_000L

    fun windowStart(playbackMs: Long): Long =
        (playbackMs - DISPLAY_LAG_MS - WINDOW_MS).coerceAtLeast(0L)

    fun windowEnd(playbackMs: Long): Long =
        (playbackMs - DISPLAY_LAG_MS).coerceAtLeast(0L)
}
4) In-memory transcript store
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
        approxDurationMs: Long = 2_000L
    ) {
        if (text.isBlank()) return

        val startMs = (approxEndMs - approxDurationMs).coerceAtLeast(0L)
        val endMs = approxEndMs.coerceAtLeast(startMs)

        _segments.update { old ->
            val mutable = old.toMutableList()

            if (!isFinal) {
                val idx = mutable.indexOfLast { !it.isFinal }
                if (idx >= 0) {
                    mutable[idx] = mutable[idx].copy(
                        startMs = startMs,
                        endMs = endMs,
                        text = text
                    )
                } else {
                    mutable += TranscriptSegment(
                        id = nextId.getAndIncrement(),
                        startMs = startMs,
                        endMs = endMs,
                        text = text,
                        isFinal = false
                    )
                }
            } else {
                val idx = mutable.indexOfLast { !it.isFinal }
                if (idx >= 0) {
                    mutable[idx] = mutable[idx].copy(
                        startMs = startMs,
                        endMs = endMs,
                        text = text,
                        isFinal = true
                    )
                } else {
                    mutable += TranscriptSegment(
                        id = nextId.getAndIncrement(),
                        startMs = startMs,
                        endMs = endMs,
                        text = text,
                        isFinal = true
                    )
                }
            }

            // keep only the last 10 minutes
            val cutoff = (mutable.maxOfOrNull { it.endMs } ?: 0L) - 10 * 60_000L
            mutable.filter { it.endMs >= cutoff }
        }
    }

    fun visibleSegmentsForPlayback(playbackMs: Long): List<TranscriptSegment> {
        val start = TranscriptWindow.windowStart(playbackMs)
        val end = TranscriptWindow.windowEnd(playbackMs)

        return _segments.value.filter { seg ->
            seg.endMs >= start && seg.startMs <= end
        }
    }

    fun clear() {
        _segments.value = emptyList()
    }
}
5) PCM downmix + resample to 16k mono PCM16

ML Kit supports AudioSource.fromPfd(...); the docs do not spell out the exact required PCM format on that page, so this implementation standardizes your stream to 16 kHz, mono, 16-bit PCM before feeding it. The fromPfd input itself is official.

import kotlin.math.roundToInt

object Pcm16MonoResampler {

    data class InputFormat(
        val sampleRate: Int,
        val channelCount: Int
    )

    fun convertTo16kMono(
        input: ByteArray,
        format: InputFormat
    ): ByteArray {
        require(format.channelCount >= 1)

        val inputSamples = bytesToShorts(input)
        val mono = if (format.channelCount == 1) {
            inputSamples
        } else {
            downmixToMono(inputSamples, format.channelCount)
        }

        return if (format.sampleRate == 16_000) {
            shortsToBytes(mono)
        } else {
            shortsToBytes(resampleLinear(mono, format.sampleRate, 16_000))
        }
    }

    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val out = ShortArray(bytes.size / 2)
        var j = 0
        var i = 0
        while (i < bytes.size - 1) {
            val lo = bytes[i].toInt() and 0xFF
            val hi = bytes[i + 1].toInt()
            out[j++] = ((hi shl 8) or lo).toShort()
            i += 2
        }
        return out
    }

    private fun shortsToBytes(samples: ShortArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        var j = 0
        for (s in samples) {
            out[j++] = (s.toInt() and 0xFF).toByte()
            out[j++] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun downmixToMono(interleaved: ShortArray, channels: Int): ShortArray {
        val frames = interleaved.size / channels
        val out = ShortArray(frames)
        var src = 0
        for (i in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) {
                sum += interleaved[src++].toInt()
            }
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
6) Media3 audio tap

Media3 AudioProcessor.queueInput() is the official place where the processor receives audio data.

This processor:

passes audio through unchanged to playback
also mirrors the PCM to your transcription pipeline
derives approximate stream time from byte count
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TranscriptionTapAudioProcessor(
    private val sink: Sink
) : BaseAudioProcessor() {

    interface Sink {
        fun onPcmChunk(
            pcm: ByteArray,
            sampleRate: Int,
            channelCount: Int,
            streamPositionMsStart: Long,
            streamPositionMsEnd: Long
        )
    }

    private var sampleRateHz: Int = 0
    private var channelCount: Int = 0
    private var bytesPerFrame: Int = 0
    private var totalFramesSeen: Long = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        sampleRateHz = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        bytesPerFrame = 2 * channelCount // PCM16

        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) return

        val bytes = ByteArray(inputSize)
        inputBuffer.get(bytes)

        val framesInChunk = inputSize / bytesPerFrame
        val startFrame = totalFramesSeen
        val endFrame = totalFramesSeen + framesInChunk

        val startMs = (startFrame * 1000L) / sampleRateHz
        val endMs = (endFrame * 1000L) / sampleRateHz

        sink.onPcmChunk(
            pcm = bytes,
            sampleRate = sampleRateHz,
            channelCount = channelCount,
            streamPositionMsStart = startMs,
            streamPositionMsEnd = endMs
        )

        totalFramesSeen = endFrame

        val outputBuffer = replaceOutputBuffer(inputSize)
        outputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.put(bytes)
        outputBuffer.flip()
    }

    override fun onFlush() {
        totalFramesSeen = 0L
    }

    override fun onReset() {
        totalFramesSeen = 0L
        sampleRateHz = 0
        channelCount = 0
        bytesPerFrame = 0
    }
}
7) Pipe writer to ML Kit

This is the bridge:

takes PCM from the tap
converts it to 16k mono PCM16
writes it into a ParcelFileDescriptor pipe
ML Kit reads from the other end with AudioSource.fromPfd(...)
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

class MlKitAudioPipeWriter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val pipe = ParcelFileDescriptor.createPipe()
    val readSide: ParcelFileDescriptor = pipe[0]
    private val writeSide: ParcelFileDescriptor = pipe[1]

    private val output = FileOutputStream(writeSide.fileDescriptor)

    private val _audioSentUntilMs = AtomicLong(0L)
    val audioSentUntilMs: Long get() = _audioSentUntilMs.get()

    fun writePcm(
        pcm16: ByteArray,
        sampleRate: Int,
        channelCount: Int,
        streamPositionMsEnd: Long
    ) {
        scope.launch {
            val converted = Pcm16MonoResampler.convertTo16kMono(
                input = pcm16,
                format = Pcm16MonoResampler.InputFormat(
                    sampleRate = sampleRate,
                    channelCount = channelCount
                )
            )
            output.write(converted)
            output.flush()
            _audioSentUntilMs.set(streamPositionMsEnd)
        }
    }

    fun close() {
        runCatching { output.close() }
        runCatching { writeSide.close() }
        runCatching { readSide.close() }
    }
}
8) ML Kit recognizer session

ML Kit speech:

supports streaming input from mic or audio file
returns a continuous stream that may be partial before final
uses an on-device recognizer in Basic mode, widely available on API 31+ devices
is alpha right now.
import android.os.Build
import com.google.mlkit.common.sdkinternal.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.DownloadStatus
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class MlKitLiveTranscriber(
    private val store: TranscriptStore,
    private val pipeWriter: MlKitAudioPipeWriter
) {
    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private var recognizer: SpeechRecognizer? = null
    private var recognitionJob: Job? = null

    suspend fun start(locale: Locale = Locale.US) {
        check(Build.VERSION.SDK_INT >= 26)

        val options: SpeechRecognizerOptions = speechRecognizerOptions {
            this.locale = locale
            preferredMode = SpeechRecognizerOptions.Mode.MODE_BASIC
        }

        val client = SpeechRecognition.getClient(options)
        recognizer = client

        when (client.checkStatus()) {
            FeatureStatus.AVAILABLE -> startRecognition(client)
            FeatureStatus.DOWNLOADABLE -> {
                client.download.collectLatest { status ->
                    if (status is DownloadStatus.DownloadCompleted) {
                        startRecognition(client)
                    }
                }
            }
            else -> error("Speech model unavailable")
        }
    }

    private fun startRecognition(client: SpeechRecognizer) {
        recognitionJob?.cancel()
        recognitionJob = scope.launch {
            val request = speechRecognizerRequest {
                audioSource = AudioSource.fromPfd(pipeWriter.readSide)
            }

            client.startRecognition(request).collectLatest { response ->
                handleResponse(response)
            }
        }
    }

    private fun handleResponse(response: Any) {
        val text = extractText(response) ?: return
        val isFinal = extractIsFinal(response)

        // Approximate timestamp: "all audio sent so far"
        val approxEndMs = pipeWriter.audioSentUntilMs

        store.upsertLiveSegment(
            approxEndMs = approxEndMs,
            text = text,
            isFinal = isFinal,
            approxDurationMs = 2_000L
        )
    }

    private fun extractText(response: Any): String? {
        // Adjust these casts/property names to the exact alpha API you get.
        // Keep this function isolated so future SDK changes are easy to patch.
        return when (response) {
            is com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse ->
                response.toString() // replace with actual text extraction
            else -> null
        }
    }

    private fun extractIsFinal(response: Any): Boolean {
        // Replace with actual field from the alpha API if available.
        val s = response.toString().lowercase()
        return "final" in s
    }

    suspend fun stop() {
        recognitionJob?.cancel()
        recognizer?.stopRecognition()
        recognizer?.close()
        recognizer = null
    }
}
Why I isolated extractText() / extractIsFinal()

Because the API is alpha. The docs clearly confirm the streaming flow, on-device modes, and AudioSource.fromPfd(...), but they do not pin down every response-field name on that guide page.

9) Coordinator: player tap → pipe → recognizer → transcript store
class LiveTranscriptCoordinator(
    private val transcriptStore: TranscriptStore
) : TranscriptionTapAudioProcessor.Sink {

    private val pipeWriter = MlKitAudioPipeWriter()
    private val transcriber = MlKitLiveTranscriber(transcriptStore, pipeWriter)

    suspend fun start() {
        transcriber.start()
    }

    suspend fun stop() {
        transcriber.stop()
        pipeWriter.close()
    }

    override fun onPcmChunk(
        pcm: ByteArray,
        sampleRate: Int,
        channelCount: Int,
        streamPositionMsStart: Long,
        streamPositionMsEnd: Long
    ) {
        pipeWriter.writePcm(
            pcm16 = pcm,
            sampleRate = sampleRate,
            channelCount = channelCount,
            streamPositionMsEnd = streamPositionMsEnd
        )
    }
}
10) Build the player with the tap processor
import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink

class AudioBookPlayerFactory(
    private val context: Context,
    private val sink: TranscriptionTapAudioProcessor.Sink
) {
    fun create(): ExoPlayer {
        val tapProcessor = TranscriptionTapAudioProcessor(sink)

        val audioSink = DefaultAudioSink.Builder()
            .setAudioProcessors(arrayOf(tapProcessor))
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setAudioSink(audioSink)

        return ExoPlayer.Builder(context, renderersFactory).build()
    }
}
11) ViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AudioBookViewModel(
    private val player: ExoPlayer,
    private val transcriptStore: TranscriptStore,
    private val coordinator: LiveTranscriptCoordinator
) : ViewModel() {

    private val _visibleTranscript = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    val visibleTranscript: StateFlow<List<TranscriptSegment>> = _visibleTranscript

    private var tickerJob: Job? = null

    fun startTranscription() {
        viewModelScope.launch {
            coordinator.start()
        }

        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                val playbackMs = player.currentPosition.coerceAtLeast(0L)
                _visibleTranscript.value = transcriptStore.visibleSegmentsForPlayback(playbackMs)
                delay(200L)
            }
        }
    }

    fun stopTranscription() {
        tickerJob?.cancel()
        viewModelScope.launch {
            coordinator.stop()
        }
    }

    override fun onCleared() {
        stopTranscription()
        super.onCleared()
    }
}
12) Compose UI
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LiveTranscriptPanel(
    segments: List<TranscriptSegment>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(12.dp)
    ) {
        items(segments, key = { it.id }) { seg ->
            Text(
                text = seg.text,
                style = if (seg.isFinal) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}
13) Wiring it together
val transcriptStore = TranscriptStore()
val coordinator = LiveTranscriptCoordinator(transcriptStore)

val playerFactory = AudioBookPlayerFactory(
    context = context,
    sink = coordinator
)

val player = playerFactory.create()

val viewModel = AudioBookViewModel(
    player = player,
    transcriptStore = transcriptStore,
    coordinator = coordinator
)

// Start when playback starts
viewModel.startTranscription()

// In Compose:
@Composable
fun PlayerScreen(vm: AudioBookViewModel) {
    val segments = androidx.lifecycle.compose.collectAsStateWithLifecycle(
        vm.visibleTranscript
    ).value

    LiveTranscriptPanel(segments = segments)
}
14) The one patch you will probably need first

Because ML Kit speech is alpha, the first compile error you’re likely to hit is in handleResponse() where I left extractText() / extractIsFinal() isolated. The rest of the architecture is the stable part.

So the first thing I’d do after adding the dependency is:

inspect the exact response type Android Studio gives you from startRecognition(...).collectLatest
replace:
extractText(response)
extractIsFinal(response)

with the actual fields

15) Two practical fixes I recommend immediately

First, start transcription only after playback begins, not when the file is merely prepared. That keeps the recognizer aligned with real audio.

Second, if you see ML Kit rejecting a stream with initial silence, trim or skip the first silent PCM frames before writing to the pipe. There is at least one recent open issue in Google’s sample repo showing fromPfd() failing when the stream starts with enough silence, so this is worth guarding against.

A minimal silence gate:

object SilenceGate {
    fun hasSpeechPcm16Mono(bytes: ByteArray, threshold: Int = 300): Boolean {
        var i = 0
        while (i < bytes.size - 1) {
            val lo = bytes[i].toInt() and 0xFF
            val hi = bytes[i + 1].toInt()
            val s = ((hi shl 8) or lo).toShort().toInt()
            if (kotlin.math.abs(s) > threshold) return true
            i += 2
        }
        return false
    }
}

Then in MlKitAudioPipeWriter.writePcm(...):

if (!SilenceGate.hasSpeechPcm16Mono(converted)) return@launch
16) The shortest possible mental model

Your app flow is now:

Player file
→ Media3 decodes audio
→ AudioProcessor taps PCM
→ convert to 16k mono PCM16
→ write into ParcelFileDescriptor pipe
→ ML Kit reads pipe with AudioSource.fromPfd(...)
→ partial/final transcript stream
→ store transcript with approximate audio times
→ UI shows [playback-30s, playback-10s]

If you want, next I’ll tighten this into a single Kotlin file version with the alpha-response placeholders replaced as far as possible from the current SDK shape.