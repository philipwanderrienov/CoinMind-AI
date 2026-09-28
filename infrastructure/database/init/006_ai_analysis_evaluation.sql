ALTER TABLE ai_analysis_history
    ADD COLUMN IF NOT EXISTS entry_price NUMERIC(30, 12),
    ADD COLUMN IF NOT EXISTS signal_score NUMERIC(10, 4);

CREATE TABLE IF NOT EXISTS ai_analysis_evaluations (
    id                TEXT            PRIMARY KEY,
    analysis_id       TEXT            NOT NULL REFERENCES ai_analysis_history(id) ON DELETE CASCADE,
    horizon           TEXT            NOT NULL,
    target_time       TIMESTAMPTZ     NOT NULL,
    exit_price        NUMERIC(30, 12) NOT NULL,
    return_pct        NUMERIC(18, 8)  NOT NULL,
    direction_correct BOOLEAN         NOT NULL,
    evaluated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (analysis_id, horizon)
);

CREATE INDEX IF NOT EXISTS idx_ai_analysis_eval_analysis
    ON ai_analysis_evaluations (analysis_id, horizon);

CREATE INDEX IF NOT EXISTS idx_ai_analysis_eval_time
    ON ai_analysis_evaluations (evaluated_at DESC);
