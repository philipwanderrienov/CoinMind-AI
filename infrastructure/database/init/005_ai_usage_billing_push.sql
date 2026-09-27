CREATE TABLE IF NOT EXISTS ai_usage_log (
    id                      TEXT            PRIMARY KEY,
    request_id              TEXT,
    symbol                  TEXT            NOT NULL,
    interval                TEXT            NOT NULL,
    trigger_type            TEXT            NOT NULL,
    model                   TEXT            NOT NULL,
    input_tokens            BIGINT          NOT NULL DEFAULT 0,
    cached_input_tokens     BIGINT          NOT NULL DEFAULT 0,
    output_tokens           BIGINT          NOT NULL DEFAULT 0,
    reasoning_tokens        BIGINT          NOT NULL DEFAULT 0,
    total_tokens            BIGINT          NOT NULL DEFAULT 0,
    estimated_input_cost_usd  NUMERIC(18, 8) NOT NULL DEFAULT 0,
    estimated_output_cost_usd NUMERIC(18, 8) NOT NULL DEFAULT 0,
    estimated_total_cost_usd  NUMERIC(18, 8) NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_usage_log_created_at
    ON ai_usage_log (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_usage_log_symbol_interval
    ON ai_usage_log (symbol, interval, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_analysis_history (
    id                 TEXT            PRIMARY KEY,
    symbol             TEXT            NOT NULL,
    interval           TEXT            NOT NULL,
    trigger_type       TEXT            NOT NULL,
    market_bias        TEXT            NOT NULL,
    confidence         INTEGER         NOT NULL,
    summary            TEXT            NOT NULL,
    supporting_factors TEXT            NOT NULL DEFAULT '',
    risk_factors       TEXT            NOT NULL DEFAULT '',
    model              TEXT            NOT NULL,
    analyzed_at        TIMESTAMPTZ     NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_analysis_history_symbol_time
    ON ai_analysis_history (symbol, interval, analyzed_at DESC);

CREATE TABLE IF NOT EXISTS push_subscriptions (
    id          TEXT        PRIMARY KEY,
    endpoint    TEXT        NOT NULL UNIQUE,
    p256dh_key  TEXT        NOT NULL,
    auth_key    TEXT        NOT NULL,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS billing_reminders (
    id             TEXT        PRIMARY KEY,
    provider       TEXT        NOT NULL,
    payment_date   DATE        NOT NULL,
    reminder_date  DATE        NOT NULL,
    reminder_type  TEXT        NOT NULL,
    sent_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status         TEXT        NOT NULL,
    UNIQUE (provider, payment_date, reminder_type)
);
