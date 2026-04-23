# Plan: Audiobook Live Transcription

## Context

Add real-time on-device transcription to the EPUB3 full-screen audio player.
When the user taps a new button, the book cover area is replaced by a
**teleprompter-style transcript panel** that shows text 10–30 seconds AHEAD of
current playback — the user reads words before hearing them.

This is folder-mode only (`AudiobookFolderController`), separate from SMIL
media overlays (which already have synchronized text).

ASR: ML Kit `genai-speech-recognition` (alpha) Basic mode.
- English (en-US) only in this version
- Requires API 31+ — graceful error on older devices (button visible, snackbar on tap)
- `AudioSource.fromPfd()` confirmed in official Google docs

---

## User Experience (Teleprompter Style)

```
┌─────────────────────────────────────────────┐
│                                             │
│  …and so the captain turned to face         │  ← +30s from now (furthest ahead)
│  the horizon, wondering if the crew         │
│  would survive the storm that was           │
│  clearly building in the west. He           │
│                                             │
│ ─────────────── NEXT UP ─────────────────── │  ← subtle divider / visual marker
│                                             │
│  called out to the first mate, urging       │  ← +10s from now (soonest)
│  him to secure the rigging before           │
│  the wind picked up.                        │
│                                             │
└─────────────────────────────────────────────┘
```

- Window displayed: [playback + 10s … playback + 30s] = 20 seconds of upcoming text
- Text scrolls **upward** as audio plays: bottom text gets heard, drops out; new text enters at top
- A thin "NEXT UP" divider (or faint horizontal rule + label) marks the +10s boundary
- Text below the divider = imminent (heard in ~10s), text above = further ahead (heard in 10–30s)
- No "currently playing" indicator — everything shown is future text
- In-progress (partial) ML Kit results shown in slightly muted/gray style until finalized

---

## Architecture Overview

The transcription logic is a **separate, self-contained Gradle module**:
`komelia-infra:audiobook-transcription`

This module has zero Komelia-specific dependencies and can be extracted to its own
repository at any time. Its only external deps are `media3-common` (for audio decoding),
`mlkit-genai-speech-recognition`, and `kotlinx.coroutines`.

```
[AudiobookFolderController]  (komelia-ui)
  passes: List<AudioTranscriptTrack>, () -> Long (getPlaybackMs)
  ↓
[LiveTranscriptEngine]  ← only public API  (komelia-infra:audiobook-transcription)
  │
  ├── [AudioPreReader]
  │     MediaExtractor + MediaCodec
  │     Reads audio starting from playbackMs + 10_000
  │     Pauses when readHead > playbackMs + 30_000
  │     Resets readHead = newPos + 10_000 on seek
  │     ↓ PcmChunk(bytes, sampleRate, channels, bookTimeMs, durationMs)
  │
  ├── [Pcm16MonoResampler]
  │     Converts any PCM16 format → 16kHz mono PCM16
  │     ↓
  ├── [MlKitAudioPipeWriter]
  │     Channel-based; writes to ParcelFileDescriptor write-end
  │     ↓
  ├── [MlKitLiveTranscriber]
  │     AudioSource.fromPfd(readSide) → ML Kit collectLatest
  │     Updates TranscriptStore with timestamped partial/final segments
  │     ↓
  └── [TranscriptStore]
        StateFlow<List<TranscriptSegment>>
        visibleSegmentsForPlayback(ms) → [ms+10_000, ms+30_000]

[AudioFullScreenPlayer]  (komelia-ui)
  collectAsState on visibleSegments
  AnimatedContent: cover ↔ transcript panel
```

**No changes to `epub-reader` module.** AudiobookPlayer.kt is untouched.
No AudioProcessor tap needed — we pre-read the file independently.

---

## Public API of the New Module

```kotlin
// komelia-infra/audiobook-transcription/

data class AudioTranscriptTrack(
    val uri: Uri,
    val bookOffsetMs: Long,   // cumulative start of this file in the book
    val durationMs: Long
)

data class TranscriptSegment(
    val id: Long,
    val startMs: Long,        // book time (ms) when this text starts
    val endMs: Long,
    val text: String,
    val isFinal: Boolean
)

sealed interface TranscriptEngineState {
    data object Idle : TranscriptEngineState
    data class Downloading(val progress: Float?) : TranscriptEngineState
    data object Active : TranscriptEngineState
    data class Error(val message: String) : TranscriptEngineState
    data object UnsupportedDevice : TranscriptEngineState  // API < 31
}

class LiveTranscriptEngine(
    context: Context,
    tracks: List<AudioTranscriptTrack>,
    getPlaybackMs: () -> Long,
    scope: CoroutineScope,
) {
    val state: StateFlow<TranscriptEngineState>
    val visibleSegments: StateFlow<List<TranscriptSegment>>

    fun start()
    fun stop()
    fun onPlaybackSeeked(newPositionMs: Long)
}
```

---

## Critical Notes on ChatGPT's Plan

**Fundamental flaw**: ChatGPT used `BaseAudioProcessor` to tap audio at render time,
showing text 10 seconds BEHIND playback. The user wants text AHEAD. We use a
pre-reader (MediaExtractor + MediaCodec) instead. No AudioProcessor needed.

**What ChatGPT got right** (code included verbatim below):
- `TranscriptStore` upsert logic for partial/final segments
- `Pcm16MonoResampler` downsample/downmix implementation
- `ParcelFileDescriptor.createPipe()` + `AudioSource.fromPfd()` (confirmed in docs)
- Rolling transcript window concept

**What we fix**:
- Per-call coroutine in `writePcm()` → Channel (avoids unbounded coroutine creation)
- `extractText()`/`extractIsFinal()` stubs → must resolve against real alpha API at compile time
- Window direction: [playbackMs + 10_000, playbackMs + 30_000] instead of ChatGPT's backward window
- Model download UX via `TranscriptEngineState` flow
- Timestamps now come from pre-reader (absolute book time), not frame counter

---

## Implementation Tasks

### Task 1: Save spec documentation
Create `agent-os/specs/2026-04-22-1200-audiobook-live-transcription/` with plan.md, shape.md,
standards.md (relevant: compose-ui/view-models, compose-ui/dependency-injection), references.md.

### Task 2: Scaffold new Gradle module

Create directory: `komelia-infra/audiobook-transcription/src/main/java/snd/komelia/transcription/`

Create `komelia-infra/audiobook-transcription/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "snd.komelia.transcription"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}

dependencies {
    implementation("androidx.media3:media3-common:1.9.0")
    implementation("com.google.mlkit:genai-speech-recognition:1.0.0-alpha1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
```

In `settings.gradle.kts` add:
```kotlin
include(":komelia-infra:audiobook-transcription")
```

In `gradle/libs.versions.toml` add to [libraries]:
```toml
mlkit-genai-speech = { module = "com.google.mlkit:genai-speech-recognition", version = "1.0.0-alpha1" }
```

In `komelia-ui/build.gradle.kts` (androidMain dependencies):
```kotlin
implementation(project(":komelia-infra:audiobook-transcription"))
```

### Task 3: Public API data classes

`AudioTranscriptTrack.kt`, `TranscriptSegment.kt`, `TranscriptEngineState.kt` — as defined
in the Public API section above.

### Task 4: TranscriptStore

```kotlin
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

            // keep last 60 minutes (audiobooks can be long sessions)
            val cutoff = (mutable.maxOfOrNull { it.endMs } ?: 0L) - 60 * 60_000L
            mutable.filter { it.endMs >= cutoff }
        }
    }

    // Returns segments in the window [playbackMs+10s, playbackMs+30s]
    fun visibleSegmentsForPlayback(playbackMs: Long): List<TranscriptSegment> {
        val windowStart = (playbackMs + 10_000L).coerceAtLeast(0L)
        val windowEnd   = (playbackMs + 30_000L).coerceAtLeast(0L)
        return _segments.value.filter { seg -> seg.endMs >= windowStart && seg.startMs <= windowEnd }
    }

    fun clear() { _segments.value = emptyList() }
}
```

### Task 5: Pcm16MonoResampler

```kotlin
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
            val left  = srcPos.toInt().coerceIn(0, input.lastIndex)
            val right = (left + 1).coerceIn(0, input.lastIndex)
            val frac  = srcPos - left
            val sample = input[left] * (1.0 - frac) + input[right] * frac
            out[i] = sample.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }
}
```

### Task 6: SilenceGate

```kotlin
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
```

### Task 7: MlKitAudioPipeWriter

```kotlin
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
            for ((bytes, bookMs) in writeChannel) {
                output.write(bytes)
                output.flush()
                _audioSentUntilMs.set(bookMs)
            }
        }
    }

    fun writePcm(pcm: ByteArray, sampleRate: Int, channelCount: Int, bookTimeMs: Long) {
        val converted = Pcm16MonoResampler.convertTo16kMono(
            pcm, Pcm16MonoResampler.InputFormat(sampleRate, channelCount)
        )
        if (SilenceGate.hasSpeech(converted)) {
            writeChannel.trySend(Pair(converted, bookTimeMs))
        }
    }

    fun close() {
        writeChannel.close()
        runCatching { output.close() }
        runCatching { writeSide.close() }
        runCatching { readSide.close() }
    }
}
```

### Task 8: MlKitLiveTranscriber

```kotlin
class MlKitLiveTranscriber(
    private val store: TranscriptStore,
    private val pipeWriter: MlKitAudioPipeWriter,
    scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<TranscriptEngineState>(TranscriptEngineState.Idle)
    val state: StateFlow<TranscriptEngineState> = _state

    private val innerScope = CoroutineScope(scope.coroutineContext + SupervisorJob())
    private var recognizer: Any? = null  // SpeechRecognizer — typed as Any for API-level guarding

    suspend fun start(locale: Locale = Locale.US) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            _state.value = TranscriptEngineState.UnsupportedDevice
            return
        }

        // NOTE: The following code requires the mlkit-genai-speech-recognition alpha SDK.
        // Field names (SpeechRecognizerResponse.text, .isFinal) must be verified against
        // the actual SDK at compile time — they are isolated in parseText() / parseIsFinal().

        val options = speechRecognizerOptions { this.locale = locale; preferredMode = SpeechRecognizerOptions.Mode.MODE_BASIC }
        val client = SpeechRecognition.getClient(options)
        recognizer = client

        when (client.checkStatus()) {
            FeatureStatus.AVAILABLE -> startRecognition(client)
            FeatureStatus.DOWNLOADABLE -> {
                _state.value = TranscriptEngineState.Downloading(null)
                client.download.collectLatest { status ->
                    when (status) {
                        is DownloadStatus.DownloadCompleted -> startRecognition(client)
                        is DownloadStatus.Downloading -> _state.value = TranscriptEngineState.Downloading(status.progress?.toFloat())
                        is DownloadStatus.DownloadFailed -> _state.value = TranscriptEngineState.Error("Model download failed")
                        else -> {}
                    }
                }
            }
            else -> _state.value = TranscriptEngineState.UnsupportedDevice
        }
    }

    private fun startRecognition(client: SpeechRecognizer) {
        _state.value = TranscriptEngineState.Active
        innerScope.launch {
            val request = speechRecognizerRequest { audioSource = AudioSource.fromPfd(pipeWriter.readSide) }
            client.startRecognition(request).collectLatest { response ->
                val text = parseText(response) ?: return@collectLatest
                val isFinal = parseIsFinal(response)
                store.upsertLiveSegment(
                    approxEndMs = pipeWriter.audioSentUntilMs,
                    text = text,
                    isFinal = isFinal,
                    approxDurationMs = 2_000L
                )
            }
        }
    }

    // IMPORTANT: These two functions use alpha API fields.
    // At compile time, inspect SpeechRecognizerResponse to get actual property names.
    // Isolating them here means only these two lines change when the SDK stabilizes.
    private fun parseText(response: Any): String? =
        (response as? com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse)
            ?.text  // <-- verify field name at compile time
            ?.takeIf { it.isNotBlank() }

    private fun parseIsFinal(response: Any): Boolean =
        (response as? com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse)
            ?.isFinal ?: false  // <-- verify field name at compile time

    fun stop() {
        innerScope.cancel()
        runCatching { (recognizer as? SpeechRecognizer)?.close() }
        recognizer = null
        _state.value = TranscriptEngineState.Idle
    }
}
```

### Task 9: AudioPreReader

Uses Android's `MediaExtractor` + `MediaCodec` to decode audio ahead of playback.
This is the key new component not derived from ChatGPT.

```kotlin
data class PcmChunk(
    val bytes: ByteArray,
    val sampleRate: Int,
    val channelCount: Int,
    val bookTimeMs: Long,   // absolute position in the book
    val durationMs: Long
)

class AudioPreReader(
    private val context: Context,
    private val tracks: List<AudioTranscriptTrack>,
    private val getPlaybackMs: () -> Long,
) {
    companion object {
        const val PRE_READ_AHEAD_MS = 10_000L
        const val MAX_AHEAD_MS = 30_000L
        const val CHUNK_TARGET_MS = 2_000L
    }

    @Volatile private var readHeadMs: Long = 0L
    @Volatile private var cancelled = false

    suspend fun run(onChunk: (PcmChunk) -> Unit) {
        cancelled = false
        readHeadMs = getPlaybackMs() + PRE_READ_AHEAD_MS

        while (!cancelled) {
            val playbackMs = getPlaybackMs()

            when {
                readHeadMs > playbackMs + MAX_AHEAD_MS -> {
                    // We're far enough ahead — wait for playback to catch up
                    delay(500)
                }
                readHeadMs < playbackMs + PRE_READ_AHEAD_MS -> {
                    // Playback jumped past our read head (seek) — reset
                    readHeadMs = playbackMs + PRE_READ_AHEAD_MS
                }
                else -> {
                    val chunk = withContext(Dispatchers.IO) {
                        decodeChunkAt(readHeadMs, CHUNK_TARGET_MS)
                    }
                    if (chunk == null) {
                        delay(1000)  // end of content or decode error — back off
                    } else {
                        onChunk(chunk)
                        readHeadMs += chunk.durationMs
                    }
                }
            }
        }
    }

    fun reset(newBookMs: Long) {
        readHeadMs = newBookMs + PRE_READ_AHEAD_MS
    }

    fun cancel() { cancelled = true }

    private fun decodeChunkAt(bookMs: Long, maxMs: Long): PcmChunk? {
        // Find which track covers bookMs
        val track = tracks.lastOrNull { it.bookOffsetMs <= bookMs } ?: return null
        val inFileMs = bookMs - track.bookOffsetMs
        if (inFileMs >= track.durationMs) return null  // past end of this track

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, track.uri, null)

            // Find the audio track index
            val audioTrackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            extractor.seekTo(inFileMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmBuffer = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var decodedMs = 0L
            var eos = false

            try {
                while (decodedMs < maxMs && !eos) {
                    // Feed compressed input
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eos = true
                        } else {
                            val pts = extractor.sampleTime
                            codec.queueInputBuffer(inIdx, 0, size, pts, 0)
                            extractor.advance()
                        }
                    }

                    // Drain decoded output
                    val outIdx = codec.dequeueOutputBuffer(info, 10_000L)
                    if (outIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)!!
                        val chunk = ByteArray(info.size)
                        outBuf.get(chunk)
                        pcmBuffer.write(chunk)
                        codec.releaseOutputBuffer(outIdx, false)

                        // Estimate decoded duration from byte count
                        val bytesPerMs = (sampleRate * channelCount * 2) / 1000
                        decodedMs += if (bytesPerMs > 0) (info.size / bytesPerMs).toLong() else 0L

                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eos = true
                    }
                }
            } finally {
                codec.stop()
                codec.release()
            }

            val pcm = pcmBuffer.toByteArray()
            if (pcm.isEmpty()) return null

            return PcmChunk(
                bytes = pcm,
                sampleRate = sampleRate,
                channelCount = channelCount,
                bookTimeMs = bookMs,
                durationMs = decodedMs
            )
        } finally {
            extractor.release()
        }
    }
}
```

### Task 10: LiveTranscriptEngine

```kotlin
class LiveTranscriptEngine(
    private val context: Context,
    private val tracks: List<AudioTranscriptTrack>,
    private val getPlaybackMs: () -> Long,
    private val scope: CoroutineScope,
) {
    private val store = TranscriptStore()
    private val pipeWriter = MlKitAudioPipeWriter(scope)
    private val transcriber = MlKitLiveTranscriber(store, pipeWriter, scope)
    private val preReader = AudioPreReader(context, tracks, getPlaybackMs)

    val state: StateFlow<TranscriptEngineState> = transcriber.state

    private val _visibleSegments = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    val visibleSegments: StateFlow<List<TranscriptSegment>> = _visibleSegments

    private var preReaderJob: Job? = null
    private var tickerJob: Job? = null

    fun start() {
        scope.launch { transcriber.start() }

        preReaderJob = scope.launch {
            preReader.run { chunk ->
                pipeWriter.writePcm(chunk.bytes, chunk.sampleRate, chunk.channelCount, chunk.bookTimeMs)
            }
        }

        tickerJob = scope.launch {
            while (isActive) {
                _visibleSegments.value = store.visibleSegmentsForPlayback(getPlaybackMs())
                delay(200)
            }
        }
    }

    fun stop() {
        preReader.cancel()
        preReaderJob?.cancel()
        tickerJob?.cancel()
        transcriber.stop()
        pipeWriter.close()
        _visibleSegments.value = emptyList()
    }

    fun onPlaybackSeeked(newPositionMs: Long) {
        // Reset pre-reader head to new position + 10s
        // Do NOT clear the store — already-transcribed future text at the new position is still valid
        preReader.reset(newPositionMs)
    }
}
```

### Task 11: EpubAudioController interface additions

In `komelia-ui/.../audio/EpubAudioController.kt`:
```kotlin
val transcriptState: StateFlow<TranscriptEngineState>? get() = null
val liveTranscriptSegments: StateFlow<List<TranscriptSegment>>? get() = null
fun startTranscription() {}
fun stopTranscription() {}
fun onTranscriptSeek(newPositionMs: Long) {}
```

Add import for `TranscriptEngineState` and `TranscriptSegment` from the new module.

### Task 12: AudiobookFolderController integration

- Add `private var transcriptEngine: LiveTranscriptEngine? = null`
- Add `private val _transcriptState = MutableStateFlow<TranscriptEngineState>(TranscriptEngineState.Idle)`
- Add `private val _liveTranscriptSegments = MutableStateFlow<List<TranscriptSegment>>(emptyList())`
- Override `transcriptState` and `liveTranscriptSegments` to return these flows

Bridge the existing track list to `AudioTranscriptTrack`:
```kotlin
private fun buildTranscriptTracks(): List<AudioTranscriptTrack> {
    return loadedTracks.mapIndexed { idx, track ->
        val offsetMs = _tracks.value.take(idx).sumOf { it.durationSeconds * 1000 }.toLong()
        AudioTranscriptTrack(
            uri = track.uri,
            bookOffsetMs = offsetMs,
            durationMs = (track.duration * 1000).toLong()
        )
    }
}
```

`startTranscription()`:
```kotlin
override fun startTranscription() {
    val engine = LiveTranscriptEngine(
        context = context,
        tracks = buildTranscriptTracks(),
        getPlaybackMs = { (_elapsedSeconds.value * 1000).toLong() },
        scope = coroutineScope,
    )
    transcriptEngine = engine

    coroutineScope.launch {
        engine.state.collect { _transcriptState.value = it }
    }
    coroutineScope.launch {
        engine.visibleSegments.collect { _liveTranscriptSegments.value = it }
    }

    engine.start()
}
```

`stopTranscription()`: call `transcriptEngine?.stop(); transcriptEngine = null`

`onTranscriptSeek(newPositionMs)`: call `transcriptEngine?.onPlaybackSeeked(newPositionMs)`

Also hook into existing seek methods:
- `seekToTrackPosition(index, positionSeconds)`: after seeking, call `onTranscriptSeek((elapsedSeconds.value * 1000).toLong())`
- `seekRelative(deltaSeconds)`: same

Call `stopTranscription()` inside `release()`.

`MediaOverlayController` does not override any transcript methods (keeps default null).

### Task 13: AudioFullScreenPlayer UI changes

New parameters (add after existing params):
```kotlin
transcriptState: TranscriptEngineState? = null,
transcriptSegments: List<TranscriptSegment> = emptyList(),
onTranscriptToggle: () -> Unit = {},
isTranscribing: Boolean = false,
```

**New toggle button** — place alongside the existing Info icon (`showMetadataDialog`) at the bottom:
```kotlin
IconButton(onClick = onTranscriptToggle) {
    Icon(
        imageVector = if (isTranscribing) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionOff,
        contentDescription = "Toggle transcript",
        tint = if (isTranscribing) accentColor else LocalContentColor.current,
    )
}
```

**Replace cover `Surface` section** with `AnimatedContent`:
```kotlin
AnimatedContent(
    targetState = isTranscribing,
    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 48.dp)
        .aspectRatio(1f),
    label = "cover-transcript"
) { showTranscript ->
    if (showTranscript) {
        TranscriptPanel(
            segments = transcriptSegments,
            state = transcriptState,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Surface(  /* existing cover code unchanged, keep sharedBounds modifier */
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .aspectRatio(1f)
                .sharedBounds(...),  // keep existing
        ) {
            ThumbnailImage(...)  // unchanged
        }
    }
}
```

**TranscriptPanel composable** (new private composable in AudioFullScreenPlayer.kt):
```kotlin
@Composable
private fun TranscriptPanel(
    segments: List<TranscriptSegment>,
    state: TranscriptEngineState?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state is TranscriptEngineState.Downloading ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Downloading transcription model…", style = MaterialTheme.typography.bodySmall)
                }

            state is TranscriptEngineState.Error ->
                Text(state.message, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.error)

            segments.isEmpty() ->
                Text("Listening…", style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

            else -> {
                // Teleprompter: bottom = soonest (+10s), top = furthest (+30s)
                // Sort ascending so earliest segment is at bottom
                val sorted = segments.sortedBy { it.startMs }
                val nearestIdx = sorted.lastIndex  // nearest = last in ascending order

                LazyColumn(
                    reverseLayout = true,  // bottom = first item = soonest segment
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(sorted, key = { _, seg -> seg.id }) { idx, seg ->
                        Column {
                            Text(
                                text = seg.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (seg.isFinal) Color.White
                                        else Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            // Show "NEXT UP" divider below the nearest segment
                            if (idx == nearestIdx) {
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                                Text(
                                    "NEXT UP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

### Task 14: Call site — Epub3ReaderContent.android.kt

Collect state and wire toggle:
```kotlin
val folderController = controller as? AudiobookFolderController
val transcriptSegments by (folderController?.liveTranscriptSegments ?: flowOf(emptyList()))
    .collectAsState(initial = emptyList())
val transcriptState by (folderController?.transcriptState ?: flowOf(null))
    .collectAsState(initial = null)

var isTranscribing by remember { mutableStateOf(false) }
val snackbarHostState = remember { SnackbarHostState() }
val scope = rememberCoroutineScope()

val onTranscriptToggle = {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        scope.launch {
            snackbarHostState.showSnackbar("Live transcription requires Android 12 or later")
        }
    } else if (isTranscribing) {
        isTranscribing = false
        controller.stopTranscription()
    } else {
        isTranscribing = true
        controller.startTranscription()
    }
}
```

Pass to `AudioFullScreenPlayer`:
```kotlin
transcriptState = transcriptState,
transcriptSegments = transcriptSegments,
onTranscriptToggle = onTranscriptToggle,
isTranscribing = isTranscribing,
```

---

## Key Files Summary

| File | Change |
|------|--------|
| `settings.gradle.kts` | Add `:komelia-infra:audiobook-transcription` |
| `gradle/libs.versions.toml` | Add `mlkit-genai-speech` library |
| `komelia-infra/audiobook-transcription/build.gradle.kts` | **New module** |
| `komelia-infra/audiobook-transcription/.../AudioTranscriptTrack.kt` | New |
| `komelia-infra/audiobook-transcription/.../TranscriptSegment.kt` | New |
| `komelia-infra/audiobook-transcription/.../TranscriptEngineState.kt` | New |
| `komelia-infra/audiobook-transcription/.../TranscriptStore.kt` | New (code above) |
| `komelia-infra/audiobook-transcription/.../Pcm16MonoResampler.kt` | New (code above) |
| `komelia-infra/audiobook-transcription/.../SilenceGate.kt` | New (code above) |
| `komelia-infra/audiobook-transcription/.../MlKitAudioPipeWriter.kt` | New (code above) |
| `komelia-infra/audiobook-transcription/.../MlKitLiveTranscriber.kt` | New (code above) |
| `komelia-infra/audiobook-transcription/.../AudioPreReader.kt` | New (code above) |
| `komelia-infra/audiobook-transcription/.../LiveTranscriptEngine.kt` | New (code above) |
| `komelia-ui/build.gradle.kts` | Add dep on new module |
| `komelia-ui/.../audio/EpubAudioController.kt` | Add 3 flows + 3 methods |
| `komelia-ui/.../audio/AudiobookFolderController.kt` | Integrate engine, bridge tracks |
| `komelia-ui/.../audio/AudioFullScreenPlayer.kt` | Toggle button + AnimatedContent + TranscriptPanel |
| `komelia-ui/.../audio/Epub3ReaderContent.android.kt` | State collection + toggle handler |

**No changes to `epub-reader` module.**

---

## Verification

1. `./gradlew :komelia-infra:audiobook-transcription:compileDebugKotlinAndroid` — no errors
2. Resolve `parseText()` / `parseIsFinal()` stubs against actual `SpeechRecognizerResponse` fields
3. `./gradlew :komelia-ui:compileDebugKotlinAndroid` — no errors
4. Install on API 31+ device
5. Open a folder-mode audiobook → open full-screen player → tap CC button
6. If model downloads: progress indicator → transitions to text
7. **UX check**: text appears ahead of audio. Say a word aloud to confirm you see it on screen before hearing it from the audiobook (~10s window)
8. Scroll through panel: NEXT UP divider visible, bottom = nearest text
9. Seek forward 2 minutes: pre-reader resets, new future text appears within ~10s
10. Tap CC button again: cover returns, engine stops, no lingering background work
11. On API < 31: snackbar appears, no crash

---

## Follow-up Beans (out of scope)

- Language selection dropdown in epub audio settings (15 languages in Basic mode)
- Extract `komelia-infra:audiobook-transcription` to its own git repository
- Auto-scroll / auto-highlight nearest upcoming line in the transcript panel
- Persist transcript session across app restarts (by book + chapter)
