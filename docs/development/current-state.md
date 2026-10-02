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
#market
```

Important: `#market` is intentionally NOT a real DOM anchor. This avoids the browser automatically jumping past the CoinMind header on first load.

On first mobile access:

- CoinMind header remains visible
- application status strip remains visible
- Market is the active menu
- tapping Market explicitly scrolls to the internal `#market-detail` section

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

The desktop route uses logical:

```text
#dashboard
```

The real chart section DOM id is:

```text
#dashboard-section
```

This separation prevents the browser from auto-jumping directly to the chart on first access.

On first desktop access:

- Dashboard is active
- viewport should remain at the top
- header, status strip, navigation, and ticker cards should remain visible

### Latest local desktop implementation (2026-10-02)

Desktop composition was refined only inside the existing >=1100px CSS block. Earlier responsive rules, HTML, section IDs, logical routes, floating selector, and TypeScript remain unchanged.

- Header, status, navigation, ticker, and content share a centered container capped at 1440px with 24px side gutters, including wide screens.
- Header/navigation/tickers are more compact; ticker prices sit beside asset labels.
- Chart remains the primary 8-column area beside the 4-column Trade Decision sidebar. Sidebar uses normal flow so long decisions stay reachable on shorter screens.
- Chart height is 360–460px according to viewport height, including indicator states. Market Condition and Hourly Activity remain side-by-side with tighter cards and a shorter activity plot.
- Trade Decision retains all content with compact overview, price/status, alignment/regime, trade levels, and detail grids.
- Detail Market retains five-column timeframe/technical grids and paired Market Microstructure/Polymarket, with tighter spacing.
- News and AI Insight retain the 7/5 split; insight confidence width adapts to the sidebar.
- History stays full-width with shorter rows; evaluation summary and AI usage share a row when Performance is expanded.

This work is local only: **do not deploy or push until the user requests it**. Production still serves the previous implementation. Untracked `deploy.sh` and `frontend/package-lock.json` predate this work and are excluded.

Validation: Angular production build status is recorded in the session log. Source comparison confirms all CSS before the >=1100px composition block is unchanged. Rendered visual acceptance is still pending; no browser screenshots were captured in this session.

Next checks: review 1100/1280/1440/1920px desktop widths and short viewports; verify loaded and empty data, WAIT/BUY/SELL decisions, long AI text, indicators, expanded News/History, and navigation. Confirm mobile at 390/760px and the unchanged 761–1099px layout, especially boundary resizing.

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
5. commit/push after checks when authorized; the current desktop work must remain local without deployment or push per user instruction.

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
