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
