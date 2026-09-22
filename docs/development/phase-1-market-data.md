# Phase 1 — Market Data Foundation

## Goal

Build a reliable realtime market-data foundation before adding database persistence, technical indicators, news analysis, or AI reasoning.

## Current scope

Symbols:

- BTCUSDT
- ETHUSDT
- SOLUSDT

Timeframes:

- 1m
- 5m
- 15m
- 1h
- 4h
- 1d

## Implemented

### Ticker pipeline

```text
Binance miniTicker
      ↓
BinanceTickerClient
      ↓
MarketTickerService
      ↓
REST + SSE
```

### Candlestick pipeline

```text
Binance kline streams
      ↓
BinanceKlineClient
      ↓
Candlestick normalization
      ↓
MarketCandlestickService
      ↓
REST + SSE
```

The candlestick service currently retains the latest in-progress or closed candle for each symbol/timeframe combination in memory.

## Why both open and closed candles are retained

Binance emits repeated updates for the currently-forming candle. This is useful for a realtime dashboard.

The `closed` flag identifies the final update for that candle. Once TimescaleDB persistence is implemented, closed candles will be the authoritative historical records while open-candle updates remain useful for live UI and signal preview.

## Next steps

1. Validate live connectivity and payloads on the development machine/server.
2. Add historical bootstrap via Binance REST so indicators have enough candles immediately after application startup.
3. Persist closed candles to PostgreSQL/TimescaleDB.
4. Add order-book ingestion.
5. Add trade stream ingestion.
6. Build the technical-indicator engine.
