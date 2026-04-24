ALTER TABLE ImageReaderSettings ADD COLUMN image_cache_size_limit_mb INTEGER NOT NULL DEFAULT 1024;
ALTER TABLE EpubReaderSettings ADD COLUMN epub_cache_size_limit_mb INTEGER NOT NULL DEFAULT 2048;
