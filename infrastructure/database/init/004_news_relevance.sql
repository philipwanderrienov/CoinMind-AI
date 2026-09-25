ALTER TABLE news_articles
    ADD COLUMN IF NOT EXISTS relevance_score NUMERIC(10, 4) NOT NULL DEFAULT 0;

ALTER TABLE news_articles_archive
    ADD COLUMN IF NOT EXISTS relevance_score NUMERIC(10, 4) NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_news_articles_relevance_published
    ON news_articles (relevance_score DESC, published_at DESC);

CREATE INDEX IF NOT EXISTS idx_news_articles_archive_relevance_published
    ON news_articles_archive (relevance_score DESC, published_at DESC);
