# AI Context Architecture

CoinMind does not send raw tick streams or chart screenshots directly to an LLM.

## Flow

```text
Ticker
+ Candle history
+ Technical indicators
+ Order book
+ Trade pressure
        ↓
MarketContextBuilder
        ↓
Compact structured MarketContext
        ↓
AiProvider
        ↓
AiAnalysisResult
```

## Why this matters

- Lower token usage
- Reproducible low-level calculations
- Easier provider switching
- Easier backtesting
- Easier auditability

## Current provider state

The default provider is `NoOpAiProvider`.

This means the application can compile and expose the AI endpoint before any paid API is connected.

## Endpoints

```text
GET /api/v1/ai/context/BTCUSDT/1h
GET /api/v1/ai/analysis/BTCUSDT/1h
```

The context endpoint is useful for validating exactly what data will later be sent to the model.

## Planned providers

- OpenAI
- Gemini
- Optional local model

All providers implement the same `AiProvider` interface.
