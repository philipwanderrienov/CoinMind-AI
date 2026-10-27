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
