# Market Microstructure

CoinMind now ingests realtime microstructure data in addition to ticker and candlestick feeds.

## Binance streams

For each configured symbol:

- `@bookTicker`
- `@aggTrade`

Initial symbols:

- BTCUSDT
- ETHUSDT
- SOLUSDT

## Data flow

```text
Binance @bookTicker
        ↓
best bid / best ask
        ↓
spread + mid price
        ↓
OrderBookService

Binance @aggTrade
        ↓
price / quantity / maker flag
        ↓
aggressor BUY / SELL classification
        ↓
TradeService
        ↓
rolling last 500 trades per symbol
```

## Initial microstructure features

CoinMind derives:

- Best bid
- Best ask
- Absolute spread
- Mid price
- Spread in basis points
- Rolling aggressive buy quote volume
- Rolling aggressive sell quote volume
- Buy/sell volume ratio

The buy/sell classification uses Binance's buyer-is-maker flag:

```text
buyerIsMaker = false → aggressor BUY
buyerIsMaker = true  → aggressor SELL
```

These metrics are intended as inputs for the future technical/AI market context, not as standalone trading signals.

## API

```text
GET /api/v1/market/microstructure/order-books
GET /api/v1/market/microstructure/order-books/BTCUSDT
GET /api/v1/market/microstructure/trades/BTCUSDT/latest
GET /api/v1/market/microstructure/BTCUSDT/summary

GET /api/v1/market/microstructure/order-books/stream
GET /api/v1/market/microstructure/trades/stream
```
