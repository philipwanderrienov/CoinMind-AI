# Current Development State

_Last updated: 2026-10-21 (Asia/Jakarta)_

Dokumen ini adalah sumber kebenaran (handoff) untuk state CoinMind-AI saat ini. Baca file ini sebelum melakukan perubahan.

## Product state

CoinMind-AI adalah aplikasi crypto market intelligence dan decision-support untuk:

- BTCUSDT
- ETHUSDT
- SOLUSDT

Decision Engine deterministic/multi-timeframe tetap menjadi sumber keputusan utama. AI News Intelligence dan Polymarket hanya berfungsi sebagai bounded supporting signals dan tidak boleh menciptakan BUY/SELL secara independen.

Primary application views:

```text
Dashboard
Trade Decision
News
AI & Usage
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

## Polymarket state

Polymarket adalah prediction-market intelligence, bukan sumber berita.

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
- fallback ke last cached result bila ada
- fallback ke neutral result bila cache belum ada
- timeout/error expected dicatat sebagai warning ringkas tanpa full stack trace
- failure Polymarket tidak boleh menjatuhkan backend atau Trade Decision

Config:

```text
POLYMARKET_BASE_URL
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

AI config mendukung:

- provider/model status
- API key state
- automatic-call guardrails
- manual analysis
- decision review
- news intelligence
- usage/token accounting
- estimated cost
- history/evaluation

Default config saat ini:

```text
AI_PROVIDER=openai
AI_MODEL=gpt-5.6-luna
AI_REASONING_EFFORT=low
AI_MAX_OUTPUT_TOKENS=400
```

## Known open issues / priorities

Urutan prioritas saat ini:

1. **Validate Polymarket hardening di server**
   - pull `main`
   - Maven build
   - restart backend
   - test Polymarket + trade setup
   - pastikan timeout sekitar beberapa detik dan tidak ada stack trace spam

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
   - setelah visual baseline diterima, hapus responsive override lama yang sudah obsolete
   - jangan terus menambah append-only override tanpa membersihkan rule konflik

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
