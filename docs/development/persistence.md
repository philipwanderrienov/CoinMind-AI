# Market Data Persistence

CoinMind uses PostgreSQL + TimescaleDB for historical market-series storage.

## MVP behavior

Persistence is intentionally disabled by default while the development server is not yet prepared.

```text
PERSISTENCE_ENABLED=false
```

The backend can therefore continue to run only with Binance REST/WebSocket and in-memory history.

Once PostgreSQL + TimescaleDB are ready:

```text
PERSISTENCE_ENABLED=true
DATABASE_URL=r2dbc:postgresql://localhost:5432/coinmind
POSTGRES_USER=coinmind
POSTGRES_PASSWORD=...
```

Apply:

```text
infrastructure/database/init/001_market_candles.sql
```

before enabling persistence.

## Write policy

Only finalized candles are persisted from the realtime stream.

```text
Binance kline
   ↓
closed = false
   → memory / realtime UI only

closed = true
   ↓
CandlestickPersistenceService
   ↓
R2DBC
   ↓
TimescaleDB market_candles
```

This prevents unnecessary database writes for every update of a forming candle.

## Uniqueness

A candle is uniquely identified by:

```text
symbol + interval + open_time
```

Writes are implemented as PostgreSQL upserts, making reconnect/replay safe.

## Planned follow-up

- Backfill historical REST candles into TimescaleDB.
- Query historical endpoints from TimescaleDB first.
- Add retention/compression policy after observing real storage growth.
- Persist additional market features separately from raw candles.
