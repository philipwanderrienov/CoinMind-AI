# Current Development State

_Last updated: 2026-10-02 (Asia/Jakarta)_

This file is the handoff note for continuing CoinMind development in a new ChatGPT conversation. Read this first before making changes.

## Current public access

Primary public test URL:

```text
https://phive-server.tail597574.ts.net/
```

This is exposed through Tailscale Funnel and currently proxies:

```text
https://phive-server.tail597574.ts.net/
  -> http://127.0.0.1:80
```

Funnel was verified with:

- public DNS resolution
- valid Let's Encrypt certificate
- HTTP/2 200 response from nginx

Private/Tailscale URL still exists:

```text
http://100.89.143.40/
```

## Current deployment workflow

Frontend:

```bash
cd /home/phive/Documents/CoinMind-AI
git pull origin main

cd frontend
npm run build

sudo rm -rf /var/www/coinmind/*
sudo cp -r dist/coinmind-web/browser/* /var/www/coinmind/

sudo systemctl reload nginx
```

Backend, when backend code changes:

```bash
cd /home/phive/Documents/CoinMind-AI/backend
mvn clean package -DskipTests
sudo systemctl restart coinmind-backend
sudo systemctl status coinmind-backend
```

## Mobile UX state

Mobile is currently the more polished view and should not be regressed while fixing desktop.

### Navigation

The visible mobile section order follows the actual visual card order:

```text
Market -> News -> History -> Dashboard -> Signals
```

The mobile section navigation is sticky while scrolling.

Active navigation styling:

- only one item should be active
- active state uses purple text/background
- no active underline

Initial mobile route uses the logical hash:

```text
#section=market
```

The logical entry hash intentionally does not match a DOM id. This prevents first-load anchor jumping while the visible Market menu still starts active. Explicit menu clicks use real section anchors.

On first mobile access:

- CoinMind header remains visible
- application status strip remains visible
- Market is the active menu
- tapping Market explicitly scrolls to the `#market` Detail market section

### Floating coin selector

On mobile, the BTC/ETH/SOL ticker row is replaced by a floating selector.

Behavior:

- draggable like a chat head
- tap opens coin dropdown
- drag moves the pill
- release snaps smoothly to the nearest left/right edge
- cannot remain in the middle of the screen
- vertical position is preserved
- last position is stored in localStorage
- dropdown opens downward if the selector is near the top and upward if near the bottom
- desktop does not use the floating selector

## Desktop UX state

Desktop is currently the main area still being refined.

The desktop first-load route uses logical:

```text
#section=dashboard
```

Explicit menu links now map directly to semantic DOM ids:

```text
#dashboard
#signals
#market
#news
#history
```

First load normalizes the URL to the viewport-specific logical hash before section ids render, including reloads of real anchors left by previous clicks. Menu clicks and hash changes during a running session target the real sections with the measured offset.

On first desktop access:

- Dashboard is active
- viewport should remain at the top
- header, status strip, navigation, and ticker cards should remain visible

### Latest desktop implementation (2026-10-02)

Desktop structure was simplified to remove the confusing right-side Signals sidebar and reduce scroll misalignment.

Current desktop section flow is literal and matches navigation:

```text
Dashboard -> Signals -> Market -> News -> History
```

- Dashboard/chart is full-width.
- Signals/Trade Decision is full-width directly below Dashboard.
- Signals is explicitly forced to normal document flow; no sticky/right sidebar behavior.
- Market remains full-width and contains timeframe, technical, microstructure, and Polymarket context.
- News and AI Insight may still share desktop horizontal space inside the lower content stack.
- History remains full-width.
- Desktop section scrolling uses the measured desktop menu height + 18px; only the section menu persists at the viewport top.
- Menu hrefs, active state names, and DOM ids are aligned to the same semantic section names.
- Mobile visual order and floating selector behavior are preserved.

### Navigation correction (local source, 2026-10-02)

- Semantic hrefs and unique DOM ids already match Dashboard/chart, Signals/Trade Decision, Market/Detail market, News/News sentiment, and History/Performance & advanced.
- First load stays at scroll position zero: Dashboard active on desktop, Market active on mobile. Incoming hashes are normalized to the corresponding logical entry hash, including real-anchor reloads.
- Section tracking starts after the view exists and sorts rendered section positions instead of assuming responsive document order; a 2px tolerance covers scroll rounding.
- Menu clicks and in-session hash changes share the same navigation handling. Desktop offset is measured menu height + 18px; mobile offset remains measured sticky-nav height + 12px. CSS anchor scroll margin shares that value.
- Click selection stays stable during smooth scrolling and at a clamped document end; wheel/touch/keyboard/manual scrolling releases selection so tracking follows section positions. A cancellable finishing correction accounts for rendering movement during the scroll.
- HTML/layout, mobile order/sticky navigation/floating selector, API, business logic, and backend remain unchanged.
- Source simulation checks cover 390/760/761/1100/1440/1920 widths, all five menu targets, entry behavior, manual tracking, hash changes, rounding, and document-end clamping. Actual browser/visual verification is pending; no additional packages or browsers are authorized for the resumed task.
- Production build passed with Node 22.23.2; existing Angular NG8102/NG8107 template warnings remain. Deployment, commit, and push are prohibited for this task; these corrections remain local. The earlier deployment attempt stopped at sudo authentication before any production files changed.

Next check: verify actual desktop/mobile scrolling and asynchronous content behavior when browser access is available.

## News and AI state

News is real, not dummy.

Current pipeline:

```text
RSS feeds
  -> relevant BTC/ETH/SOL articles
  -> PostgreSQL/history
  -> deterministic relevance + sentiment
  -> optional Luna AI News Intelligence
```

AI News Intelligence is on-demand and returns:

- BULLISH / BEARISH / NEUTRAL
- importance
- confidence
- impact horizon
- summary
- catalysts
- risks
- per-article impact

AI news is used as a bounded modifier in the Decision Engine. It cannot independently create a BUY/SELL signal.

## Polymarket state

Polymarket is treated as prediction-market intelligence, NOT as a news source.

Current backend:

- searches relevant BTC/ETH/SOL markets
- uses active/non-closed directional markets
- reads probability, 24h probability change, liquidity, and volume
- generates a bounded BULLISH/BEARISH/NEUTRAL modifier
- refreshes periodically
- contribution to trade confidence is intentionally small

Endpoint:

```text
GET  /api/v1/polymarket/{symbol}
POST /api/v1/polymarket/{symbol}/refresh
```

## Decision Engine guardrails

Technical/multi-timeframe analysis remains the primary source of decisions.

AI News and Polymarket only modify confidence within limited ranges.

AI News:

- aligned strong news: small confidence boost
- conflicting strong news: confidence reduction
- high-importance/high-confidence contradiction may downgrade BUY/SELL to WATCH

Polymarket:

- strong aligned signal: up to about +3 confidence
- strong opposing signal: down to about -3 confidence
- never creates BUY/SELL independently

## Important frontend files

```text
frontend/src/app/app.component.ts
frontend/src/app/app.component.html
frontend/src/app/app.component.css
frontend/src/app/core/models/market.models.ts
frontend/src/app/core/services/market-api.service.ts
```

Most recent UI work is concentrated in `app.component.*`.

## Continuation instruction

When continuing development in a new conversation:

1. read this file first
2. inspect the latest `main` branch before editing
3. preserve current mobile behavior
4. prioritize desktop layout quality next
5. use the public Tailscale Funnel URL for user-side testing
6. update this handoff file again whenever a major UX/architecture change is completed


## ChatGPT / Codex synchronization

GitHub is the shared source of truth between the main ChatGPT conversation and Codex running directly on `phive-server`.

Root instructions:

```text
AGENTS.md
```

Shared handoff files:

```text
docs/development/current-state.md
docs/development/session-log.md
```

Synchronization rule for every meaningful change:

1. implement the requested change;
2. update `current-state.md`;
3. append a concise entry to `session-log.md`;
4. run relevant build/tests;
5. commit/push after checks when authorized; the current navigation work must remain local without commit, deployment, or push per user instruction.

A new Codex session should be started from:

```bash
cd /home/phive/Documents/CoinMind-AI
git pull origin main
codex
```

Codex should read `AGENTS.md` automatically and must follow its handoff rules.

## Next operational task

Install and authenticate Codex CLI on `phive-server`, then verify it can:

- open the CoinMind repository;
- read `AGENTS.md`;
- read `docs/development/current-state.md`;
- inspect local server/deployment state when specifically requested.

Codex is intended as an on-demand server-side inspection/development tool, not the primary day-to-day conversation channel.

### Desktop persistent section menu (local, 2026-10-02)

- At widths >=761px, only the existing desktop menu becomes fixed at top:0 when its original slot reaches the viewport top. The slot preserves its height; header/status/tickers remain in normal flow.
- The final desktop shell rule explicitly disables shell stickiness and backdrop filtering, preventing a fixed-containing-block conflict. Mobile sticky rules and floating selector remain unchanged.
- Navigation targets, CSS scroll margin, and active-section tracking share measured menu height + 18px on desktop. Mobile keeps menu height + 12px. Entry logic remains top/Dashboard on desktop and top/Market on mobile.
- No deployment, commit, or push. Browser verification of this new persistence behavior remains pending.

- Validation for desktop persistence: standard `npm run build` passed on retry with Node 22.23.2 (first attempt hit Google Fonts DNS EAI_AGAIN); existing Angular template warnings remain. New browser/visual checks remain pending.
