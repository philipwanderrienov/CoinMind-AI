# CoinAPI adoption gate and offline test contract

Status: IN PROGRESS, NOT LIVE FUTURES READY. Last reviewed 2026-10-28.

## Economic decision
CoinAPI $25 is a promotional new-account trial credit in PAYG, not a $25/month plan. PAYG usage continues to incur charges after credits are spent. Startup costs $79/month and includes trade and OHLCV WebSocket, 1,000 REST credits/day; quote streaming requires Streamer and order book streaming requires Pro according to public pricing. Confirm futures contract metrics and permissions via trial *before* committing to a plan.

## What has been implemented
A **deliberately isolated development fixture endpoint**, disabled unless FUTURES_FIXTURES_ENABLED=true:
- GET /api/v1/dev/futures-fixtures/BTCUSDT?scenario=NORMAL
- scenario=STALE
- scenario=MISSING_OI
- scenario=DISCONNECTED
- also ETHUSDT and SOLUSDT.

Fixtures carry upstreamProvider=COINAPI but provenance=SYNTHETIC_FIXTURE and liveSignalEligible=false. This is a CoinMind-normalized internal contract, **not** a validated reproduction of CoinAPI's official REST/WebSocket wire schemas. No real CoinAPI connection, key, or paid request is performed. Cannot be mistaken for a valid Futures TradeSetup data feed.

## Before an adapter may power live Futures intelligence
1. Capture versioned official CoinAPI fixture examples for the precise products/endpoints and venue-specific symbols we will buy; add schema contract tests.
2. Implement FuturesDataProvider abstraction and adapter for normalized candles/trades/quotes/order book/mark/funding/OI, preserving venue, contract id, event time and receipt time.
3. Implement historical replay with strict chronological ordering and no lookahead, missing/out-of-order event tests, rate-limit and reconnect tests.
4. Enforce fail-closed stale/incomplete/unknown market states. Synthetic data must NEVER enter trade recommendation or apparent PnL analytics.
5. Record and evaluate real contract signal performance after taker/maker fees, slippage, funding, liquidation and venue rules; track net expectancy, profit factor and drawdown.
6. Verify 5m/15m intraday model on out-of-sample futures history and paper-trade execution before any live-money decision.
7. Confirm exact CoinAPI coverage, quota costs and direct connectivity under a restricted trial; do not purchase Startup solely for development.
8. Remove legacy Polymarket-named models/routes/UI/AI config. Manifold remains the only prediction-market provider.

No capability or profit claim is warranted by the fixture tests alone.
