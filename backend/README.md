# CoinMind AI Backend

Java + Spring Boot backend for CoinMind AI.

## Current phase — Phase 1 Market Data Foundation

Implemented:

- Spring Boot application foundation
- Spring WebFlux
- Actuator health endpoint
- Configurable market symbols and timeframes
- Binance combined WebSocket connection
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
- In-memory latest ticker and candle stores
- REST endpoints for latest market snapshots
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

Single ticker:

```bash
curl http://localhost:8080/api/v1/market/tickers/BTCUSDT
```

Realtime ticker stream:

```bash
curl -N http://localhost:8080/api/v1/market/tickers/stream
```

Latest candles across all configured symbols/timeframes:

```bash
curl http://localhost:8080/api/v1/market/candles
```

Latest candles for BTC:

```bash
curl http://localhost:8080/api/v1/market/candles/BTCUSDT
```

Latest BTC 1-minute candle:

```bash
curl http://localhost:8080/api/v1/market/candles/BTCUSDT/1m
```

Realtime candlestick stream:

```bash
curl -N http://localhost:8080/api/v1/market/candles/stream
```

## Planned package structure

```text
com.coinmind
├── config
├── market
│   ├── api
│   ├── exchange
│   ├── model
│   └── service
├── indicator
├── news
├── ai
├── signal
└── common
```

The MVP remains a modular monolith.

## Next market-data milestones

1. Historical candle bootstrap via Binance REST
2. PostgreSQL/TimescaleDB persistence for closed candles
3. Order-book ingestion
4. Trade-stream ingestion
5. Technical indicator engine
