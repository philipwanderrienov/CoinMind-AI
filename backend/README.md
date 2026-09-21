# CoinMind AI Backend

Java + Spring Boot backend for CoinMind AI.

## Current phase — Phase 1 Market Data Foundation

Implemented:

- Spring Boot application foundation
- Spring WebFlux
- Actuator health endpoint
- Configurable market symbols and timeframes
- Binance public REST + WebSocket market-data integration
- Realtime mini-ticker ingestion:
  - BTCUSDT
  - ETHUSDT
  - SOLUSDT
- Realtime candlestick/kline ingestion:
  - 1m
  - 5m
  - 15m
  - 1h
  - 4h
  - 1d
- Historical candle bootstrap from Binance REST
- Default historical depth: 500 candles per symbol/timeframe
- In-memory bounded historical candle store
- Optional PostgreSQL + TimescaleDB persistence via R2DBC
- Closed-candle upsert persistence (disabled by default until server DB is ready)
- Realtime candle updates merged into historical series
- REST endpoints for ticker, current candle and history
- Server-Sent Events streams for realtime development verification
- Basic unit tests

## Runtime

Requirements:

- Java 25 LTS
- Maven 3.9+

Run:

```bash
cd backend
mvn spring-boot:run
```

Health:

```bash
curl http://localhost:8080/actuator/health
```

Latest tickers:

```bash
curl http://localhost:8080/api/v1/market/tickers
```

Historical BTC 1-minute candles:

```bash
curl "http://localhost:8080/api/v1/market/history/BTCUSDT/1m?limit=500"
```

Realtime candles:

```bash
curl -N http://localhost:8080/api/v1/market/candles/stream
```

## Data flow

```text
Startup
  ↓
Binance REST /api/v3/klines
  ↓
Historical candle store
  ↓
REST history endpoint
  ↓
Angular initial chart

Binance WebSocket
  ↓
Realtime kline
  ↓
Latest candle + history upsert
  ↓
SSE
  ↓
Angular realtime chart update
```

## Next market-data milestones

1. Historical REST backfill into TimescaleDB
2. Order-book ingestion
3. Trade-stream ingestion
4. Technical indicator engine
5. AI market context builder
