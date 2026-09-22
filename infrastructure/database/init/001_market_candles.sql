CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE IF NOT EXISTS market_candles (
    symbol          VARCHAR(32)      NOT NULL,
    interval        VARCHAR(16)      NOT NULL,
    open_time       TIMESTAMPTZ      NOT NULL,
    close_time      TIMESTAMPTZ      NOT NULL,
    open_price      NUMERIC(30, 12)  NOT NULL,
    high_price      NUMERIC(30, 12)  NOT NULL,
    low_price       NUMERIC(30, 12)  NOT NULL,
    close_price     NUMERIC(30, 12)  NOT NULL,
    base_volume     NUMERIC(38, 12)  NOT NULL,
    quote_volume    NUMERIC(38, 12)  NOT NULL,
    trade_count     BIGINT           NOT NULL,
    closed          BOOLEAN          NOT NULL,
    event_time      TIMESTAMPTZ      NOT NULL,
    PRIMARY KEY (symbol, interval, open_time)
);

SELECT create_hypertable(
    'market_candles',
    'open_time',
    if_not_exists => TRUE,
    migrate_data => TRUE
);

CREATE INDEX IF NOT EXISTS idx_market_candles_symbol_interval_time_desc
    ON market_candles (symbol, interval, open_time DESC);

CREATE INDEX IF NOT EXISTS idx_market_candles_event_time
    ON market_candles (event_time DESC);
