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
