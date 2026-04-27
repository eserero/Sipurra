# Shape: Pluggable Transcription Engine

## Problem
`LiveTranscriptEngine` hard-codes ML Kit as the sole speech engine. There is no way to swap in an alternative backend without editing the engine itself.

## Solution
Introduce a `TranscriptionBackend` interface. The shared `AudioPreReader → Pcm16MonoResampler` pipeline stays unchanged; only the recognition layer is swapped. `LiveTranscriptEngine` accepts a `TranscriptionBackend` at construction time.

The first new backend is Whisper (via whisper.cpp JNI), which processes audio in ~5s chunks rather than streaming via a Unix pipe.

## Key Decisions

- **Chunk size (5s)**: Balances Whisper inference latency with transcript freshness.
- **q5_0 quantization**: ~75 MB, good quality/size tradeoff for mobile.
- **Model URL**: `https://github.com/eserero/Sipurra/releases/download/model/ggml-base-q5_0.bin`
- **Settings persistence**: SQLite via Exposed (same as all other app settings), migration V47.
- **SMIL passthrough deferred**: Not in scope for this plan.

## Out of Scope
- SMIL text passthrough
- Desktop/JVM Whisper support (Android-only for now)
- Streaming Whisper (chunked-only in this version)
