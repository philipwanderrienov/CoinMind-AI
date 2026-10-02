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

## Navigation / responsive shell

CoinMind now uses a view-based application sidebar instead of the old horizontal section navigator.

Primary views:

```text
Dashboard
Trade Decision
News
AI & Usage
```

### Desktop

- persistent left sidebar, 252px wide
- horizontal section navigation has been removed
- clicking a sidebar item switches the visible application view instead of smooth-scrolling through a long combined page
- selected coin summary remains visible in the sidebar footer
- Dashboard contains chart, market condition, hourly activity, and Detail Market context
- Trade Decision focuses on the full deterministic decision explanation, entry/risk/targets, alignment, reasons, warnings, and AI review
- News shows the configured coin news/sentiment area
- AI & Usage shows AI Insight plus engine/model details, token usage, cost estimates, evaluation, and analysis history

### Mobile

- the sidebar becomes an off-canvas drawer opened from a hamburger button in the header
- tapping a menu closes the drawer and switches view
- the existing draggable floating coin selector remains available
- the old sticky horizontal section navigation is removed

Navigation is hash-based at the view level:

```text
#dashboard
#trade
#news
#ai
```

This view-based model intentionally removes the previous section-anchor/scroll-tracking complexity.

### Validation status

The sidebar implementation is pushed to `main`. Angular production build is still required on `phive-server` before deployment/acceptance. Do not treat the current sidebar pass as visually accepted until desktop and mobile have both been rendered and checked.

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


### Mobile sidebar/header stabilization (2026-10-02)

After the first sidebar pass, the mobile header rendered with the hamburger on its own row, the brand pushed down, status cards colliding, and inconsistent spacing before content.

Latest CSS stabilization now enforces:
- a single 64px mobile header row containing hamburger + CoinMind brand;
- fixed, balanced hamburger and logo sizing;
- a separate two-column status strip beneath the header;
- normal-flow mobile header/status positioning with no accidental overlap;
- hidden legacy sticky-controls block on mobile;
- normalized main/content width and spacing;
- mobile content cards constrained to the viewport width.

Latest commit: `ee472d3 fix: stabilize mobile header and sidebar layout`.

Production Angular build and browser visual verification are still required on `phive-server`.
