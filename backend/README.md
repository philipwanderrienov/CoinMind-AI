# CoinMind AI Backend

Java + Spring Boot backend for CoinMind AI.

## Current phase — Phase 1 Foundation

Implemented:

- Spring Boot application foundation
- Spring WebFlux
- Actuator health endpoint
- Configurable market symbols
- Binance combined WebSocket connection
- Realtime mini-ticker ingestion for:
  - BTCUSDT
  - ETHUSDT
  - SOLUSDT
- In-memory latest ticker store
- REST endpoints for latest ticker snapshots
- Server-Sent Events stream for development/realtime verification
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

Realtime development stream:

```bash
curl -N http://localhost:8080/api/v1/market/tickers/stream
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

The MVP remains a modular monolith. Database persistence, technical indicators, AI analysis, and the Angular realtime gateway are subsequent milestones.
