# CoinMind AI

AI-powered cryptocurrency market intelligence platform focused initially on BTC, ETH, and SOL.

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

> Status: project foundation / Phase 1.
