CREATE TABLE IF NOT EXISTS news_articles_archive (
    id                TEXT            PRIMARY KEY,
    title             TEXT            NOT NULL,
    source            TEXT            NOT NULL,
    url               TEXT,
    summary           TEXT,
    published_at      TIMESTAMPTZ     NOT NULL,
    symbols           TEXT            NOT NULL DEFAULT '',
    sentiment_score   NUMERIC(10, 4)  NOT NULL DEFAULT 0,
    ingested_at       TIMESTAMPTZ     NOT NULL,
    archived_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_news_articles_archive_published_at_desc
    ON news_articles_archive (published_at DESC);

CREATE INDEX IF NOT EXISTS idx_news_articles_archive_symbols
    ON news_articles_archive (symbols);
