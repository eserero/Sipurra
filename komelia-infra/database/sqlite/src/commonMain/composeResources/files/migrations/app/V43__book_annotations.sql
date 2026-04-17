CREATE TABLE book_annotations
(
    id              TEXT    NOT NULL,
    book_id         TEXT    NOT NULL,
    reader_type     TEXT    NOT NULL,
    locator_json    TEXT,
    selected_text   TEXT,
    highlight_color INTEGER,
    page_number     INTEGER,
    x               REAL,
    y               REAL,
    note            TEXT,
    created_at      INTEGER NOT NULL,
    PRIMARY KEY (id)
);

ALTER TABLE AppSettings ADD COLUMN last_highlight_color INTEGER;
