# Development Session Log

This is a concise chronological handoff log shared by ChatGPT and Codex.

## 2026-10-01

- Added logical mobile `#market` and desktop `#dashboard` routes to prevent first-load anchor jumps while preserving active navigation state.
- Kept mobile header/status visible on first load and preserved sticky mobile section navigation.
- Added draggable, edge-snapping floating BTC/ETH/SOL selector on mobile.
- Normalized responsive layout rules and added a desktop dashboard composition pass.
- Removed duplicate AI Decision Review content.
- Added AI News Intelligence and conservative Decision Engine integration.
- Added Polymarket prediction-market intelligence and bounded confidence integration.
- Enabled public HTTPS access through Tailscale Funnel at `https://phive-server.tail597574.ts.net/`.
- Verified Funnel DNS, TLS certificate, Nginx response, and HTTP 200.
- Added `docs/development/current-state.md` as the project handoff snapshot.

## 2026-10-02

- Established GitHub as the synchronization layer between the main ChatGPT conversation and Codex running on the server.
- Added root `AGENTS.md` with mandatory project-read, validation, handoff, and synchronization rules.
- Added this session log.
- Going forward, every meaningful ChatGPT/Codex change should update both `current-state.md` and this log.

### Desktop layout implementation (local only)

- Refined the existing >=1100px composition block in `app.component.css`; unified all desktop containers, compacted upper controls/tickers, reduced chart/support-panel height, and tightened Trade Decision grids.
- Retained chart/decision 8/4 split, paired Microstructure/Polymarket and News/Insight; compacted Detail Market and full-width History, pairing evaluation/usage cards.
- Removed the conflicting >=1500px container/chart override and desktop sidebar stickiness so long decision content remains reachable.
- No HTML, TypeScript, navigation IDs/routes, mobile rules, API, business logic, or backend changes. Source comparison verified pre-desktop CSS unchanged.
- Production build passed using installed Node 22.23.2 and network access for Google Fonts; existing Angular NG8102/NG8107 template warnings remain. Default Node 20 fails the CLI version requirement; sandbox build cannot resolve Google Fonts.
- Visual/browser acceptance pending: check desktop widths >=1100px, short screens, long decision/AI content, indicators and expanded News/History; smoke-check mobile navigation/selector and breakpoint resizing.
- Updated current-state handoff. No deployment or GitHub push, per user instruction. Existing untracked deploy script/package lock excluded.


### Desktop structure/navigation correction

- Replaced mismatched section route/id naming with semantic ids: `dashboard`, `signals`, `market`, `news`, `history`.
- Updated desktop and mobile nav hrefs/click targets to the same semantic section ids.
- Changed logical first-load aliases to `#section=dashboard` and `#section=market` so initial header visibility is preserved without colliding with real anchors.
- Corrected desktop scroll offset: desktop no longer subtracts the full control-shell height because the shell is in normal flow.
- Removed the desktop 8/4 chart/Signals composition; Dashboard and Signals now stack full-width in normal document order.
- Explicitly disabled sticky positioning/max-height constraints on Trade Decision for desktop.
- Preserved lower desktop Market/News/History composition and current mobile interaction model.
- Changes were pushed to `main`; Angular production build is still pending in this ChatGPT environment and must be run on `phive-server` before deployment/acceptance.


### Navigation-only source correction (local, no commit/deployment/push)

- Rechecked HTML semantic hrefs/ids and the responsive CSS; retained existing section targets and all visual layout/selector/sticky-nav behavior.
- Moved tracking after view creation; normalized initial entry before ids render to keep both viewport types at the top, including real-anchor reloads (desktop Dashboard/mobile Market).
- Unified scripted offsets and CSS anchor margins; tracked sorted rendered section positions with rounding tolerance. Added in-session hash handling and cancellable scroll finishing/selection release for manual scrolling.
- Source simulation passed 30 menu-target cases across six widths plus entry, manual tracking, hashchange, rounding, and bottom clamping; actual browser verification remains pending.
- Final production build passed with Node 22.23.2; existing Angular NG8102/NG8107 warnings remain. No commit/push/deploy allowed by the resumed instruction. Earlier sudo deployment attempt failed authentication before copying files or reloading Nginx.

### Desktop persistent section menu (local only)

- Added a footprint slot around the desktop menu; only the menu pins to the viewport top after scrolling past its original position, without moving section layout.
- Desktop-only final rules neutralize legacy shell stickiness and its backdrop filter. Mobile navigation and floating selector remain unchanged.
- Desktop offset is measured menu height + 18px, shared by click/hash scrolling, CSS anchors, and active tracking; mobile offset and first-load behavior are preserved.
- No deployment, commit, or push. Browser verification of the new behavior remains pending.

- Validation for desktop persistence: standard `npm run build` passed on retry with Node 22.23.2 (first attempt hit Google Fonts DNS EAI_AGAIN); existing Angular template warnings remain. New browser/visual checks remain pending.


### View-based application sidebar

- Replaced the legacy horizontal section navigator with four primary application views: Dashboard, Trade Decision, News, and AI & Usage.
- Added persistent desktop sidebar with active state and selected-market summary.
- Added mobile hamburger + off-canvas drawer; retained the draggable floating coin selector.
- Dashboard view keeps chart, market condition/hourly activity, and Detail Market context.
- Trade Decision view isolates the detailed decision engine output.
- News view isolates the coin news/sentiment section.
- AI & Usage view exposes AI Insight, engine/model details, token usage, cost estimates, evaluation summary, and history.
- Removed obsolete horizontal navigation markup so Angular no longer compiles stale event handlers.
- Changes are pushed to main. Production Angular build and real browser visual validation are still pending on phive-server.


### Mobile header cleanup after sidebar introduction

- Fixed the broken mobile header composition introduced by the sidebar pass.
- Hamburger, brand mark, and CoinMind AI title now share one 64px row.
- Status cards were separated into a dedicated two-column strip with mobile-safe sizing/wrapping.
- Legacy sticky controls are hidden on mobile; main/content widths and bottom spacing are normalized.
- Commit pushed: `ee472d3 fix: stabilize mobile header and sidebar layout`.
- Production build and real-device/browser visual verification remain pending.

## 2026-10-21

### Polymarket resilience hardening

- Investigated production timeout to `gamma-api.polymarket.com:443`; backend itself remained healthy on port 8080.
- Added configurable Reactor Netty connect timeout (default 5s) and response/operation timeout (default 8s).
- Added per-symbol failure cooldown (default 2 minutes) to prevent repeated upstream hammering while Polymarket is unreachable.
- Added fallback behavior: return last cached intelligence when available, otherwise return neutral intelligence.
- Replaced expected timeout stack-trace logging with concise warning messages containing symbol, reason, and fallback mode.
- Added `POLYMARKET_BASE_URL`, `POLYMARKET_CONNECT_TIMEOUT_MS`, `POLYMARKET_RESPONSE_TIMEOUT_MS`, and `POLYMARKET_FAILURE_COOLDOWN_MS` configuration.
- Implementation commits: `accbf7a`, `2276883`.
- Server-side Maven build/restart/smoke validation remains pending after `git pull origin main`.

### Handoff cleanup

- Rewrote `docs/development/current-state.md` so the view-based sidebar architecture is canonical.
- Removed the stale legacy horizontal-navigation state from the primary handoff.
- Added current backend verification state, Polymarket hardening details, frontend validation priorities, and technical cleanup priorities.

### Frontend legacy navigation CSS cleanup

- Verified current HTML no longer contains `desktop-dashboard-nav`, `desktop-nav-slot`, `mobile-sticky-dashboard-nav`, `#signals`, or `#history`.
- Removed the obsolete tail CSS that still implemented scroll-anchor offsets and pinned horizontal desktop navigation for those deleted elements.
- Preserved current view-based sidebar styles and existing Trade Decision grid rules.
- Commit: `585f26f style: remove obsolete section navigation CSS`.
- Production Angular build and browser validation are still required after pulling latest `main`.

### Advisor-only product scope

- Product scope was explicitly fixed as advisor / decision-support only.
- CoinMind will not send BUY/SELL orders to Binance or another exchange.
- Updated Trade Decision wording so BUY/SELL/WATCH are presented as recommendations.
- Added an advisor-only disclaimer in the Trade view.
- Reworded stop/invalidation and entry areas as risk/reference levels.
- Updated README and canonical current-state documentation.
- Commits: `1942cea`, `9eaede0`, `43251c7`.
- Frontend production build/deployment remains pending on `phive-server`.

## 2026-10-22

### Cost-aware AI router and Polymarket AI intelligence

- Added routine/deep AI model routing with `AI_ROUTINE_MODEL` and `AI_DEEP_MODEL`.
- General market analysis and Decision Review use the deep route; News Intelligence uses the routine route.
- Added `AiPolymarketIntelligence` model, service and API endpoints.
- Polymarket AI analysis receives only compact structured data from the deterministic layer, capped to the top five markets.
- Added Polymarket materiality gating, cache, hourly/daily automatic-call guardrail, and scheduled analysis of tracked symbols.
- Default automatic trigger thresholds: 3% probability change, importance/relevance 80, deep materiality 75, cache 60 minutes.
- Added deterministic/AI separation in the frontend Prediction Market panel plus manual AI analysis action.
- Added route-specific token pricing so routine/deep cost estimates can remain accurate when different models are configured.
- Enforced `AI_ENABLED` as a hard master switch for every live OpenAI provider call.
- Updated `.env.example` with model-routing, pricing, Polymarket AI and upstream resilience settings.
- AI Polymarket output remains advisor context only; it does not yet modify the deterministic Decision Engine.
- Live API provider remains disabled until a server API key is intentionally configured.
- Local build could not be executed in the ChatGPT container because external GitHub DNS/network access is unavailable; server-side Maven + Angular production validation is required before deployment acceptance.

### Polymarket multi-source discovery failover

- Replaced Gamma `/public-search` as primary discovery with the documented `GET /events?active=true&closed=false&order=volume_24hr...` flow.
- Added local BTC/ETH/SOL filtering over active events and direct extraction from nested markets, removing the extra per-event fetch roundtrip.
- Added read-only CLOB `/simplified-markets` as a second-host fallback when Gamma is unreachable.
- CLOB fallback confidence is capped lower than Gamma because it may lack 24h probability-change metadata; current YES probability is used only as bounded contextual evidence.
- Existing cache/neutral fallback and cooldown remain the final degradation layer.
- Added `POLYMARKET_CLOB_BASE_URL`, `POLYMARKET_GAMMA_EVENT_LIMIT`, and `POLYMARKET_CLOB_PAGE_LIMIT`.
- Server-side Maven validation is required before deployment acceptance.

### Compliant forecast-source fallback for Indonesia

- Production server logs confirmed both `gamma-api.polymarket.com` and `clob.polymarket.com` were DNS/routing-blocked and timed out from the Indonesian ISP path.
- Added Manifold public forecasting API as a third-source fallback instead of implementing VPN/proxy circumvention.
- Prediction intelligence now carries explicit source provenance: `POLYMARKET_GAMMA`, `POLYMARKET_CLOB`, `MANIFOLD`, or `NONE`.
- Manifold uses public `/v0/search-markets` binary/open-market discovery for BTC/ETH/SOL-related questions.
- Manifold and CLOB fallback confidence remains lower than Gamma because 24h probability-change data may be unavailable.
- Frontend relabeled the panel to generic Forecast Signal and shows the active data source.
- AI structured context now includes the source and uses source-neutral prediction/forecast wording.
- Server Maven + frontend production build validation remains required.

### Routine/deep AI benchmark harness

- Updated current OpenAI benchmark candidates to `gpt-6-luna` (routine) and `gpt-6.1-sol` (deep), while keeping `AI_ENABLED=false` by default.
- Updated token-cost defaults for the two routes.
- Extended `AiProvider` with an explicit model override for controlled benchmark runs.
- Added a manual forecast benchmark service that runs routine and deep sequentially against exactly the same structured prediction context.
- Benchmark output includes per-model latency/result, direction agreement, deterministic-bias agreement, source and materiality.
- Added free `GET /api/v1/ai/benchmark/config` and paid/manual-only `POST /api/v1/ai/benchmark/forecast/{symbol}`.
- No scheduled benchmark calls are made.
- Frontend AI advanced details now displays routine/deep model routing.
- Backend Maven + frontend production build remain required on phive-server before this increment is accepted.

### Dedicated AI Billing / Cost Center

- Added a fifth application view: `AI Billing`.
- Added backend cost-center aggregation for today, current month and lifetime usage.
- Cost accounting separates input and output cost and tracks input/cached/output/reasoning tokens.
- Added monthly cost breakdown by model and trigger type.
- Added optional `AI_CREDIT_BUDGET_USD` internal budget reference and estimated remaining internal budget.
- Added `GET /api/v1/ai/usage/cost-center`.
- Frontend displays internal credit budget, estimated lifetime spend, remaining estimate, daily/monthly token/cost cards, per-model costs and per-activity costs.
- UI explicitly states that this is a CoinMind estimate, not the live OpenAI prepaid balance.
- Historical/lifetime accounting depends on persistence being enabled.
- Backend Maven and Angular production builds remain required on phive-server for acceptance.

## 2026-10-26

### Pre-live paid AI readiness audit

- Re-audited market, forecast, trade decision, AI routing, billing, persistence and paid-call failure modes before API-key activation.
- Found Manifold fallback markets could be multi-year and were capable of affecting forecast bias despite lacking equivalent 24h probability-change data.
- Restricted Manifold to closing-month / <=35-day markets, sorted by 24h volume.
- Manifold is now neutral deterministic context only; static probabilities do not create trading direction.
- Only `POLYMARKET_GAMMA` may apply the bounded prediction-market confidence modifier in TradeSetup.
- Automatic AI interpretation is skipped for fallback forecast sources; fallback sources remain available for manual second-opinion analysis.
- Added `AI_AUTOMATIC_ENABLED=false` fail-closed master switch for automatic/event-triggered AI.
- Added `AI_MAX_MONTHLY_SPEND_USD=0` fail-closed paid API spend cap.
- Added WebFlux paid-call guardrail: live OpenAI POSTs require persistence plus a positive monthly cap with recorded spend below the cap.
- Exposed automatic AI state and monthly cap in AI guardrail API/UI.
- Code-level syntax sanity passed; phive-server Maven/Angular production builds and smoke tests are still required before API key provisioning.

### Final smoke-test confidence alignment

- Real server smoke tests passed for health, trade setup, forecast fallback, AI mock status, guardrails and cost center.
- Observed `WAIT` with confidence 86, which was technically possible because confidence and actionability were computed separately but was misleading for advisor UX.
- Added recommendation confidence caps: WAIT <= 60, WATCH <= 80, BUY/SELL <= 95.
- Added regression test for WAIT confidence cap.
- Requires one final backend test/build/restart on phive-server before live API provisioning.

### Prediction source health diagnostics

- Reconfirmed that Polymarket Gamma is prediction-market metadata/probability data, not a news API.
- Current Indonesian network restrictions mean CoinMind must not depend on bypassing Polymarket access.
- Added in-memory source health tracking for Gamma, CLOB and Manifold.
- Added `GET /api/v1/polymarket/source-health` reporting UNKNOWN/UP/EMPTY/UNAVAILABLE, last checked time, concise detail and signal count.
- Health endpoint is observational only and does not cause an extra upstream request.
- Existing compliant failover remains Gamma -> CLOB -> Manifold -> cache/neutral.
- Backend Maven validation on phive-server is required after pull.


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
