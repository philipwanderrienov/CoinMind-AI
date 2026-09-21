# Backend

Java + Spring Boot backend for CoinMind AI.

## Planned modules

```text
backend/
└── src/main/java/.../coinmind/
    ├── market/       # Exchange WebSocket ingestion
    ├── indicator/    # RSI, EMA, MACD, ATR, volatility
    ├── news/         # News ingestion and classification
    ├── ai/           # AI provider abstraction and analysis
    ├── signal/       # Decision and confidence engine
    ├── websocket/    # Realtime client push
    └── api/          # REST API
```

Initial implementation should remain a modular monolith. Split into services only when scaling requires it.
