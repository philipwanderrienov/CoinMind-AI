-- Apply manually to a trusted development database before enabling the archive.
-- Paper replay summaries only. No credentials, live positions or executable signals.
CREATE TABLE IF NOT EXISTS futures_paper_replay_archive (
    id UUID PRIMARY KEY,
    completed_at TIMESTAMPTZ NOT NULL,
    provenance VARCHAR(32) NOT NULL CHECK (provenance IN ('SYNTHETIC_FIXTURE', 'HISTORICAL_REPLAY')),
    initial_equity NUMERIC NOT NULL CHECK (initial_equity > 0),
    final_equity NUMERIC NOT NULL,
    max_drawdown_quote NUMERIC NOT NULL CHECK (max_drawdown_quote >= 0),
    executed INTEGER NOT NULL CHECK (executed >= 0),
    risk_status VARCHAR(24) NOT NULL CHECK (risk_status IN ('NORMAL','LOSS_MONITORING','DAILY_RISK_LOCK')),
    risk_day_utc DATE NOT NULL,
    daily_loss_limit_quote NUMERIC NOT NULL CHECK (daily_loss_limit_quote > 0),
    realized_loss_quote NUMERIC NOT NULL CHECK (realized_loss_quote >= 0),
    remaining_loss_budget_quote NUMERIC NOT NULL CHECK (remaining_loss_budget_quote >= 0)
);
CREATE INDEX IF NOT EXISTS idx_futures_paper_replay_archive_recent
    ON futures_paper_replay_archive (completed_at DESC, id DESC);
