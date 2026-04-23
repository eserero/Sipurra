package snd.komelia.transcription

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

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

    private val _engineState = MutableStateFlow<TranscriptEngineState>(TranscriptEngineState.Idle)
    val state: StateFlow<TranscriptEngineState> = _engineState

    private val _visibleSegments = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    val visibleSegments: StateFlow<List<TranscriptSegment>> = _visibleSegments

    private val chunksDecoded = AtomicInteger(0)
    private var preReaderJob: Job? = null
    private var tickerJob: Job? = null

    fun start() {
        scope.launch { transcriber.start() }

        preReaderJob = scope.launch {
            preReader.run { chunk ->
                chunksDecoded.incrementAndGet()
                pipeWriter.writePcm(chunk.bytes, chunk.sampleRate, chunk.channelCount, chunk.bookTimeMs)
            }
        }

        tickerJob = scope.launch {
            while (isActive) {
                val playMs = getPlaybackMs()
                _visibleSegments.value = store.visibleSegmentsForPlayback(playMs)
                val baseState = transcriber.state.value
                _engineState.value = if (baseState is TranscriptEngineState.Active) {
                    val sentMs = pipeWriter.audioSentUntilMs
                    val totalSegs = store.segments.value.size
                    val vis = _visibleSegments.value.size
                    val responses = transcriber.responsesReceived.get()
                    val errors = transcriber.errorsReceived.get()
                    val lastType = transcriber.lastResponseType.get()
                    val lastErr = transcriber.lastErrorMsg.get()
                    TranscriptEngineState.Active(
                        "chunks=${chunksDecoded.get()} piped=${sentMs / 1000}s " +
                        "play=${playMs / 1000}s resp=$responses err=$errors " +
                        "last=$lastType segs=$totalSegs vis=$vis\nerr:$lastErr"
                    )
                } else {
                    baseState
                }
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
        _engineState.value = TranscriptEngineState.Idle
    }

    fun onPlaybackSeeked(newPositionMs: Long) {
        preReader.reset(newPositionMs)
    }
}
