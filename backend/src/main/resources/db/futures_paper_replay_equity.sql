-- Apply ONLY to an isolated development database after futures_paper_replay_archive.sql.
-- Realized-only simulated equity; no live positions or credentials.
CREATE TABLE IF NOT EXISTS futures_paper_replay_equity (
    replay_id UUID NOT NULL REFERENCES futures_paper_replay_archive(id) ON DELETE CASCADE,
    point_index INTEGER NOT NULL CHECK (point_index >= 0),
    realized_at TIMESTAMPTZ NOT NULL,
    equity_usdt NUMERIC NOT NULL,
    net_pnl_usdt NUMERIC NOT NULL,
    PRIMARY KEY (replay_id, point_index)
);
CREATE INDEX IF NOT EXISTS idx_futures_paper_replay_equity_time
    ON futures_paper_replay_equity (replay_id, realized_at);
