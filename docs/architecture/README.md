# Architecture

## Target flow

```text
Binance / other exchange WebSocket
              ↓
      Market Data Collector
              ↓
   Technical Feature Engine
              ↓
 PostgreSQL/TimescaleDB + Redis
              ↓
      Market Context Builder
        ↙            ↘
 News Context       Market Context
        ↘            ↙
         AI Analysis Engine
                ↓
       Signal / Risk Engine
                ↓
 Spring Boot REST + WebSocket
                ↓
      Angular Dashboard
```

## Design principles

1. AI is a reasoning layer, not the source of raw market data.
2. Indicators and features are calculated deterministically before AI analysis.
3. AI providers are interchangeable through an adapter interface.
4. Realtime price updates do not invoke AI on every tick.
5. Every AI analysis should be persisted so its later accuracy can be measured.
