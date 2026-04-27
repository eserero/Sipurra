CREATE TABLE TranscriptionSettings (
    book_id TEXT NOT NULL PRIMARY KEY,
    transcription_engine TEXT NOT NULL DEFAULT 'ML_KIT',
    whisper_language TEXT
);
