# Market Data Persistence

CoinMind uses PostgreSQL + TimescaleDB for durable market history and PostgreSQL for persisted news articles.

## Enable persistence

```text
PERSISTENCE_ENABLED=true
DATABASE_URL=r2dbc:postgresql://localhost:5432/coinmind
POSTGRES_USER=coinmind
POSTGRES_PASSWORD=...
```

Apply database migrations in order:

```text
infrastructure/database/init/001_market_candles.sql
infrastructure/database/init/002_news_articles.sql
```

## Candle persistence

Only finalized realtime candles are written continuously. Historical REST bootstrap data is also backfilled.

```text
Binance REST/WebSocket
        ↓
closed candles
        ↓
CandlestickPersistenceService
        ↓
R2DBC
        ↓
TimescaleDB market_candles
```

A candle is uniquely identified by:

```text
symbol + interval + open_time
```

Writes use PostgreSQL upserts, so reconnects and historical replay are idempotent.

## News persistence

Normalized RSS articles are written to:

```text
news_articles
```

Stored fields include the article ID, title, source, URL, summary, publish time,
detected symbols, and deterministic sentiment score.

At application startup, the most recent persisted articles are restored into the
in-memory news cache. This preserves the existing low-latency synchronous news and
sentiment endpoints while preventing news history from disappearing after a restart.

News writes also use an upsert keyed by article ID, so repeated RSS polling does not
create duplicate rows.

## Current storage policy

Persisted:
- historical candles
- finalized realtime candles
- normalized news articles

In memory / derived on request:
- ticker snapshots
- order book
- rolling trades
- technical indicators
- market context
- AI analysis

## Planned follow-up

- Add retention/compression policy after observing real candle storage growth.
- Add persistent AI analysis history once a real AI provider is enabled.
- Consider aggregated microstructure snapshots instead of persisting every raw tick.
