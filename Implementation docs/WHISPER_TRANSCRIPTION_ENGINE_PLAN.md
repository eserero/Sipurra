# Pluggable Transcription Engine + Whisper Support

## Context

The audiobook transcription feature currently hard-codes ML Kit as its speech engine inside `LiveTranscriptEngine`. This plan refactors the engine to support pluggable backends, adds Whisper (via whisper.cpp JNI) as a second engine, wires up model download from GitHub releases, and adds a dedicated "Transcription" settings section.

SMIL text passthrough (same UI for pre-synced EPUB3 SMIL books, no transcription needed) is deferred to a follow-up.

---

## Architecture Overview

### Pluggable Backend Pattern

The `AudioPreReader` → `Pcm16MonoResampler` pipeline is shared and unchanged. Only the speech recognition layer is swapped:

```
AudioPreReader → Pcm16MonoResampler → TranscriptionBackend (ML Kit OR Whisper)
                                               ↓
                                         TranscriptStore
                                               ↓
                              visibleSegments StateFlow → UI
```

### ML Kit vs Whisper Processing Model

| | ML Kit | Whisper (whisper.cpp) |
|---|---|---|
| Mode | Streaming via Unix pipe | Chunked: batch ~5s of audio |
| Timestamps | Approximate (from audio pipe position) | Accurate word/segment timestamps |
| Model download | Auto (Google Play Services) | Manual (GitHub release, ~75 MB) |
| Seek handling | No-op (pipe-based) | Clear buffer, reset offset |
| Language control | `Locale` at startup | `-l he` / auto flag |
| Android min SDK | 31 | 26 (native C++) |

---

## Critical Files

| File | Role |
|------|------|
| `komelia-infra/audiobook-transcription/src/main/java/snd/komelia/transcription/LiveTranscriptEngine.kt` | Orchestrator — refactor to accept `TranscriptionBackend` |
| `komelia-infra/audiobook-transcription/src/main/java/snd/komelia/transcription/MlKitLiveTranscriber.kt` | Extract into `MlKitTranscriptionBackend` |
| `komelia-infra/audiobook-transcription/src/main/java/snd/komelia/transcription/MlKitAudioPipeWriter.kt` | Moves inside `MlKitTranscriptionBackend` |
| `komelia-infra/audiobook-transcription/src/main/java/snd/komelia/transcription/TranscriptStore.kt` | Shared — minor addition: `addSegments()` method |
| `komelia-infra/audiobook-transcription/src/main/java/snd/komelia/transcription/AudioPreReader.kt` | Shared — no changes |
| `komelia-infra/audiobook-transcription/src/main/java/snd/komelia/transcription/Pcm16MonoResampler.kt` | Shared — no changes |
| `komelia-domain/core/src/commonMain/kotlin/snd/komelia/updates/OnnxModelDownloader.kt` | Reference pattern for new `WhisperModelDownloader` |
| `komelia-domain/core/src/androidMain/kotlin/snd/komelia/updates/AndroidOnnxModelDownloader.kt` | Reference pattern for `AndroidWhisperModelDownloader` |
| `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/navigation/SettingsNavigationMenu.kt` | Add "Transcription" menu entry |
| `komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/audio/AudiobookFolderController.kt` | Read settings, create correct backend on `startTranscription()` |
| `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/imagereader/ncnn/NcnnSettingsContent.kt` | Reference for model download UI pattern (DownloadDialog, install button) |

---

## Task 1 — Save Spec Documentation

Create `agent-os/specs/2026-04-24-pluggable-transcription-engine/` with:
- `plan.md` — copy of this plan
- `shape.md` — shaping notes
- `standards.md` — relevant standards (compose-ui/view-models, compose-ui/dialogs)
- `references.md` — pointers to NCNN settings as reference implementation

---

## Task 2 — Define `TranscriptionBackend` Interface + Refactor ML Kit

### 2a. New interface

**New file**: `komelia-infra/audiobook-transcription/src/main/java/snd/komelia/transcription/TranscriptionBackend.kt`

```kotlin
interface TranscriptionBackend {
    val state: StateFlow<TranscriptEngineState>

    // Called once; may suspend until initialization (model load, download, etc.)
    suspend fun start()

    // Called for every 16kHz mono PCM16 chunk from the resampler pipeline
    // bookTimeMs: book-time position (ms) of the start of this chunk
    // durationMs: duration of this chunk
    suspend fun onPcmChunk(bytes: ByteArray, bookTimeMs: Long, durationMs: Long)

    // Called on user seek to reset internal buffers
    fun onSeek(newPositionMs: Long)

    // Called to tear down
    fun stop()
}
```

### 2b. Extract ML Kit into `MlKitTranscriptionBackend`

**New file**: `MlKitTranscriptionBackend.kt`

Move all logic from `MlKitLiveTranscriber` + `MlKitAudioPipeWriter` here:

```kotlin
class MlKitTranscriptionBackend(
    private val store: TranscriptStore,
    scope: CoroutineScope,
    private val locale: Locale = Locale.US,
) : TranscriptionBackend
```

- `start()` — initializes ML Kit recognizer, handles model download progress, calls `startRecognition()`
- `onPcmChunk(bytes, bookTimeMs, durationMs)` — writes resampled PCM to pipe (delegates to `MlKitAudioPipeWriter`)
- `onSeek()` — no-op (streaming pipe, no buffer to clear)
- `stop()` — cancels scope, closes recognizer + pipe

Keep `MlKitAudioPipeWriter` as a private implementation detail inside this class (it is not part of the public API).

Delete the old `MlKitLiveTranscriber.kt` file.

### 2c. Refactor `LiveTranscriptEngine`

```kotlin
class LiveTranscriptEngine(
    private val context: Context,
    private val tracks: List<AudioTranscriptTrack>,
    private val getPlaybackMs: () -> Long,
    private val scope: CoroutineScope,
    private val backend: TranscriptionBackend,   // ← new parameter
)
```

Changes:
- Remove `pipeWriter` and `transcriber` fields; remove their construction
- `start()`: `scope.launch { backend.start() }`, then in `preReader.run { chunk → resampler → backend.onPcmChunk(...) }`
- Ticker job: reads `backend.state` instead of `transcriber.state`
- `stop()`: calls `backend.stop()`
- `onPlaybackSeeked(ms)`: also calls `backend.onSeek(ms)`

---

## Task 3 — whisper.cpp JNI Module

Add native build support to `komelia-infra/audiobook-transcription/`.

### 3a. Vendor whisper.cpp source

Copy whisper.cpp source into:
```
komelia-infra/audiobook-transcription/src/main/cpp/
├── CMakeLists.txt
└── whisper.cpp/           ← whisper.cpp source (whisper.h, whisper.cpp, ggml*.c/h)
    whisper_jni.cpp        ← our JNI bridge
```

Use the whisper.cpp release that supports GGUF quantized models (v1.7.x or later). Include only the files needed: `whisper.h`, `whisper.cpp`, `ggml.h`, `ggml.c`, `ggml-alloc.c/h`, `ggml-backend.c/h`, `ggml-quants.c/h`.

### 3b. CMakeLists.txt

```cmake
cmake_minimum_required(VERSION 3.22)
project(whisper_jni)

set(CMAKE_CXX_STANDARD 17)

add_library(whisper STATIC
    whisper.cpp/ggml.c
    whisper.cpp/ggml-alloc.c
    whisper.cpp/ggml-backend.c
    whisper.cpp/ggml-quants.c
    whisper.cpp/whisper.cpp
)
target_include_directories(whisper PUBLIC whisper.cpp/)

add_library(whisper_jni SHARED whisper_jni.cpp)
target_link_libraries(whisper_jni whisper android log)
```

### 3c. Gradle module update

In `komelia-infra/audiobook-transcription/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        externalNativeBuild {
            cmake { path = "src/main/cpp/CMakeLists.txt" }
        }
    }
    externalNativeBuild {
        cmake { path = "src/main/cpp/CMakeLists.txt" }
    }
}
```

### 3d. Kotlin JNI wrapper

**New file**: `WhisperJni.kt`

```kotlin
internal object WhisperJni {
    init { System.loadLibrary("whisper_jni") }

    // Returns a native context pointer (>0 on success, 0 on failure)
    external fun loadModel(modelPath: String): Long

    // Transcribes a chunk of 16kHz mono float32 audio.
    // offsetMs: book-time of the first sample in pcmFloat
    // language: null → auto-detect, "he" → Hebrew, "en" → English, etc.
    // Returns array of segments (may be empty)
    external fun transcribeChunk(
        ctx: Long,
        pcmFloat: FloatArray,
        offsetMs: Long,
        language: String?,
    ): Array<WhisperResult>

    // Frees the native context
    external fun freeContext(ctx: Long)
}

data class WhisperResult(val startMs: Long, val endMs: Long, val text: String)
```

### 3e. C++ JNI bridge (`whisper_jni.cpp`)

```cpp
#include <jni.h>
#include "whisper.cpp/whisper.h"
#include <android/log.h>
#include <string>
#include <vector>

extern "C" {

JNIEXPORT jlong JNICALL
Java_snd_komelia_transcription_WhisperJni_loadModel(JNIEnv *env, jobject, jstring modelPathJ) {
    const char *path = env->GetStringUTFChars(modelPathJ, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPathJ, path);
    return (jlong) ctx;
}

JNIEXPORT jobjectArray JNICALL
Java_snd_komelia_transcription_WhisperJni_transcribeChunk(
        JNIEnv *env, jobject,
        jlong ctxL, jfloatArray pcmJ, jlong offsetMs, jstring langJ) {

    auto *ctx = (whisper_context *) ctxL;

    jsize len = env->GetArrayLength(pcmJ);
    jfloat *pcm = env->GetFloatArrayElements(pcmJ, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.token_timestamps = true;
    params.max_len = 0; // segment by sentence
    params.single_segment = false;

    if (langJ != nullptr) {
        params.language = env->GetStringUTFChars(langJ, nullptr);
    }

    whisper_full(ctx, params, pcm, (int) len);
    env->ReleaseFloatArrayElements(pcmJ, pcm, JNI_ABORT);

    int n = whisper_full_n_segments(ctx);

    // Build WhisperResult[] return array
    jclass resultClass = env->FindClass("snd/komelia/transcription/WhisperResult");
    jmethodID ctor = env->GetMethodID(resultClass, "<init>", "(JJLjava/lang/String;)V");
    jobjectArray result = env->NewObjectArray(n, resultClass, nullptr);

    for (int i = 0; i < n; i++) {
        int64_t t0 = offsetMs + whisper_full_get_segment_t0(ctx, i) * 10; // whisper time is in cs
        int64_t t1 = offsetMs + whisper_full_get_segment_t1(ctx, i) * 10;
        const char *text = whisper_full_get_segment_text(ctx, i);
        jstring textJ = env->NewStringUTF(text);
        jobject seg = env->NewObject(resultClass, ctor, (jlong) t0, (jlong) t1, textJ);
        env->SetObjectArrayElement(result, i, seg);
        env->DeleteLocalRef(textJ);
        env->DeleteLocalRef(seg);
    }

    return result;
}

JNIEXPORT void JNICALL
Java_snd_komelia_transcription_WhisperJni_freeContext(JNIEnv *, jobject, jlong ctxL) {
    whisper_free((whisper_context *) ctxL);
}

} // extern "C"
```

Note: whisper.cpp timestamps are in centiseconds (cs). Multiply by 10 to get milliseconds.

---

## Task 4 — `WhisperTranscriptionBackend`

**New file**: `WhisperTranscriptionBackend.kt`

```kotlin
private const val CHUNK_THRESHOLD_MS = 5_000L  // run inference when buffer ≥ 5s

class WhisperTranscriptionBackend(
    private val store: TranscriptStore,
    private val modelPath: String,
    private val language: String?,       // null = auto-detect
    scope: CoroutineScope,
) : TranscriptionBackend {

    private val _state = MutableStateFlow<TranscriptEngineState>(TranscriptEngineState.Idle)
    override val state: StateFlow<TranscriptEngineState> = _state

    private val innerScope = CoroutineScope(scope.coroutineContext + SupervisorJob())
    private var nativeCtx = 0L

    // PCM16 accumulation buffer
    private val pcmBuffer = mutableListOf<Short>()
    private var bufferStartMs = 0L
    private var bufferDurationMs = 0L
    private val mutex = Mutex()

    override suspend fun start() {
        nativeCtx = WhisperJni.loadModel(modelPath)
        if (nativeCtx == 0L) {
            _state.value = TranscriptEngineState.Error("Failed to load Whisper model: $modelPath")
            return
        }
        _state.value = TranscriptEngineState.Active()
    }

    override suspend fun onPcmChunk(bytes: ByteArray, bookTimeMs: Long, durationMs: Long) {
        if (nativeCtx == 0L) return
        mutex.withLock {
            // Convert PCM16 bytes to shorts
            val shorts = ShortArray(bytes.size / 2) { i ->
                (bytes[i * 2].toInt() and 0xFF or (bytes[i * 2 + 1].toInt() shl 8)).toShort()
            }
            if (pcmBuffer.isEmpty()) bufferStartMs = bookTimeMs
            pcmBuffer.addAll(shorts.toList())
            bufferDurationMs += durationMs

            if (bufferDurationMs >= CHUNK_THRESHOLD_MS) {
                runInference()
            }
        }
    }

    private fun runInference() {
        // Convert PCM16 → float32 normalized to [-1, 1]
        val floats = FloatArray(pcmBuffer.size) { i -> pcmBuffer[i] / 32768f }
        val offsetMs = bufferStartMs

        val results = WhisperJni.transcribeChunk(nativeCtx, floats, offsetMs, language)

        val segments = results.map { r ->
            TranscriptSegment(
                id = r.startMs,
                startMs = r.startMs,
                endMs = r.endMs,
                text = r.text.trim(),
                isFinal = true,
            )
        }.filter { it.text.isNotBlank() }

        store.addSegments(segments)

        pcmBuffer.clear()
        bufferStartMs = offsetMs + bufferDurationMs
        bufferDurationMs = 0L
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
        innerScope.launch {
            mutex.withLock {
                // Flush remaining buffer if ≥1s worth of audio
                if (bufferDurationMs >= 1_000L && nativeCtx != 0L) {
                    runInference()
                }
            }
        }
        innerScope.cancel()
        if (nativeCtx != 0L) {
            WhisperJni.freeContext(nativeCtx)
            nativeCtx = 0L
        }
        _state.value = TranscriptEngineState.Idle
    }
}
```

**`TranscriptStore` addition** — add a bulk insert method to avoid calling `upsertLiveSegment` per segment:

```kotlin
fun addSegments(segments: List<TranscriptSegment>) {
    if (segments.isEmpty()) return
    val current = _segments.value.toMutableList()
    current.addAll(segments)
    // Keep last 60 minutes (same existing cleanup logic)
    _segments.value = current.trimToWindow()
}
```

---

## Task 5 — Whisper Model Download Infrastructure

### 5a. Interface

**New file**: `komelia-domain/core/src/commonMain/kotlin/snd/komelia/updates/WhisperModelDownloader.kt`

```kotlin
interface WhisperModelDownloader {
    fun whisperBaseDownload(): Flow<UpdateProgress>
    fun isModelDownloaded(): Flow<Boolean>
    fun modelFilePath(): String
}
```

### 5b. Android implementation

**New file**: `komelia-domain/core/src/androidMain/kotlin/snd/komelia/updates/AndroidWhisperModelDownloader.kt`

```kotlin
class AndroidWhisperModelDownloader(
    private val context: Context,
    private val updateClient: UpdateClient,
) : WhisperModelDownloader {

    private val modelsDir = File(context.filesDir, "whisper_models")
    private val modelFile = File(modelsDir, "ggml-base-q5_0.bin")

    private val MODEL_URL =
        "https://github.com/eserero/Sipurra/releases/download/model/ggml-base-q5_0.bin"

    override fun whisperBaseDownload(): Flow<UpdateProgress> = flow {
        modelsDir.mkdirs()
        updateClient.streamFile(MODEL_URL) { response ->
            val total = response.contentLength() ?: -1L
            val out = modelFile.outputStream()
            var downloaded = 0L
            response.bodyAsChannel().copyTo(out) { bytesCopied ->
                downloaded += bytesCopied
                emit(UpdateProgress(total, downloaded, "Downloading Whisper base model"))
            }
        }
    }

    override fun isModelDownloaded(): Flow<Boolean> = flow {
        // Re-emit on each collection; caller can poll or use distinctUntilChanged
        emit(modelFile.exists() && modelFile.length() > 0)
    }

    override fun modelFilePath(): String = modelFile.absolutePath
}
```

Use the existing `UpdateClient.streamFile()` helper, which handles HTTP streaming and emits `HttpResponse`.

### 5c. Dependency injection

Wire `AndroidWhisperModelDownloader` in the Android DI module (wherever `AndroidOnnxModelDownloader` is wired — check the module factory/DI file).

---

## Task 6 — Transcription Settings Data Model + Persistence

### 6a. Domain model

**New file**: `komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/model/TranscriptionSettings.kt`

```kotlin
enum class TranscriptionEngineType { ML_KIT, WHISPER }

data class TranscriptionSettings(
    val engine: TranscriptionEngineType = TranscriptionEngineType.ML_KIT,
    val whisperLanguage: String? = null,  // null = auto-detect
)
```

### 6b. Repository interface

**New file**: `komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/TranscriptionSettingsRepository.kt`

```kotlin
interface TranscriptionSettingsRepository {
    fun getSettings(): Flow<TranscriptionSettings>
    suspend fun putSettings(settings: TranscriptionSettings)
}
```

### 6c. DB persistence

Add columns to the existing settings table (or a new `transcription_settings` table) via Flyway migration:

```sql
-- New migration: V{next}__add_transcription_settings.sql
ALTER TABLE app_settings ADD COLUMN transcription_engine TEXT NOT NULL DEFAULT 'ML_KIT';
ALTER TABLE app_settings ADD COLUMN whisper_language TEXT;
```

Implement `ExposedTranscriptionSettingsRepository` following the same pattern as `ExposedImageReaderSettingsRepository`.

---

## Task 7 — Transcription Settings UI

### 7a. ViewModel

**New file**: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/transcription/TranscriptionSettingsViewModel.kt`

```kotlin
class TranscriptionSettingsViewModel(
    private val settingsRepo: TranscriptionSettingsRepository,
    private val whisperDownloader: WhisperModelDownloader,
    screenModelScope: CoroutineScope,
) : StateScreenModel<TranscriptionSettingsState>(TranscriptionSettingsState.Loading)
```

State fields (in a data class or StateScreenModel state):
- `engine: TranscriptionEngineType`
- `whisperLanguage: String?`
- `isWhisperModelDownloaded: Boolean`
- `downloadProgress: UpdateProgress?` (null = not downloading)

Methods:
- `onEngineChange(engine: TranscriptionEngineType)` — persists via repo
- `onLanguageChange(lang: String?)` — persists via repo
- `onDownloadRequest()` — launches download, updates `downloadProgress`

### 7b. Screen Composable

**New file**: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/transcription/TranscriptionSettingsScreen.kt`

Layout:
1. **Engine selector** — two-option radio group or `SingleChoiceSegmentedButtonRow`:
   - `ML Kit` (default) — "Uses Google's on-device model. Downloads automatically."
   - `Whisper` — "Uses a local model you download once (~75 MB)."

2. **Whisper section** — shown only when Whisper is selected:
   - Row: "Model: Base (quantized)" label + status chip ("Downloaded" in green / "Not downloaded" in gray)
   - Download button — opens `DownloadDialog` (reuse existing component from `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/imagereader/onnxruntime/DownloadDialog.kt`)
   - Language dropdown:
     - Auto (default)
     - English
     - Hebrew
     - French
     - German
     - Spanish

3. **Info card** at bottom explaining the tradeoffs.

### 7c. Settings navigation

In `SettingsNavigationMenu.kt`, add after the "Epub Reader" entry (inside "App Settings" group):

```kotlin
HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
SettingsListItem(
    label = "Transcription",
    onClick = { onNavigation(TranscriptionSettingsScreen()) },
    isSelected = currentScreen is TranscriptionSettingsScreen,
)
```

Also add `TranscriptionSettingsScreen` to `ViewModelFactory` and any platform-specific settings screen containers (`MobileSettingsScreen`, `DesktopSettingsScreen`) following the existing pattern.

---

## Task 8 — Wire `AudiobookFolderController` to Settings

In `AudiobookFolderController.startTranscription()` (currently at lines 339–365 of `AudiobookFolderController.kt`):

```kotlin
override fun startTranscription() {
    val settings = transcriptionSettingsRepository.getSettings().value
        ?: TranscriptionSettings()  // fallback to defaults

    val store = TranscriptStore()

    val backend: TranscriptionBackend = when (settings.engine) {
        TranscriptionEngineType.ML_KIT ->
            MlKitTranscriptionBackend(store, coroutineScope)

        TranscriptionEngineType.WHISPER -> {
            val modelPath = whisperModelDownloader.modelFilePath()
            if (!File(modelPath).exists()) {
                _transcriptState.value = TranscriptEngineState.Error(
                    "Whisper model not downloaded. Go to Settings → Transcription to download it."
                )
                return
            }
            WhisperTranscriptionBackend(store, modelPath, settings.whisperLanguage, coroutineScope)
        }
    }

    val engine = LiveTranscriptEngine(
        context = context,
        tracks = buildTranscriptTracks(),
        getPlaybackMs = { (_elapsedSeconds.value * 1000).toLong() },
        scope = coroutineScope,
        backend = backend,
    )
    transcriptEngine = engine
    coroutineScope.launch { engine.state.collect { _transcriptState.value = it } }
    coroutineScope.launch { engine.visibleSegments.collect { _liveTranscriptSegments.value = it } }
    engine.start()
}
```

Inject `TranscriptionSettingsRepository` and `WhisperModelDownloader` into `AudiobookFolderController` via its constructor.

---

## Verification

1. **Regression check (ML Kit)**: Build and run. Start ML Kit transcription on a folder audiobook → confirm it works identically to before the refactor. Transcript segments appear, read-ahead window works.

2. **Whisper model download**: Settings → Transcription → select Whisper → tap Download → confirm download dialog shows progress in MB, file appears at `<app-files-dir>/whisper_models/ggml-base-q5_0.bin`.

3. **Whisper transcription (English)**: With model downloaded and language set to Auto or English, play a folder audiobook → confirm text segments appear in transcript panel with the same read-ahead visual behaviour as ML Kit.

4. **Hebrew language**: Set language to Hebrew, play a Hebrew audiobook → confirm whisper.cpp uses `language="he"` and output text is in Hebrew script.

5. **Seek behaviour**: While Whisper transcription is active, seek forward 30s → confirm buffer resets, transcription resumes from new position without stale segments.

6. **Settings persistence**: Change engine to Whisper + set language, force-quit the app, reopen → confirm settings are restored.

7. **Error state**: Select Whisper but do NOT download the model, tap the CC button → confirm an error message is shown ("Whisper model not downloaded…") without a crash.

---

## Pre-release Manual Step

Before shipping, upload the quantized Whisper base model as a GitHub release asset on `eserero/Sipurra`, tagged `model`:

```bash
# 1. Download the original GGUF base model from whisper.cpp releases:
#    https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin

# 2. Quantize to q5_0 using whisper.cpp's quantize tool:
./build/bin/quantize models/ggml-base.bin models/ggml-base-q5_0.bin q5_0

# 3. Upload ggml-base-q5_0.bin to:
#    https://github.com/eserero/Sipurra/releases/tag/model
```

The download URL in `AndroidWhisperModelDownloader` must match the uploaded asset name exactly.
