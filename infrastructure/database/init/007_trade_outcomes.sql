CREATE TABLE IF NOT EXISTS trade_outcomes (
    id VARCHAR(36) PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    interval VARCHAR(16) NOT NULL,
    action VARCHAR(32) NOT NULL,
    side VARCHAR(16) NOT NULL,
    confidence INTEGER NOT NULL,
    signal_score NUMERIC(20, 8),
    alignment_score INTEGER NOT NULL,
    market_regime VARCHAR(64),
    market_activity VARCHAR(32),
    market_activity_score NUMERIC(20, 8),
    entry_low NUMERIC(30, 12),
    entry_high NUMERIC(30, 12),
    invalidation_price NUMERIC(30, 12),
    target1 NUMERIC(30, 12),
    target2 NUMERIC(30, 12),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    entry_hit_at TIMESTAMPTZ,
    entry_price NUMERIC(30, 12),
    target1_hit_at TIMESTAMPTZ,
    target2_hit_at TIMESTAMPTZ,
    stopped_at TIMESTAMPTZ,
    expired_at TIMESTAMPTZ,
    generated_at TIMESTAMPTZ NOT NULL,
    evaluated_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trade_outcomes_symbol_generated
    ON trade_outcomes (symbol, generated_at DESC);

CREATE INDEX IF NOT EXISTS idx_trade_outcomes_open
    ON trade_outcomes (status, generated_at)
    WHERE status IN ('PENDING', 'ENTRY_HIT', 'TP1_HIT');
