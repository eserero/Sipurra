package snd.komelia.transcription

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}
private const val CHUNK_THRESHOLD_MS = 10_000L

class WhisperTranscriptionBackend(
    private val store: TranscriptStore,
    private val modelPath: String,
    private val language: String?,
    scope: CoroutineScope,
) : TranscriptionBackend {

    private val _state = MutableStateFlow<TranscriptEngineState>(TranscriptEngineState.Idle)
    override val state: StateFlow<TranscriptEngineState> = _state

    private val innerScope = CoroutineScope(scope.coroutineContext + SupervisorJob())
    private var nativeCtx = 0L

    private val pcmBuffer = mutableListOf<Short>()
    private var bufferStartMs = 0L
    private var bufferDurationMs = 0L
    private val mutex = Mutex()
    private val inferenceMutex = Mutex()

    override suspend fun start() {
        logger.info { "WhisperBackend: loading model from $modelPath" }
        val loadStart = System.currentTimeMillis()
        nativeCtx = withContext(Dispatchers.IO) { WhisperJni.loadModel(modelPath) }
        if (nativeCtx == 0L) {
            logger.error { "WhisperBackend: model load failed (path=$modelPath)" }
            _state.value = TranscriptEngineState.Error("Failed to load Whisper model: $modelPath")
            return
        }
        logger.info { "WhisperBackend: model loaded ctx=$nativeCtx in ${System.currentTimeMillis() - loadStart}ms" }
        _state.value = TranscriptEngineState.Active()
    }

    override suspend fun onPcmChunk(bytes: ByteArray, bookTimeMs: Long, durationMs: Long) {
        if (nativeCtx == 0L) return
        val shouldRunInference = mutex.withLock {
            val shorts = ShortArray(bytes.size / 2) { i ->
                (bytes[i * 2].toInt() and 0xFF or (bytes[i * 2 + 1].toInt() shl 8)).toShort()
            }
            if (pcmBuffer.isEmpty()) bufferStartMs = bookTimeMs
            pcmBuffer.addAll(shorts.toList())
            bufferDurationMs += durationMs
            logger.debug { "WhisperBackend: chunk bytes=${bytes.size} bookMs=$bookTimeMs durationMs=$durationMs bufferMs=$bufferDurationMs" }
            bufferDurationMs >= CHUNK_THRESHOLD_MS
        }
        if (shouldRunInference) {
            runInference()
        }
    }

    private suspend fun runInference() {
        val (floats, offsetMs) = mutex.withLock {
            if (pcmBuffer.isEmpty()) return
            val f = FloatArray(pcmBuffer.size) { i -> pcmBuffer[i] / 32768f }
            val o = bufferStartMs
            pcmBuffer.clear()
            bufferStartMs += bufferDurationMs
            bufferDurationMs = 0L
            f to o
        }

        val inferStart = System.currentTimeMillis()
        logger.info { "WhisperBackend: running inference floats=${floats.size} offsetMs=$offsetMs" }
        // inferenceMutex ensures freeContext in stop() waits for this call to finish
        val results = inferenceMutex.withLock {
            if (nativeCtx == 0L) return
            runCatching {
                withContext(Dispatchers.IO) {
                    WhisperJni.transcribeChunk(nativeCtx, floats, offsetMs, language)
                }
            }.getOrElse { e ->
                logger.error(e) { "WhisperBackend: transcribeChunk threw" }
                _state.value = TranscriptEngineState.Error("Whisper inference failed: ${e.message}")
                return
            }
        }
        logger.info { "WhisperBackend: inference done in ${System.currentTimeMillis() - inferStart}ms segments=${results.size}" }

        val segments = results.map { r ->
            TranscriptSegment(
                id = store.nextId(),
                startMs = r.startMs,
                endMs = r.endMs,
                text = r.text.trim(),
                isFinal = true,
                chunkId = offsetMs,
            )
        }.filter { it.text.isNotBlank() }

        segments.forEach { logger.debug { "WhisperBackend: segment [${it.startMs}-${it.endMs}] ${it.text}" } }
        store.addSegments(segments)
    }

    override fun onSeek(newPositionMs: Long) {
        innerScope.launch {
            mutex.withLock {
                pcmBuffer.clear()
                bufferStartMs = newPositionMs
                bufferDurationMs = 0L
            }
        }
    }

    override fun stop() {
        logger.info { "WhisperBackend: stop called" }
        innerScope.cancel()
        val savedCtx = nativeCtx
        nativeCtx = 0L
        if (savedCtx != 0L) {
            // Wait for any in-flight transcribeChunk to finish before freeing the context
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch(Dispatchers.IO) {
                inferenceMutex.withLock {
                    WhisperJni.freeContext(savedCtx)
                    logger.info { "WhisperBackend: freeContext done" }
                }
            }
        }
        _state.value = TranscriptEngineState.Idle
    }
}
