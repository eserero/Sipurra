CREATE TABLE IF NOT EXISTS epub_bookmarks (
    id TEXT PRIMARY KEY,
    book_id TEXT NOT NULL,
    locator_json TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS epub_bookmarks_book_id_idx ON epub_bookmarks (book_id);