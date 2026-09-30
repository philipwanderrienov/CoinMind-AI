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
infrastructure/database/init/003_news_archive.sql
infrastructure/database/init/004_news_relevance.sql
infrastructure/database/init/005_ai_usage_billing_push.sql
infrastructure/database/init/006_ai_analysis_evaluation.sql
infrastructure/database/init/007_trade_outcomes.sql
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
detected symbols, deterministic sentiment score, and relevance score.

At application startup, the most recent persisted articles are restored into the
in-memory news cache. This preserves the existing low-latency synchronous news and
sentiment endpoints while preventing news history from disappearing after a restart.

News writes also use an upsert keyed by article ID, so repeated RSS polling does not
create duplicate rows.

### News retention

Recommended defaults:

```text
NEWS_HOT_RETENTION_DAYS=30
NEWS_ARCHIVE_RETENTION_DAYS=365
```

Articles older than the hot-retention period are copied to
`news_articles_archive` and removed from `news_articles`.

Archived articles older than the archive-retention period are permanently deleted.

The retention job runs daily at 03:15 by default:

```text
NEWS_RETENTION_CRON=0 15 3 * * *
```

This keeps realtime news queries focused on recent data while preserving one year
of history for research and backtesting.

## Current storage policy

Persisted:
- historical candles
- finalized realtime candles
- normalized news articles
- generated trade setups and their observed outcomes

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


## Trade outcome tracking

Trade setups with actionable states (`BUY`, `SELL`, `WATCH_BUY`, `WATCH_SELL`)
are persisted with a 15-minute deduplication window. The tracker evaluates finalized
1-minute candles once per minute and advances each setup through:

`PENDING -> ENTRY_HIT -> TP1_HIT -> TP2_HIT`

or terminates it as `STOPPED` or `EXPIRED`. Entry opportunities expire after
24 hours if the configured entry area is never touched. When stop and target are
both touched inside the same 1-minute candle, the evaluator uses the conservative
assumption and records the setup as stopped.

Endpoints:

`GET /api/v1/trade/outcomes/{symbol}?limit=20`

`GET /api/v1/trade/outcomes/{symbol}/summary`

`POST /api/v1/trade/outcomes/evaluate`
