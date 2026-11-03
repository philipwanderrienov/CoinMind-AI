# Current Development State

_Last updated: 2026-10-26 (Asia/Jakarta)_

Dokumen ini adalah sumber kebenaran (handoff) untuk state CoinMind-AI saat ini. Baca file ini sebelum melakukan perubahan.

## Product state

CoinMind-AI adalah aplikasi crypto market intelligence dan trade-advisor / decision-support untuk:

- BTCUSDT
- ETHUSDT
- SOLUSDT

Decision Engine deterministic/multi-timeframe tetap menjadi sumber rekomendasi utama. AI News Intelligence dan Polymarket hanya berfungsi sebagai bounded supporting signals dan tidak boleh menciptakan BUY/SELL secara independen.

Scope produk sudah diputuskan sebagai **advisor-only**. CoinMind tidak mengeksekusi order ke Binance/exchange. Output BUY/SELL/WATCH, entry, target, dan invalidation adalah rekomendasi yang harus dieksekusi manual oleh user di exchange.

Primary application views:

```text
Dashboard
Trade Decision
News
AI & Usage
AI Billing
```

## Current access

Public test URL:

```text
https://phive-server.tail597574.ts.net/
```

Private Tailscale URL:

```text
http://100.89.143.40/
```

Backend local:

```text
http://localhost:8080/
```

## Deployment workflow

### Frontend

```bash
cd /home/phive/Documents/CoinMind-AI
git pull origin main

cd frontend
npm run build

sudo rm -rf /var/www/coinmind/*
sudo cp -r dist/coinmind-web/browser/* /var/www/coinmind/
sudo systemctl reload nginx
```

### Backend

```bash
cd /home/phive/Documents/CoinMind-AI
git pull origin main

cd backend
mvn clean package -DskipTests
sudo systemctl restart coinmind-backend
sudo systemctl status coinmind-backend
```

Smoke test backend:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/trade/setup/BTCUSDT/1m
```

## Navigation / responsive shell

CoinMind menggunakan view-based application sidebar.

### Desktop

- persistent left sidebar, 252px
- tidak menggunakan horizontal section navigation lama
- klik menu mengganti view aplikasi, bukan melakukan smooth-scroll ke halaman panjang
- sidebar footer menampilkan selected market
- Dashboard berisi chart, market condition, hourly activity, Detail Market, technical/microstructure context
- Trade Decision berisi penjelasan lengkap Decision Engine
- News berisi news/sentiment dan AI News Intelligence
- AI & Usage berisi AI status, engine/model, token usage, estimasi biaya, evaluation, dan history

### Mobile

- sidebar berubah menjadi off-canvas drawer melalui hamburger
- memilih menu menutup drawer
- floating BTC/ETH/SOL selector tetap draggable dan edge-snapping
- header mobile ditargetkan sebagai satu row 64px
- status strip berada di row terpisah
- legacy sticky horizontal navigation sudah dihapus

View state menggunakan logical hash:

```text
#view=dashboard
#view=trade
#view=news
#view=ai
```

First-load logic menggunakan manual scroll restoration agar browser tidak meloncat ke chart/DOM anchor.

Latest mobile header hardening commit:

```text
937f6c6 fix: harden mobile header geometry
```

Visual acceptance setelah commit tersebut masih perlu dilakukan pada production build / browser nyata.

## Backend / Decision Engine state

Backend telah terbukti berjalan pada port 8080 dan endpoint trade setup merespons.

Trade actions:

```text
WAIT
WATCH_BUY
WATCH_SELL
BUY
SELL
```

Decision timeframes:

```text
1m  -> Execution
15m -> Short-term
1h  -> Decision
4h  -> Trend
1d  -> Context
```

Trade setup dapat memuat:

- confidence
- signal score
- risk level
- alignment score/label
- market regime
- market activity
- entry range
- invalidation
- target 1 / target 2
- risk/reward
- reasons
- warnings

Guardrail penting: alignment tinggi tidak otomatis berarti BUY/SELL. Jika setup minimum belum terpenuhi, engine tetap dapat menghasilkan WAIT/WATCH.

## Market / realtime state

Market source utama:

- Binance REST
- Binance WebSocket

Frontend menggunakan SSE/EventSource untuk:

- ticker
- candle
- order book

Frontend stale threshold saat ini sekitar 20 detik dengan reconnect cooldown sekitar 15 detik.

## News / AI state

News pipeline:

```text
RSS
 -> relevansi BTC/ETH/SOL
 -> deterministic sentiment
 -> persistence/history
 -> optional AI News Intelligence
```

Baseline RSS:

- CoinDesk
- Cointelegraph

AI News Intelligence menghasilkan:

- BULLISH / BEARISH / NEUTRAL
- importance
- confidence
- horizon
- summary
- catalysts
- risks
- per-article impact

AI News hanya bounded modifier untuk Decision Engine.

### Prediction source health

Because Polymarket access is officially blocked in Indonesia, CoinMind does not attempt to bypass ISP/regulatory blocking.

A runtime source-health endpoint now exposes the last observed status of each prediction source:

```text
GET /api/v1/polymarket/source-health
```

Possible states:

```text
UNKNOWN
UP
EMPTY
UNAVAILABLE
```

Tracked sources:

```text
POLYMARKET_GAMMA
POLYMARKET_CLOB
MANIFOLD
```

The endpoint records the last check time, concise failure/reachability detail, and last relevant signal count. It does not trigger a new network request by itself; it reports the state captured during normal refreshes.

Important separation:

- Gamma/CLOB/Manifold = prediction-market / forecast context.
- CoinDesk/Cointelegraph RSS = news context.
- Polymarket Gamma is not a news API.

## Polymarket state

Polymarket adalah prediction-market intelligence, bukan sumber berita.

Market discovery sekarang memakai **multi-source failover**: Gamma `/events` sebagai primary, CLOB `/simplified-markets` sebagai secondary, lalu **Manifold public forecasting API** sebagai compliant third-source fallback jika Polymarket diblokir/unreachable. Frontend menampilkan source aktual agar provenance data tidak ambigu.

Endpoint:

```text
GET  /api/v1/polymarket/{symbol}
POST /api/v1/polymarket/{symbol}/refresh
```

Server pada 2026-10-21 terobservasi dapat resolve DNS `gamma-api.polymarket.com`, tetapi koneksi TCP/HTTPS dapat timeout.

Hardening yang sudah masuk `main`:

- Reactor Netty connect timeout default 5 detik
- response/operation timeout default 8 detik
- failure cooldown default 2 menit per symbol
- fallback antar-source: Gamma events -> CLOB simplified markets -> Manifold search markets
- source field ikut dikirim ke frontend (`POLYMARKET_GAMMA`, `POLYMARKET_CLOB`, `MANIFOLD`, `NONE`)
- fallback ke last cached result bila semua source gagal
- fallback ke neutral result bila cache belum ada
- timeout/error expected dicatat sebagai warning ringkas tanpa full stack trace
- failure Polymarket tidak boleh menjatuhkan backend atau Trade Decision

Config:

```text
POLYMARKET_BASE_URL
POLYMARKET_CLOB_BASE_URL
PREDICTION_MANIFOLD_BASE_URL
POLYMARKET_GAMMA_EVENT_LIMIT
POLYMARKET_CLOB_PAGE_LIMIT
POLYMARKET_CONNECT_TIMEOUT_MS
POLYMARKET_RESPONSE_TIMEOUT_MS
POLYMARKET_FAILURE_COOLDOWN_MS
```

Latest commits:

```text
accbf7a fix: harden Polymarket timeouts and fallback
2276883 config: add Polymarket resilience settings
```

Backend Maven build dan smoke test pada `phive-server` masih perlu dijalankan setelah pull commit ini.

## AI & Usage state

AI framework sekarang mendukung **cost-aware task routing**.

Important: AI provider live **belum aktif** sampai server memiliki API key dan `AI_ENABLED=true`. ChatGPT Plus/Codex subscription tidak digunakan oleh backend; CoinMind memakai API key dan API billing terpisah.

Task routing:

```text
Routine model
- News Intelligence
- Polymarket interpretation dengan materiality rendah/menengah

Deep model
- Manual/general market analysis
- AI Decision Review
- Polymarket interpretation dengan materiality tinggi
```

Config:

```text
AI_PROVIDER
AI_MODEL
AI_ROUTINE_MODEL
AI_DEEP_MODEL
AI_API_KEY
AI_ENABLED
```

Default routine/deep model masih fallback ke `AI_MODEL`, sehingga model berbeda tidak wajib digunakan sebelum benchmark.

Existing cost controls:

- token usage tracking
- estimated cost tracking
- cached input token tracking
- hourly automatic-call cap
- daily automatic-call cap
- automatic signal-score guardrail
- route-specific pricing for routine/deep models
- `AI_ENABLED=false` is a hard master switch; paid provider methods return unavailable instead of calling the API

If routine/deep models use different prices, configure:

```text
AI_ROUTINE_INPUT_PRICE_MICROS_PER_MILLION
AI_ROUTINE_CACHED_INPUT_PRICE_MICROS_PER_MILLION
AI_ROUTINE_OUTPUT_PRICE_MICROS_PER_MILLION
AI_DEEP_INPUT_PRICE_MICROS_PER_MILLION
AI_DEEP_CACHED_INPUT_PRICE_MICROS_PER_MILLION
AI_DEEP_OUTPUT_PRICE_MICROS_PER_MILLION
```

### AI model benchmark

Current benchmark candidates (AI remains disabled by default):

```text
Routine: gpt-6-luna
Deep:    gpt-6.1-sol
```

Rationale:

- routine route is for high-volume structured classification/interpretation
- deep route is for Decision Review, general market reasoning, and high-materiality forecast context
- these are benchmark candidates, not a claim that the deep route is always superior for CoinMind

Current standard API pricing configured in the usage estimator:

```text
gpt-6-luna:
  input       $0.10 / 1M
  cached      $0.01 / 1M
  output      $0.50 / 1M

gpt-6.1-sol:
  input       $2.00 / 1M
  cached      $0.10 / 1M
  output      $10.00 / 1M
```

Manual benchmark endpoints:

```text
GET  /api/v1/ai/benchmark/config
POST /api/v1/ai/benchmark/forecast/{symbol}
```

GET is free and only reports configured routes.

POST requires `AI_ENABLED=true` and an API key and intentionally performs two paid calls against the same forecast context: routine then deep. The result reports latency, each structured analysis, model agreement, and whether each direction matches the deterministic forecast bias.

Benchmark is manual only; there is no scheduled benchmark job.

Frontend AI & Usage advanced details now shows the configured routine/deep models.

### AI Polymarket Intelligence

Polymarket sekarang memiliki dua layer terpisah:

```text
Polymarket API
   ↓
Deterministic parser / scorer
   ↓
PolymarketIntelligence
   ↓
Materiality gate + cache + budget guardrail
   ↓ (hanya jika perlu)
AI Polymarket Intelligence
```

Automatic AI call hanya layak jika deterministic data dianggap material, default:

- absolute average probability change >= 3%
- atau importance/relevance >= 80
- atau materiality score >= 75

Materiality tinggi memilih `AI_DEEP_MODEL`; kondisi lain memilih `AI_ROUTINE_MODEL`.

AI input dibatasi ke structured data dan maksimum lima market teratas, bukan raw Polymarket payload penuh.

AI output:

- direction
- importance
- confidence
- horizon
- summary
- catalysts
- risks
- contradictions
- materiality score
- actual model used

Cache AI Polymarket default 60 menit. Repeated identical context menggunakan cache agar tidak membuang token.

Automatic Polymarket AI calls juga menggunakan `AiBudgetGuardrailService` sehingga tunduk pada hourly/daily limit.

Endpoints:

```text
GET  /api/v1/ai/polymarket-intelligence/{symbol}
POST /api/v1/ai/polymarket-intelligence/{symbol}
```

GET hanya membaca cache/fallback deterministic dan tidak memaksa paid call.
POST adalah manual analysis request.

Config:

```text
AI_POLYMARKET_ENABLED
AI_POLYMARKET_MIN_PROBABILITY_CHANGE
AI_POLYMARKET_MIN_IMPORTANCE
AI_POLYMARKET_DEEP_MATERIALITY_SCORE
AI_POLYMARKET_CACHE_MINUTES
```

`AiProvider` tetap menjadi abstraction layer, sehingga provider lain dapat ditambahkan untuk benchmark tanpa mengubah service/use-case layer.

AI Polymarket interpretation **belum menjadi modifier tambahan untuk Decision Engine**. Untuk sekarang ia ditampilkan sebagai advisor context/second opinion. Deterministic Polymarket modifier yang sudah dibatasi tetap menjadi satu-satunya Polymarket contribution ke Decision Engine sampai hasil AI layer dievaluasi.

Frontend Prediction Market panel sekarang menampilkan deterministic layer dan AI interpretation layer secara terpisah.

## AI Billing / Cost Center

Dedicated `AI Billing` view tracks CoinMind's own AI usage accounting:

- input tokens
- cached input tokens
- output tokens
- reasoning tokens
- estimated input cost
- estimated output cost
- total cost today / current month / lifetime
- monthly breakdown by model
- monthly breakdown by trigger/activity
- optional internal credit budget via `AI_CREDIT_BUDGET_USD`
- estimated remaining internal budget

Endpoint:

```text
GET /api/v1/ai/usage/cost-center
```

Important accounting rule:

- CoinMind calculates cost from token usage returned by the provider response and configured model pricing.
- `AI_CREDIT_BUDGET_USD` is only an internal reference budget.
- Estimated remaining budget is NOT the live OpenAI prepaid balance.
- OpenAI Billing remains authoritative for actual prepaid credit, grants, invoices and account-level spend.
- Usage persistence must be enabled for historical/lifetime accounting.

## Advisor-only UI state

Frontend sekarang secara eksplisit menampilkan CoinMind sebagai advisor:

- Dashboard quick decision menggunakan label `Rekomendasi`
- Trade view menggunakan eyebrow `TRADE ADVISOR`
- disclaimer advisor-only tampil di Trade Decision
- entry/target/invalidation dijelaskan sebagai reference/recommendation, bukan execution command
- footer Trade Decision menggunakan `CoinMind advisor engine`
- tidak ada tombol Place Order / Execute BUY / Execute SELL

Latest commits:

```text
1942cea ui: clarify CoinMind advisor-only trade recommendations
9eaede0 style: add advisor-only recommendation notice
43251c7 docs: define CoinMind as advisor-only platform
```

Frontend production build masih perlu dijalankan di server setelah pull terbaru.

### Real-server smoke-test finding

Production smoke tests on 2026-10-26 confirmed:

- actuator health = UP
- TradeSetup responds with live Binance-derived context
- forecast fallback source = MANIFOLD
- Manifold remains NEUTRAL/context-only with confidence 45
- AI provider = MOCK
- automatic AI = false
- monthly hard spend cap = 0
- AI usage/cost center = zero paid usage

A real output exposed one recommendation-semantics issue: WAIT could still display very high confidence because confidence was calculated independently from actionability. This has been fixed by capping recommendation confidence:

```text
WAIT       <= 60
WATCH_*    <= 80
BUY/SELL   <= 95
```

This keeps the displayed confidence aligned with the recommendation state.

## Pre-live AI readiness audit (2026-10-26)

Paid AI must remain unconfigured until the server validates this increment.

Key safety changes from the audit:

- Manifold fallback is now restricted to markets closing within roughly one month and sorted by 24h volume.
- Manifold static probability is no longer converted into an automatic bullish/bearish trading signal.
- Only `POLYMARKET_GAMMA` may modify deterministic Trade Decision confidence; CLOB/Manifold remain context only.
- Automatic paid forecast AI is disabled for fallback forecast sources.
- `AI_AUTOMATIC_ENABLED=false` is now a separate master switch; adding an API key does not enable scheduled/event-triggered paid AI.
- `AI_MAX_MONTHLY_SPEND_USD=0` is a hard fail-closed default.
- Paid AI HTTP POSTs are blocked with HTTP 402 unless persistence is enabled and recorded monthly spend is below the configured hard cap.
- AI guardrail status now exposes automatic-enabled state and monthly hard spend cap.

Current safe first-live configuration should be:

```text
PERSISTENCE_ENABLED=true
AI_ENABLED=true
AI_PROVIDER=openai
AI_AUTOMATIC_ENABLED=false
AI_MAX_MONTHLY_SPEND_USD=<small deliberate amount>
AI_CREDIT_BUDGET_USD=<optional internal reference>
```

Then verify one manual call / benchmark before considering automatic AI.

Forecast-source policy:

```text
POLYMARKET_GAMMA -> decision-confidence modifier allowed (bounded)
POLYMARKET_CLOB  -> context only
MANIFOLD         -> short-horizon context only
NONE             -> neutral fallback
```

Manifold API documentation labels the API alpha; it is acceptable as a resilient context source for this project, but should not be treated as equivalent to Polymarket temporal signal quality.

## Known open issues / priorities

Urutan prioritas saat ini:

1. **Validate AI Router + Polymarket AI pipeline di server**
   - pull `main`
   - Maven build
   - restart backend
   - test deterministic Polymarket endpoint
   - test AI Polymarket GET fallback while AI is disabled
   - verify frontend production build
   - do not enable paid AI until environment/config has been reviewed

2. **Frontend shell visual validation**
   - production Angular build setelah commit `937f6c6`
   - cek mobile header 390px
   - cek desktop 1280/1440/1920px
   - cek sidebar, status strip, chart, long Trade Decision/AI content

3. **Frontend architecture cleanup**
   - `AppComponent` masih terlalu besar
   - `app.component.css` mengandung banyak legacy/incremental overrides
   - target split: AppShell, Dashboard, TradeDecision, News, AiUsage, MobileCoinSelector

4. **CSS cleanup**
   - obsolete CSS untuk horizontal section anchor / pinned desktop menu sudah dihapus dari tail stylesheet (commit `585f26f`)
   - masih ada technical debt besar karena `app.component.css` >10k baris dan banyak incremental responsive overrides
   - setelah visual baseline diterima, lanjut hapus rule obsolete secara bertahap; jangan menambah append-only override tanpa membersihkan konflik

5. **Testing**
   - E2E view switching
   - first-load top position
   - mobile drawer
   - responsive breakpoints
   - realtime disconnect/reconnect
   - external source degraded state

## Important frontend files

```text
frontend/src/app/app.component.ts
frontend/src/app/app.component.html
frontend/src/app/app.component.css
frontend/src/app/core/models/market.models.ts
frontend/src/app/core/services/market-api.service.ts
frontend/src/app/features/market-chart/
```

## Important backend areas

```text
backend/src/main/java/com/coinmind/polymarket/
backend/src/main/resources/application.yml
```

Decision, market, news, AI, persistence modules should be inspected directly before modification.

## Synchronization rule

GitHub `main` adalah shared source of truth antara ChatGPT dan Codex.

Untuk perubahan bermakna:

1. inspect latest `main`
2. implement
3. validate relevant build/test
4. update `current-state.md`
5. append `session-log.md`
6. push/commit sesuai authorization

Jangan menggunakan chat history sebagai canonical implementation state.


## 2026-10-27 — Futures intraday work on main (partial, not deployed)

User requested direct main development. Implemented paper-only `POST /api/v1/futures/risk/preview` with validated sizing, fees/slippage assumptions, 1% risk cap, 5x leverage cap, 20% margin allocation ceiling, and associated unit tests. `GET /api/v1/futures/readiness` explicitly reports BLOCKED until futures-specific feeds, contract liquidation rules and strategy validation are ready. Prediction refresh now calls Manifold exclusively and source-health reports Manifold only; old Polymarket-named classes, unused Gamma/CLOB methods, configuration, and frontend/AI references still require full cleanup. Do not call this complete. Server Maven/Angular tests were not run; no deployment was made.


### 2026-10-27 — Manifold-only runtime and automated validation setup

- The legacy PolymarketIntelligenceService name remains for dependency compatibility, but its implementation was rewritten to request ONLY Manifold public search, filter binary unresolved questions that close in the next 35 days, and return NEUTRAL contextual evidence; no Polymarket Gamma or CLOB network client/method remains inside this service. Manifold source-health only. Configuration moved to coinmind.manifold.*.
- Old Polymarket-named routes, models, frontend labels, AI service names and legacy configuration sections elsewhere in project have NOT all been migrated. Migration is incomplete.
- Futures preview inputs now reject null side cleanly.
- Added GitHub Actions Java 25 Maven verify and Angular Node 24 production build workflow in .github/workflows/validate.yml. At last query GitHub reported zero workflow runs, so checks are not yet verified.
- DO NOT deploy or claim futures-live readiness. Production tests, exchange futures data, fee/risk/liquidation guards, and end-to-end verification remain blockers.

## 2026-10-27 — Futures public derivatives feed (main)

- Added GET /api/v1/futures/market-data/{symbol} (BTCUSDT/ETHUSDT/SOLUSDT) as a read-only opt-in endpoint.
- Pulls USD-M premiumIndex (mark/index/funding/nextFundingTime) and openInterest from the futures venue, only when FUTURES_MARKET_DATA_ENABLED=true. Default false; emits DISABLED and never silently substitutes spot data.
- Data status UP requires valid positive prices/OI, numeric funding and timestamps <2 minutes old; otherwise STALE_OR_INVALID, UNAVAILABLE, or DISABLED. No order endpoints.
- Added deterministic unit tests for disabled feed and unknown symbol.
- Not integrated into TradeSetup decision yet. Existing spot market data remains, so futuresSignalsEnabled remains false and /api/v1/futures/readiness stays BLOCKED.
- GitHub Actions failed on this revision; root CI cause not retrievable via connector. Maven and startup tests on phive-server still required. Do not deploy until verification.
- Remaining: futures candles/depth/taker pressure and historical OI, basis and funding history, exchange filters and liquidation tiers, position/risk lifecycle, after-fee backtesting and paper tracking, Manifold-only renaming/Polymarket legacy removal, UI.


## 2026-10-28 — CoinAPI synthetic fixture groundwork

Added disabled-by-default /api/v1/dev/futures-fixtures/{symbol} with NORMAL/STALE/MISSING_OI/DISCONNECTED responses and JUnit checks. This is an isolated internal normalized simulator with explicit SYNTHETIC_FIXTURE provenance, not verified CoinAPI wire compatibility and not connected to live TradeSetup. Requires Maven validation on server. See docs/development/coinapi-adapter-validation.md. Real Futures market data and model performance remain unvalidated.


## 2026-10-28 — Offline Futures candle replay engine (main; not deployed)

Added `FuturesReplayEngine` as a pure deterministic Java component and five JUnit tests. Explicit SYNTHETIC_FIXTURE or HISTORICAL_REPLAY provenance; strict ascending OHLC timestamps, direction validation, LONG/SHORT entry/exit, conservative stop-before-target on intrabar ambiguity, adverse slippage, two-sided trading fees and TIME_EXIT at final candle. No HTTP endpoint or connection to live TradeSetup; no exchange order placement. PnL is only a partial net-of-trading-fee simulation, not complete Futures net PnL: funding, liquidation, margin tiers, quantity step filters, venue fee tiers, partial fills, realistic intrabar path, and risk limits remain missing. Requires Maven verify on phive-server; do not treat results as evidence of profitable strategy.


## 2026-10-28 — Offline futures performance metrics on main

Added FuturesPerformanceAnalytics with explicit replay-only provenance checks and tests. Evaluates closed replay outputs, excludes no-entry scenarios from executed trades, computes net expectancy, win rate, profit factor (null if there are no losing trades), and max drawdown in quote currency. All outputs are experimental and exclude funding, liquidation, and exchange-specific impacts. Requires Maven server verification. Not connected to the live Decision Engine; futures readiness remains BLOCKED.


## 2026-10-28 — Intraday risk preflight (main; isolated)

Added pure Java FuturesIntradayRiskGuard and six unit tests. Fails closed for synthetic or stale data, account state not fresh, daily realized-loss threshold, maximum open positions, or consecutive-loss cooldown. Passing a preflight is NOT a live trade authorization; no order execution is performed. This isolated guard is NOT yet wired into TradeSetup, persistence, actual exchange account or an end-to-end trading workflow. Current equity daily-loss calculation is preliminary and needs authoritative start-of-day equity plus unrealized PnL and pending exposure before live use. The existing Futures readiness endpoint remains BLOCKED. Requires Maven verify on phive-server.


## 2026-10-28 — Conservative candle replay hardening

On main: full candle series is now validated before running a trade replay, even if a trade would stop out early. Entry-candle target touches no longer count as TARGET because OHLC cannot establish whether target occurred before entry. STOP on the entry candle still wins conservatively. Added two regression tests and adjusted the short target fixture to require a second candle. Maven verify is pending on phive-server. This improves simulation integrity but is NOT a full intrabar execution model; funding, liquidation, orders, exchange fee tiers, and walk-forward validation remain unimplemented. Futures readiness stays BLOCKED.


## 2026-10-28 — Independent futures data gate

Added FuturesSignalGate and four unit tests to main. Validates declared live provenance, symbol and contract metadata, time ordering/freshness, mark/index/funding/OI, execution candles/order book freshness, and contract/liquidation model verification flags. This gate is currently standalone, NOT wired to TradeSetup; these boolean flags must only come from trusted verified services, never client input. A successful gate result merely validates supplied data, not profitability or permission to trade. Existing Futures readiness stays BLOCKED. Maven verification on phive-server pending.


## 2026-10-28 — Offline futures orchestration

Added FuturesOfflineValidationPipeline combining replay results and performance metrics in a single isolated offline entry point, with three tests for synthetic batch PnL, disallowed LIVE_EXCHANGE provenance, and empty report. It does not yet run real FuturesSignalGate or FuturesIntradayRiskGuard, and is not connected to live TradeSetup or the CoinAPI adapter. No live order functionality. Maven verify is pending on phive-server; overall Futures readiness remains BLOCKED.


## 2026-10-28 — Experimental offline intraday decision engine

Added FuturesIntradayDecisionEngine and five tests on main. The separate engine proposes WATCH_LONG/WATCH_SHORT only with offline provenance, completed candles, 5m/15m momentum agreement against 1h/4h trend; otherwise WAIT. Initial thresholds are hypotheses only and NOT empirically optimized, and the engine is not yet coupled to replay, risk guard, CoinAPI adapter, or live TradeSetup. Requires Maven validation on phive-server; no live Futures recommendation is authorized.


## 2026-10-28 — Experimental strategy-to-replay integration

Added FuturesStrategyReplayPipeline and four tests. The pipeline joins offline 5m/15m decision outputs with future-only execution candles, side/provenance checks, replay outcomes, and aggregate performance; WAIT candidates produce no trade. This only checks supplied timestamps and provenance: score construction is not independently verified to exclude future data, and risk guard, CoinAPI adapter, historical ingestion, funding and liquidation remain unintegrated. Maven verification pending on phive-server; live Futures readiness remains BLOCKED.


## 2026-10-28 — Replay candidate chronology hardening

The experimental FuturesStrategyReplayPipeline now rejects nonascending decision timestamps and overlapping candidate candle windows. This deliberately conservative rule avoids counting overlapping sample windows as independent trades but also may exclude valid overlapping market opportunities. Two regression tests added. This is still an offline prototype; indicator provenance, execution realism, risk integration and historical backtesting remain open. Maven verify pending on phive-server; live readiness BLOCKED.


## 2026-10-28 — Closed candle score derivation (experimental)

Added FuturesClosedCandleScoring and four tests: calculates bounded [-100,100] close-to-close 2-interval momentum for 5m, 15m, 1h, 4h. Candle timestamp is explicitly OPEN time, and every candle must have ended by decisionTime. Rejects unclosed/future, malformed, out-of-order candles and non-offline provenance. This is a provisional score formula, not a validated strategy; caller must ensure data is truly exchange-representative and indicator history wasn't selected using future information. It is not yet wired into the strategy replay orchestrator. Requires Maven server verification; Futures readiness remains BLOCKED.


## 2026-10-28 — Closed-candle strategy-to-replay pipeline

Added FuturesCandleStrategyPipeline, which builds offline 5m/15m/1h/4h scores from completed candles, then feeds the existing FuturesStrategyReplayPipeline for chronological execution replay and net performance aggregation. Five regression tests check trade replay, WAIT, unclosed candle rejection, live-provenance rejection, and same-timestamp execution rejection. The existing standalone score-based API remains unchanged for compatibility. This is an experimental OFFLINE_ONLY pipeline: candle authenticity and a continuous historical data ingestion pipeline are not verified, the replay model excludes funding/liquidation/venue constraints, and no production decision endpoints or live futures signals were enabled. Maven verify pending on phive-server; Futures readiness remains BLOCKED.


## 2026-10-28 — Experimental chronological historical runner

Added FuturesHistoricalWalkForwardRunner and four tests. Offline-only walk-forward over contiguous 5m candles, with 15m/1h/4h closed-bar snapshots; constructs scores only using historical closed bars, executes fixed-horizon trades on future 5m bars, excludes overlap and reports aggregate PnL. Rejects missing cadence candles and LIVE_EXCHANGE provenance. Price for pending entry is the following candle's opening price (known only at execution, not scored); this is a simplified fill assumption, not exchange-realistic. No real historical feed, funding, liquidation, strategy optimization, fees by venue, portfolio sizing or end-to-end risk integration. Server Maven verification pending; Futures readiness remains BLOCKED.


## 2026-10-28 — Fix historical walk-forward decision/entry timestamp collision

Diagnosed likely cause of initial 73-test / 1-error Maven failure: decisionTime is close of 5m bar i, exactly equal to open of bar i+1; strategy replay requires strictly later execution candle timestamps. Runner now enters at open of bar i+2 (one extra 5m bar delay) and adjusts horizon bounds, preserving strict anti-lookahead. Added regression test for trending fixture path. This is a conservative experimental fill delay, not an exchange-realistic execution model. Verify with Maven on phive-server; Futures readiness remains BLOCKED.


## 2026-10-28 — Strict UTC candle time boundary validation

Updated FuturesHistoricalWalkForwardRunner to reject any OHLC open timestamp not aligned to its UTC timeframe boundary (5m, 15m, 1h, 4h) or having nonzero subsecond precision, in addition to existing contiguous-cadence checks. Added two regression tests: regularly spaced but minute-shifted 1h candles, and a subsecond-offset 5m candle. The checks validate supplied data shape, not market authenticity; no live signals enabled. Maven verification on phive-server pending and Futures readiness remains BLOCKED.


## 2026-10-28 — Risk-budgeted offline position sizing adapter

Added FuturesPaperPositionSizer, wrapping the existing FuturesRiskCalculator for SYNTHETIC_FIXTURE/HISTORICAL_REPLAY only, with four new tests (LONG, SHORT, risk cap and live-data exclusion). Estimates quantity, risk budget, notional and initial margin on fixed reference equity; rejects unsuitable risk parameters via calculator. This is an isolated paper-only adapter and is NOT yet wired into FuturesHistoricalWalkForwardRunner. Sizing uses simplified fees and slippage, not funding, gap loss, liquidation, exchange minimum steps or real account state. Server Maven verification pending. Futures live readiness remains BLOCKED.


## 2026-10-28 — Opt-in paper sizing on historical runner

Added FuturesHistoricalWalkForwardRunner.runWithPaperSizing(Input, PaperSizing) using existing FuturesPaperPositionSizer for per-candidate risk-budgeted quantity at fixed reference equity. Existing run(Input) retains fixed-quantity behavior. Strict paper constraints cap risk at 1%, leverage at 5x, with margin cap delegated to FuturesRiskCalculator. Two tests exercise sized vs legacy outcomes and invalid parameters. This is NOT compounding/equity-curve sizing; outcomes do not feed back into equity or risk controls. Exchange lot-size, adverse gaps, funding, liquidation and realized drawdown constraints remain unmodeled. Maven server verification pending. No live Futures signals; readiness BLOCKED.


## 2026-10-29 — Experimental realized paper equity ledger

Added FuturesPaperEquityLedger and four regression tests on main. This standalone OFFLINE_ONLY ledger tracks realized net PnL, sequential equity, peak-to-trough absolute drawdown, and a UTC-day loss stop which blocks subsequent submitted trades after the threshold is reached, resetting on the next UTC day. It rejects non-offline provenance and nonascending realized timestamps. **Not yet integrated into FuturesHistoricalWalkForwardRunner or live TradeSetup**: reports use externally supplied realized timestamps and cannot guarantee that trades were opened after a daily stop; equity does not yet dynamically determine the position size; no intratrade equity, unrealized PnL, funding, liquidation or exchange execution rules. Maven verify pending on phive-server. Futures readiness remains BLOCKED.


## 2026-10-29 — Opt-in dynamic equity walk-forward mode

Added FuturesHistoricalWalkForwardRunner.runWithDynamicEquity(Input,PaperSizing,dailyLossLimitPercent) with three tests. Keeps legacy modes intact, sizes each eligible candidate against latest realized simulated equity, enforces UTC daily loss stop before candidate entry, computes equity points and realized peak drawdown, and attributes PnL to conservative full-horizon settlement date. All outputs remain OFFLINE_ONLY. It currently reuses the existing runner for data pre-validation (including its legacy simulation), then performs separate sequential dynamic replay. No unrealized equity, funding, liquidation, position netting, exchange rounding, adverse gaps or true execution fills; the full horizon is reserved even if stop/target is touched sooner. Maven verify still required on phive-server; Futures live readiness BLOCKED.


## 2026-10-29 — Dynamic equity settlement chronology hardening

Fixed runWithDynamicEquity so prior replay PnL is realized at the **close** of the final execution 5m candle, rather than its open. The occupancy guard now excludes candidate decisions through that settlement instant; this prevents next decisions consuming unclosed prior-trade PnL. Added one regression test for strictly ascending, UTC-aligned settlement timestamps. This is a targeted chronology fix, not proof of complete daily-stop enforcement across all scenarios. Future work: explicit loss-triggering stress fixtures, pre-entry settlement day boundaries, realistic time-to-exit, and account-state risk integration. Maven verification pending on phive-server; live Futures remains BLOCKED.


## 2026-10-30 — Adverse-day daily-loss stress regression

Added a targeted synthetic stress test for runWithDynamicEquity: strong closed-candle bullish signals combined with adverse 5m lows that touch protective stops, a restrictive 0.5% daily realized-loss limit, blocked subsequent entries, and re-enabled entries after the next UTC day. The test also checks offline-only status and negative net equity. No production/live changes, and no exchange-side orders or automatic position closures. The daily rule remains realized-PnL based and can exceed its threshold on the triggering trade; funding, gaps, unrealized exposure and venue-specific liquidation are not modeled. Maven verify pending on phive-server; Futures readiness BLOCKED.


## 2026-10-30 — Offline daily risk dashboard snapshot model

Added FuturesPaperRiskStatus and five tests: OFFLINE_ONLY status NORMAL, LOSS_MONITORING, DAILY_RISK_LOCK, remaining realized-loss allowance and UTC risk day. Data provenance restricted to SYNTHETIC_FIXTURE or HISTORICAL_REPLAY; results cannot authorize live signals. This standalone presentational snapshot is not yet wired into the Angular dashboard or dynamic runner, and callers must provide internally trustworthy ledger values. Limit uses day-start equity with realized PnL only and omits unrealized losses, funding, liquidation, exposure and external account reconciliation. Maven verify pending on phive-server; Futures live readiness remains BLOCKED.


## 2026-10-30 — Offline dynamic replay to risk status adapter

Added FuturesPaperRiskReport.fromReplay: a pure read-only adapter reconstructing day-start equity, realized daily PnL and FuturesPaperRiskStatus from a chronological DynamicReport, with four regression tests for intraday state, next-UTC-day reset, future equity event rejection, and LIVE_EXCHANGE rejection. API exposure and Angular dashboard integration are explicitly deferred until authenticated/trusted replay persistence exists; accepting arbitrary client-supplied replay/account state as authoritative would be unsafe. Still OFFLINE_ONLY, live Futures BLOCKED. Maven verify pending on phive-server.


## 2026-11-01 — Gated internal offline replay snapshot read API (phase 1)

Added process-local FuturesPaperReplayRegistry, registering only server-side OFFLINE_ONLY DynamicReport snapshots with strict provenance and chronology checks via FuturesPaperRiskReport. Added GET /api/v1/dev/futures-paper/replays/{id}/risk as a read-only endpoint, default disabled via FUTURES_PAPER_REPLAY_READ_ENABLED=false; missing IDs and disabled requests return HTTP 404. Added four unit tests covering disabled endpoint, registered snapshot, live rejection and future settlement rejection. **No public POST/write endpoint, no automatic runner registration, no persistent PostgreSQL storage, no Angular integration.** Process-local data disappears at restart; UUID lookup is not an authentication mechanism. Keep endpoint disabled on internet-facing deployments until authorization is designed. Maven verify pending on phive-server. Futures live readiness remains BLOCKED.


## 2026-11-01 — Gated PostgreSQL paper replay archive (phase 2)

Added FuturesPaperReplayArchive (R2DBC DatabaseClient, opt-in), manual schema backend/src/main/resources/db/futures_paper_replay_archive.sql, and read-only FuturesPaperReplayArchiveController endpoints GET /api/v1/dev/futures-paper/archive and /{id} with limit capped at 100. Added four unit tests for server-side archive summary validation. Config FUTURES_PAPER_REPLAY_ARCHIVE_ENABLED=false and FUTURES_PAPER_REPLAY_ARCHIVE_READ_ENABLED=false by default. Archive saves only validated OFFLINE_ONLY snapshots via internal saveTrusted(), not direct browser input; currently **no automatic integration with runner or registry**, and schema installation must be done manually in a trusted development database. Not yet production-ready: no authentication on developer GET, no authorization or retention, no end-to-end PostgreSQL integration test, no Angular UI. Do not enable internet-facing read flags. Maven build pending on phive-server; live Futures readiness remains BLOCKED.

Added two further controller guard tests for fail-closed disabled routes and bounded history limit (1..100). Total new unit tests in this phase: six. Server Maven verification pending.


## 2026-11-02 — Novice-friendly Angular Simulasi & Risiko workspace

Added a dedicated sidebar view `paper-risk` to the existing single-shell Angular application, retaining the default Market dashboard. New standalone PaperRiskComponent provides an explicit simulation-only banner, explanations of NORMAL/LOSS_MONITORING/DAILY_RISK_LOCK, beginner definitions of equity/drawdown and UTC reset, refresh control, responsive historical replay cards, loading/disabled/empty/error states, and live-signal disclaimer. Uses read-only `GET /api/v1/dev/futures-paper/archive?limit=20`; does NOT fabricate summary metrics when archive is absent. Currently archive reads remain OFF by default and there is no automatic runner-to-PostgreSQL write flow, so expect the informative unavailable state. No authorization has been added: do not expose archive endpoints publicly or enable developer flags on internet-facing deployment. No backend live signal/order changes. `npm run build` pending on phive-server, no UI deployment performed. Future: authenticated archive access, verified DB integration and validated persisted replay records including time series for charts.


## 2026-11-02 — Angular navigation compile fix

Fixed `AppComponent.navigateToView` parameter union to accept `paper-risk`, matching sidebar template and activeSection. Resolves reported TS2345 at app.component.html line 40. Existing NG8102 nullish coalescing warnings are separate. Frontend `npm run build` pending verification on phive-server; no deployment performed.


## 2026-11-02 — Fix washed-out paper risk mobile theme and floating BTC overlay

Replaced dark-theme fallback text and translucent cards in PaperRiskComponent with an accessible light palette: dark heading/body text, white cards, clear blue-violet reload action, visible borders, status accents, mobile vertical header/layout and extra bottom padding. On paper-risk view the redundant floating BTC selector is now hidden; on other mobile views its un-dragged form is compact (158px max) to reduce obstruction while retaining drag/menu functionality. No changes to market signals, storage, security flags or live trading. `npm run build` and visual mobile/desktop verification pending on phive-server; no deployment performed.


## 2026-11-02 — Internal offline replay workflow bridge

Added `FuturesPaperReplayWorkflow` as a Spring service with `runAndRegister()` to execute dynamic paper replay and register an internally computed snapshot, plus `archiveRegistered(UUID)` to explicitly persist that trusted snapshot using the existing R2DBC archive when enabled. No HTTP POST, no automatic archive subscriptions, no production migration or API flag changes. Added three regression tests for fail-closed input/live/unknown ID. Archive persistence remains opt-in and must be independently validated against PostgreSQL after manually applying its schema. Existing archive GET endpoints remain disabled by default and unauthenticated; do not enable them on an internet-facing server. Maven verify pending on phive-server; Futures live readiness BLOCKED.


## 2026-11-02 — Optional PostgreSQL archive integration validation

Added `FuturesPaperReplayPostgresIT` (manual opt-in JUnit test) which runs only when `FUTURES_ARCHIVE_TEST_CONFIRM=ISOLATED_TEST_DATABASE_ONLY` and explicit test R2DBC URL are supplied. Refuses the standard `/coinmind` database name, inserts a random-id synthetic OFFLINE_ONLY summary, reads it through a separately created repository object, checks history/idempotent insert, and removes its test row. Instructions: `docs/development/paper-replay-postgres-test.md`. This is NOT enabled in normal `mvn verify` and has NOT been run against a live database yet. No schema applied or production flags changed. Further work needed for secure archive API, automated persistence and persisted equity time series. Futures live remains BLOCKED.


## 2026-11-02 — Realized equity curve persistence groundwork

Added `FuturesPaperEquityArchive` as opt-in R2DBC storage of server-computed realized equity points and time-ordered reads, plus isolated SQL `backend/src/main/resources/db/futures_paper_replay_equity.sql` (FK to replay summary). Registry now exposes an internal defensive curve copy. `FuturesPaperReplayPersistence.archiveRegisteredWithCurve(UUID)` explicitly archives an already registered trusted OFFLINE_ONLY replay: validates chronology, PnL arithmetic, final equity and executed count, then stores summary and curve. **Not automatic**; no new HTTP endpoint and no public API activation. Writes are not yet atomic across the two tables; failures may leave a summary without full curve and must be retried using the same ID. Added five curve validation tests. Test DB migrations must be run manually in an isolated DB only; neither Maven nor production DB migrations have been executed here. Next: verify Maven, dedicated PostgreSQL test and transaction-safe archive, then authenticated UI read/graph. Do not advertise demo values as actual backtests; Futures live stays BLOCKED.


## 2026-11-02 — Atomic PostgreSQL replay summary + equity curve archive

Updated `FuturesPaperReplayPersistence` to wrap `summaries.saveTrusted` and `curves.saveTrusted` in a reactive `TransactionalOperator` backed by `R2dbcTransactionManager` using the application `ConnectionFactory`. A failure inserting any equity point should now roll back the summary transaction rather than leave a partial archive (subject to actual PostgreSQL integration verification). Validation remains ahead of the transaction; there is still no automatic replay scheduling, public POST route, schema migration or enabling of archive API flags. Maven verification and isolated PostgreSQL rollback test are pending on phive-server. Existing `FuturesPaperReplayWorkflow.archiveRegistered` remains a summary-only internal method; call `FuturesPaperReplayPersistence.archiveRegisteredWithCurve` for atomic summary+curve archives. Futures live stays BLOCKED.


## 2026-11-02 — Secure opt-in PostgreSQL verification and transaction rollback test

Introduced `FuturesArchiveTestConnection` to assemble isolated test R2DBC ConnectionFactory from a credential-free URL (`r2dbc:postgresql://127.0.0.1:5432/coinmind_replay_test`) plus separate `FUTURES_ARCHIVE_TEST_DB_USER`/`FUTURES_ARCHIVE_TEST_DB_PASSWORD` environment variables. Helper accepts only local dedicated DB, and confirmation flag stays mandatory. Updated `FuturesPaperReplayPostgresIT`; added `FuturesPaperReplayTransactionIT` which commits summary + curve together and deliberately triggers a duplicate point-key database exception in a subsequent transaction, expecting rollback of both tables. Updated test command in `docs/development/paper-replay-postgres-test.md`. No production DB or API flags touched; Maven and real DB verification on phive-server PENDING. Important: this test proves the same transaction pattern, not an invocation of `FuturesPaperReplayPersistence.archiveRegisteredWithCurve`; service-level integration remains future work. Never include passwords in shared logs. Futures live BLOCKED.


## 2026-11-02 — Validated internal replay history projection

Added `FuturesPaperReplayHistory` as internal read-only service combining archived PostgreSQL summary with ordered realized equity points. It rejects missing/incomplete points, invalid point indices or timestamps, PnL arithmetic mismatches and final-equity mismatches rather than presenting broken curves as valid. Added six focused unit tests and extended isolated `FuturesPaperReplayTransactionIT` to verify read-back after successful commit and absence after rollback. No HTTP route, no auth bypass, no live data, no production DB migration, and archive flags remain OFF. Both Maven verify and dedicated PostgreSQL integration tests need rerun on phive-server; prior user report showed 118/118 unit and 2/2 DB tests before this change. Next steps: service-level archive integration, authenticated read API and Angular chart; Futures live remains BLOCKED.


## 2026-11-02 — Private keyed replay history API + real equity curve UI (verification pending)

Updated previously unsafe developer archive GET routes to fail closed unless read-enabled flag is true **and** a separate server-side read key of at least 32 characters matches `X-CoinMind-Archive-Key` (constant-time comparison). Added GET `/{id}/equity` using internally validated `FuturesPaperReplayHistory`. Angular Simulasi & Risiko now accepts a session-only private key (no localStorage), requests archive list with key, loads persisted per-replay equity and renders an SVG line plus realized PnL rows with provenance. By default APIs remain unavailable; do not publicly enable without further production-grade authentication/access controls, rate limiting, and safe reverse-proxy configuration. A test-specific full `FuturesPaperReplayPersistence` PostgreSQL service round-trip was added as opt-in `FuturesPaperReplayServiceIT`; controller negative authorization regression test added. No automated replay runner/scheduler or production database migration; no fabricated historical curve. Backend Maven, Angular build and test PostgreSQL suite are NOT run in this environment and must be confirmed on phive-server before deploying. Futures live stays BLOCKED.


## 2026-11-02 — Trusted run-and-archive convenience path

`FuturesPaperReplayWorkflow.runAndArchive(input, sizing, dailyLossLimitPercent, completedAt, persistence)` now composes vetted offline dynamic replay -> internal registry -> transactional summary+equity archival in a deferred Mono. No scheduler, unattended automatic execution, or public HTTP writes; a trusted caller still must supply vetted candles and subscribe. Pending server Maven/DB/Angular verification and a production-ready authenticated user flow. Do not turn on developer archive endpoints on public deployments just to make the chart populate.


## 2026-11-02 — Opt-in internal Offline Replay Job

Added `FuturesOfflineReplayJob`, disabled by default via `coinmind.futures.offline-replay-job-enabled:false`. A trusted internal caller may invoke `execute(input,sizing,dailyLossLimitPercent)` with vetted OFFLINE_ONLY fixture/historical bars; it composes the previously tested `FuturesPaperReplayWorkflow.runAndArchive` into transactional PostgreSQL summary+equity archival. It uses boundedElastic for CPU replay work, a process-local single-flight guard to prevent overlapping replay jobs, and a maximum of 100,000 5m candles. No scheduled trigger, HTTP endpoint, CLI, live feed or automatic ingestion was introduced. Existing archive flags still default OFF and production credentials/schema are untouched. Added three fail-closed unit tests. Build and integration checks still need running on phive-server; do NOT enable job or archive reads in public deployments until ingest validation, safe internal trigger and production-grade auth are in place. Futures live remains BLOCKED.


## 2026-11-02 — Strict offline candle CSV ingestion foundation

Added `FuturesOfflineCandleImport` pure Java CSV reader with mandatory header `open_time_utc,open,high,low,close`; accepts ISO-8601 UTC instants and decimal OHLC for 5m/15m/1h/4h, validates contiguous timestamps, exact timeframe boundaries, positive and internally consistent candle prices and bounded input (100,000 rows per frame). `assemble()` verifies sequences again before producing `FuturesHistoricalWalkForwardRunner.Input` with explicitly offline provenance only. Added four ingestion unit tests for success, gaps, invalid timeframe/OHLC, wrong headers and non-offline sources. **This neither downloads candles nor verifies external historical authenticity**: provenance must be established by trusted operators before labeling a source `HISTORICAL_REPLAY`. No scheduled job trigger, HTTP upload, production DB migration or live trading access was enabled. Backend Maven verification pending on phive-server. Next: trusted multi-timeframe import from authorized local files and job integration test against isolated DB. Futures live BLOCKED.


## 2026-11-02 — Cross-timeframe OHLC consistency gate

`FuturesOfflineCandleImport.assemble()` now compares each 15m/1h/4h candle to the corresponding aggregate of 5m candles when the full higher-timeframe window is covered by the 5m input; open = first 5m open, close = last 5m close, high/low = max/min. Rejects mismatches before replay. Two unit tests cover matching and mismatching 15m examples. Partial edge windows are deliberately not compared, so this is not a complete data authenticity check; existing runner closed-bar/no-look-ahead validation remains separate. No new network, HTTP, scheduled job or production access. Prior user 132-unit-test build was successful; new 134-unit-test suite awaits verification on phive-server. Further work required for trusted file-bundle ingest and fully isolated CSV→job→PostgreSQL integration. Futures live BLOCKED.


## 2026-11-02 — Four-stream offline CSV bundle ingestion

Added `FuturesOfflineCsvBundle.read(...)` for four explicitly trusted Reader streams (5m/15m/1h/4h). It reuses strict CSV row parsing and the new complete-window cross-timeframe consistency gate, returning a validated `FuturesHistoricalWalkForwardRunner.Input` ready for a trusted caller to pass to the disabled-by-default internal job. No filesystem path handling, auto-discovery, exchange API call, scheduled trigger, or public HTTP endpoint. Added two tests for four-frame fixture and rejection of live provenance before reading. **136 unit tests expected**, pending Maven verification on phive-server. Import and job are not yet automatically connected to real historical datasets or production persistence. Futures live BLOCKED.


## 2026-11-02 — Isolated four-CSV-to-PostgreSQL end-to-end integration test

Added opt-in `FuturesOfflineCsvToPostgresIT`: produces four synchronized synthetic flat OHLC CSV streams (5m 325 bars, 15m 109, 1h 28, 4h 7); reads via `FuturesOfflineCsvBundle`, executes `FuturesOfflineReplayJob` with explicit test-only enablement, persists via the real transactional `FuturesPaperReplayPersistence` to isolated PostgreSQL, then reloads via `FuturesPaperReplayHistory` and deletes the test snapshot. Explicitly tests a zero-trade flat-market case (empty equity curve and unchanged initial/final equity), **not** a profitable trading scenario or a nonempty equity curve. Requires isolated `coinmind_replay_test` database, same test credentials and `FUTURES_ARCHIVE_TEST_CONFIRM=ISOLATED_TEST_DATABASE_ONLY`; never deploy or enable public routes. Maven integration execution on phive-server PENDING. Follow-up: add nonzero-trade CSV integration fixture and more failure-path coverage, then trusted real-data ingestion and operational auth. Futures live BLOCKED.


## 2026-11-02 — Nonempty realized-PnL CSV-to-PostgreSQL scenario

Extended isolated `FuturesOfflineCsvToPostgresIT` with a second end-to-end scenario constructing consistent rising synthetic 5m/15m/1h/4h CSV streams, invoking the real opt-in internal job, persisting through R2DBC, and independently reading back and reconciling the nonempty realized equity curve against net PnL and final equity. Existing zero-trade flat case remains. The fixture is synthetic; results must never be represented as genuine historical trading performance. This new test has NOT been executed on phive-server yet: run dedicated integration suite with isolated DB and report failures for correction. No production DB, live trading, HTTP route or scheduler changes. Futures live BLOCKED.


## 2026-11-02 — Fix weak synthetic trend in CSV/PostgreSQL integration fixture

The isolated `FuturesOfflineCsvToPostgresIT.trendingCsvProducesPersistedRealizedPnl` failed on phive-server with zero executed trades (5 tests; 1 failure). Investigated the actual decision engine: five/15m weighted momentum requires absolute score >=55; prior fixture increased price by only 0.01 per 5m bar and correctly triggered WAIT. Changed ONLY the test generator to rise by 1 price unit per 5m bar; higher-timeframe OHLC remains derived from the same 5m sequence. No lowered strategy thresholds, no production logic changes, no assertion weakening. Integration test on isolated PostgreSQL must be rerun and may uncover further constraints. Futures live BLOCKED.


## 2026-11-02 — Fix exact equity ledger accumulation after nonempty CSV integration failure

User's test progressed beyond zero-trade assertion but failed with `Invalid or future realized equity event` in `FuturesPaperRiskReport.fromReplay`. Audited runner math: `equity.add(netPnl, MathContext.DECIMAL64)` rounded equity after individual netPnL was calculated, while downstream risk/archive validation requires `previousEquity + netPnl == currentEquity` exactly. Changed realized equity, daily PnL and max drawdown accumulation to exact BigDecimal arithmetic (leaving fee/slippage/strategy calculations intact). Strengthened dynamic runner regression to reconcile each realized point and final equity with exact sums. This is a plausible root cause, not independently confirmed without full exception stack trace and rerun; phive-server dedicated PostgreSQL integration tests and `mvn clean verify` are PENDING. No archive validation relaxation, no live trading or production data updates. Futures live BLOCKED.


## 2026-11-02 — PostgreSQL numeric precision fix for replay readback

User confirmed `mvn clean verify` passes 136 tests but nonempty CSV-to-PostgreSQL integration fails in `FuturesPaperReplayHistory.verify` with `Archived equity/PnL mismatch` **after** successful archive write. Root cause: both SQL schemas use fixed `NUMERIC(28,10)` for every equity/PnL monetary column, while replay computations preserve BigDecimal digits beyond 10 decimal places. PostgreSQL silently rounds individual equity and PnL on insert, breaking exact `previous equity + net PnL = stored equity` validation on readback. Updated both **new installation schemas** to unconstrained `NUMERIC`; added `backend/src/main/resources/db/futures_paper_replay_precision_migration.sql` to ALTER relevant monetary columns on **existing isolated DBs**. Do not assume changing CREATE TABLE IF NOT EXISTS alters existing tables. Run migration manually against `coinmind_replay_test` only, then rerun 2 integration tests. Values already rounded in old rows cannot be reconstructed; preserve strict arithmetic validator. No production DB altered, no live trading changes. Migration and test results on phive-server PENDING. Futures live BLOCKED.


## 2026-11-02 — Historical dataset integrity gate

The user confirmed isolated `FuturesOfflineCsvToPostgresIT` **2/2 tests passed** after manually applying `futures_paper_replay_precision_migration.sql` to `coinmind_replay_test`; production remains unchanged. Added `FuturesOfflineVerifiedDataset` to require an operator-reviewed explicit source, `SPOT` or `USD_M_FUTURES` market, symbol, `HISTORICAL_REPLAY` provenance, and 4 SHA-256 hashes of UTF-8 CSV strings before strict four-frame replay import. Added three unit tests: valid fixture, tampering rejection, invalid provenance/market. SHA-256 is only content integrity versus supplied hashes, **not cryptographic provider authentication or evidence that candles are genuine**. No dataset has been downloaded, no network, no production DB/API/scheduler/live trading access. Next: vetted real BTCUSDT Futures source acquisition and reproducible local artifact metadata, then run historical walk-forward. New backend unit suite expected 139 tests, **not yet verified** by phive-server.


## 2026-11-02 — Official Binance USD-M Futures archive acquisition (opt-in)

User confirmed 139/139 backend unit tests passed. Added standalone Python standard-library `scripts/offline/binance_um_monthly.py` for **explicit opt-in** downloading of completed monthly BTCUSDT USD-M Futures ZIP klines (5m,15m,1h,4h) from Binance public archive `https://data.binance.vision/data/futures/um/monthly/klines/`. Every ZIP is checked against the companion provider `.CHECKSUM` SHA-256 before bounded one-member extraction; 12-column rows normalize to `open_time_utc,open,high,low,close` with UTC open times, contiguous candles and OHLC checks. Writes four normalized CSVs and `manifest.json` containing symbol, market, origin URL, provider ZIP checksum, local CSV hash and counts. Added offline conversion tests in `scripts/offline/test_binance_um_monthly.py`. Usage from repo root: `python3 -m unittest discover -s scripts/offline -p 'test_*.py'`; `python3 scripts/offline/binance_um_monthly.py --month 2026-10 --output /home/phive/coinmind-btcusdt-2026-10` (new directory; no overwrites). Inspect source files/checksums and manifest before internal historical replay. **The script has not been run against the live archive or tested on phive-server**, and does not invoke the Java replay/job nor PostgreSQL. Archive completeness, remote availability, network access and normalized multi-frame agreement need validation on server. It does not require API keys or enable public trading endpoints. Do not commit downloaded data or sensitive local artifacts. Futures live BLOCKED.


## 2026-11-02 — Diagnose real-archive timestamp failures without weakening integrity checks

User ran Python 2/2 tests successfully; actual `--month 2026-10` downloader failed `ValueError: Unaligned, duplicate, or missing candles` while normalizing first archive(s), before a manifest was written. The previous error collapsed timestamp unit, unaligned clock and true missing candles into one message. Updated Python normalizer to explicitly recognize 13-digit millisecond and 16-digit microsecond epoch timestamps, reject fractional or nonconforming timestamps, and report timeframe, row, expected UTC open, actual UTC open and exact delta for gaps or duplicates. **Real-archive source of failure not yet confirmed**; Binance documentation specifically describes microseconds for SPOT starting 2025, not necessarily Futures. No missing candles filled and no thresholds bypassed. Move output directory creation after successful validation of all four archives so failed runs do not leave partial output. Added 2 Python regression tests; total target 4 tests, pending execution on phive-server. Run `python3 -m unittest discover -s scripts/offline -p 'test_*.py'` then retry with a NEW output directory, e.g. `/home/phive/coinmind-btcusdt-2026-10-v2`. If failure recurs, share the precise new error; treat actual gaps as a dataset integrity issue. No trading, DB writes or live endpoints. Futures live BLOCKED.


## 2026-11-02 — Operator-only verified September historical replay test

User downloaded official BTCUSDT USD-M Futures September 2026 archive on phive-server: 5m=8640, 15m=2880, 1h=720, 4h=180, local manifest hashes all match. Added `backend/src/test/java/com/coinmind/trade/futures/FuturesHistoricalDatasetReplayIT.java` opt-in JUnit integration test, gated by `COINMIND_HISTORICAL_DATASET_DIR`, to read local manifest/files (no network), enforce exact four timeframe filenames/order/official source URL metadata, month completeness, hash/count checks, call existing Java multi-frame OHLC verified loader and run realized-equity walk-forward. Prints executions, waits, risk blocks, realized net PnL, max quote drawdown, wins/losses/win-rate/profit factor and reconciles all equity points. This is a first research/backtest baseline only: default sizing/equity/fees/slippage are explicit test assumptions; no funding, liquidation, venue fill realism, out-of-sample testing or profitability assertion. Does NOT persist PostgreSQL or expose HTTP/order execution. Use `cd backend && COINMIND_HISTORICAL_DATASET_DIR=/home/phive/coinmind-btcusdt-2026-09 mvn '-Dtest=FuturesHistoricalDatasetReplayIT' test`; integration test is disabled unless env supplied. Actual test compilation, Sept OHLC cross-timeframe agreement and replay outcomes are PENDING verification on phive-server. `mvn clean verify` standard should remain 139 tests (this opt-in test skipped unless enabled). Futures live BLOCKED.


## 2026-11-02 — Compile fix for opt-in historical replay integration test

User's phive-server `mvn clean verify` failed in `testCompile` because `FuturesHistoricalDatasetReplayIT` imports `com.fasterxml.jackson.databind.JsonNode/ObjectMapper`, but Jackson 2 databind was absent from the Maven test classpath (Spring Boot 4 project). Added explicit `com.fasterxml.jackson.core:jackson-databind:2.19.4` with Maven `test` scope to `backend/pom.xml`; no production runtime dependency, business logic, trading or database change. Re-run `mvn clean verify`, then `COINMIND_HISTORICAL_DATASET_DIR=/home/phive/coinmind-btcusdt-2026-09 mvn '-Dtest=FuturesHistoricalDatasetReplayIT' test`. The historical replay test itself and new dependency resolution are NOT yet verified on phive-server. Futures live BLOCKED.


## 2026-11-02 — Offline historical per-trade diagnostics

User confirmed real BTCUSDT USD-M Sep 2026 historical backtest `BUILD SUCCESS` with 15 executed trades, 5 wins, 10 losses, net PnL about -8.30 USDT, profit factor ~0.546. Added immutable `TradeDetail` records to dynamic replay results with decision UTC, execution-window open UTC, conservative settlement UTC, LONG/SHORT, entry/exit reason, fill prices, gross PnL, fees and net PnL. Preserved old 11-argument `DynamicReport` constructor for existing callers/fixtures; does not alter strategy/risk/entry/exit or persistence schema. Historical opt-in test now prints `COINMIND HISTORICAL TRADE` detail rows, and unit test reconciles trade results with realized curve. Disclaimer: settlement timestamp is end-of-reserved-horizon accounting time, NOT necessarily actual STOP/TARGET touch time; execution-window open is not a precise actual fill timestamp. No production API or trading changes. Build/test execution on phive-server PENDING. Run `mvn clean verify` and then `COINMIND_HISTORICAL_DATASET_DIR=/home/phive/coinmind-btcusdt-2026-09 mvn '-Dtest=FuturesHistoricalDatasetReplayIT' test`. Futures live BLOCKED.


## 2026-11-02 — Historical backtest diagnostics and horizon comparison

After user's successful September 2026 offline backtest (15 trades, 5 wins/10 losses, ~-8.30 USDT net), added pure `FuturesBacktestDiagnostics` grouping by LONG/SHORT and STOP/TARGET/TIME_EXIT with counts, wins/losses, gross PnL, total fees, net PnL and average net PnL. Added unit regression ensuring group partitions reconcile to total realized equity. Extended opt-in `FuturesHistoricalDatasetReplayIT` to print `COINMIND HISTORICAL GROUP` summaries and run 2-, 3-, and 6-bar horizons on the exact same verified dataset, risk sizing and cost assumptions, labeled IN-SAMPLE EXPLORATION (not parameter optimization). No strategy thresholds, entry/exit rules, risk limits, live order routing, database or public endpoint changes. New backend unit test count expected 140, unverified on phive-server; historical output also pending. Run `mvn clean verify` then `COINMIND_HISTORICAL_DATASET_DIR=/home/phive/coinmind-btcusdt-2026-09 mvn '-Dtest=FuturesHistoricalDatasetReplayIT' test`. A separate out-of-sample month is required before any strategy/parameter recommendation. Futures live BLOCKED.


## 2026-11-02 — Read-only multi-month holdout comparison launcher

User confirmed real September BTCUSDT USD-M replay and diagnostics successfully ran. Added `scripts/offline/compare_historical_months.py` to orchestrate the EXISTING checksum/source/month-validated `FuturesHistoricalDatasetReplayIT` once per supplied month in isolated Maven test processes, defaulting to Jul/Aug/Sep 2026. It fails closed when any dataset directory is absent, the JUnit process fails, or report month/market/symbol/metrics mismatch. No CSV is written unless all months succeed; creates a new metrics-only CSV, not DB data. Added 3 parser unit tests. No strategy/entry/exit/fee parameters changed, no orders or production DB updates. Run `python3 -m unittest discover -s scripts/offline -p 'test_*.py'`, then download July and August into `/home/phive/coinmind-btcusdt-2026-07` and `...-08` with `binance_um_monthly.py` (new paths only), then from repo root run `python3 scripts/offline/compare_historical_months.py --backend backend --dataset-root /home/phive --months 2026-07 2026-08 2026-09 --output /home/phive/coinmind-historical-comparison-2026-07-09.csv`. WARNING: July/Aug are EARLIER periods; this is chronological retrospective robustness/holdout, not a genuinely later prospective out-of-sample. Maven/report and new Python tests pending server verification. Futures live BLOCKED.


## 2026-11-03 — Cross-month strategy diagnosis, trade-side and exit breakdown

Following user's successful Jul/Aug/Sep BTCUSDT USD-M monthly comparison (11, 20, 15 trades respectively; all 3 months net negative), extended `scripts/offline/compare_historical_months.py` to require complete reconciled `COINMIND HISTORICAL GROUP` output from each existing verified Java historical integration test. New `--groups-output` produces an additional CSV with month, dimension (side/exit), category LONG/SHORT/STOP/TARGET/TIME_EXIT, trade count, wins, losses, gross, fees, net and average net. Strictly checks 5 unique categories per month; both the side and exit partitions must independently sum to that month's executed count and exact net PnL. Prints independent-month aggregate trading fees/net PnL; not compounded portfolio performance. All exports remain offline/read-only and fail closed on missing/mismatched results. Three added parser tests (expected Python suite 10 tests), **not yet run on phive-server**. Usage after `git pull`: `python3 -m unittest discover -s scripts/offline -p 'test_*.py'` then `python3 scripts/offline/compare_historical_months.py --backend backend --dataset-root /home/phive --months 2026-07 2026-08 2026-09 --output /home/phive/coinmind-historical-comparison-2026-07-09-v2.csv --groups-output /home/phive/coinmind-historical-groups-2026-07-09.csv`. New output paths must not exist. No modification of strategy, production API, database, or trading; Futures live BLOCKED.
