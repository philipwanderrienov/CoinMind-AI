# Technical Indicator Engine

CoinMind calculates deterministic technical features before involving AI.

## Implemented indicators

- EMA 20
- EMA 50
- EMA 200
- RSI 14
- MACD 12/26
- MACD signal 9
- MACD histogram
- ATR 14
- Bollinger Bands 20 / 2
- Volume ratio

## Derived scores

CoinMind also derives normalized feature scores:

- Trend score
- Momentum score
- Volatility score

These scores are intended as compact machine-readable inputs for the future AI context layer.

## API

```text
GET /api/v1/indicators/{symbol}/{interval}
```

Example:

```text
GET /api/v1/indicators/BTCUSDT/1h
```

The endpoint reads recent candle history from TimescaleDB when persistence is enabled, otherwise it falls back to the in-memory historical store.

## Design principle

The AI should not calculate low-level indicators from raw text or screenshots.

```text
Candles
  ↓
Deterministic Java calculations
  ↓
TechnicalIndicators
  ↓
Market Context
  ↓
AI reasoning
```

This keeps cost, latency and reproducibility under control.
