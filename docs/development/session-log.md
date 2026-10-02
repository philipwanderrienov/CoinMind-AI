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
