# Current Development State

_Last updated: 2026-10-01 (Asia/Jakarta)_

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

### Latest desktop redesign

The latest CSS pass attempts to make desktop a true dashboard instead of a stretched mobile layout.

At >= 1100px:

- workspace uses a 12-column grid
- chart occupies 8 columns
- Trade Decision occupies 4 columns
- Trade Decision is sticky
- Market Condition and Hourly Activity sit side-by-side inside the chart card
- Detail Market returns to full width below the main dashboard
- Market Microstructure and Polymarket use two columns
- News and AI Insight use a 7/5 split
- History remains full width

A duplicate AI Decision Review block was removed from the Trade Decision section.

Latest relevant commits:

```text
29c3c89289e380f3b6cbf1b5a03c584e0c02d226
style: redesign desktop dashboard composition

6d8906e78716227708c5c3eccfc4fddb5eb78349
fix: remove duplicate AI decision review section

76f8e52fe9903cc1d4b2fdc4183329d774afb12a
style: update dashboard section selectors for logical routing

404912ee3ca0fcf79dd76ea296f1f86e7fe9191f
fix: make desktop dashboard route logical without anchor jump
```

### Desktop issue still open

The latest desktop layout has NOT yet been visually accepted.

Previous desktop screenshot feedback:

- desktop still looked less polished than mobile
- chart consumed too much visual space
- the page felt vertically long
- content looked like mobile cards stretched across a large screen
- horizontal space was not used efficiently

The very next development task should be:

1. deploy the latest desktop composition
2. inspect the real rendered desktop UI
3. adjust desktop spacing, proportions, card hierarchy, and chart/sidebar ratio
4. do not change the working mobile layout unless strictly necessary

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
