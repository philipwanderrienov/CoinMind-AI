# CoinMind AI

AI-assisted cryptocurrency market intelligence and trade-advisor platform focused initially on BTC, ETH, and SOL. CoinMind provides recommendations and decision support; it does not execute exchange orders.

## Architecture

- **Backend:** Java + Spring Boot
- **Realtime:** Spring WebFlux + WebSocket
- **Frontend:** Angular + RxJS
- **Market charts:** TradingView Lightweight Charts
- **Database:** PostgreSQL + TimescaleDB
- **Cache / event stream:** Redis
- **Deployment:** Docker Compose + Nginx
- **Server:** Ubuntu Server (headless / no GUI required)

## Repository Structure

```text
CoinMind-AI/
├── backend/          # Java / Spring Boot application
├── frontend/         # Angular realtime dashboard
├── infrastructure/   # Docker, database, nginx and deployment config
├── docs/             # Architecture and API documentation
├── .env.example
├── .gitignore
├── docker-compose.yml
└── README.md
```

## Initial Scope

Market coverage:
- BTC/USDT
- ETH/USDT
- SOL/USDT

Core pipeline:

```text
Exchange WebSocket
      ↓
Market Data Collector
      ↓
Technical / Feature Engine
      ↓
PostgreSQL + TimescaleDB / Redis
      ↓
AI Analysis Engine
      ↓
Spring Boot API + WebSocket
      ↓
Angular Realtime Dashboard
```

> Scope: advisor / decision-support only. BUY/SELL/WATCH outputs are recommendations, not automatic order execution.

> Status: active development.


## Current Development Handoff

For the latest implementation state, UI decisions, deployment commands, open issues, and continuation notes, read:

```text
docs/development/current-state.md
```

This file should be checked first when continuing CoinMind development in a new conversation.
