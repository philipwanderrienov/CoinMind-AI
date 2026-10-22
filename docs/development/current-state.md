# Current Development State

_Last updated: 2026-10-21 (Asia/Jakarta)_

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
