# CoinMind AI Frontend

Angular realtime dashboard aligned with the CoinMind Spring Boot market API.

## Stack

- Angular 22
- RxJS
- Angular HttpClient
- Server-Sent Events for the current MVP realtime feed
- Lightweight Charts 5.2 for candlesticks

## Current UI

- BTC / ETH / SOL market cards
- Realtime ticker price and 24-hour change
- Symbol selection
- Timeframes: 1m, 5m, 15m, 1h, 4h, 1d
- Realtime candlestick chart
- Current candle OHLC, volume and trade count
- Backend connection status
- Phase/progress panel for the future AI layer
- Responsive desktop/mobile layout

## Development

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm start
```

Open:

```text
http://localhost:4200
```

The Angular development server proxies `/api` to Spring Boot on `http://localhost:8080`.

## Realtime flow

```text
Binance
   ↓
Spring Boot / WebFlux
   ↓
REST snapshot + SSE
   ↓
Angular / RxJS
   ↓
Market cards + Lightweight Charts
```

## Current chart limitation

Until historical REST bootstrap is implemented, the browser begins with the latest candle snapshot and accumulates subsequent realtime candles during the current session.

The next backend milestone will provide historical candles so the chart and technical indicators are populated immediately after loading the page.
