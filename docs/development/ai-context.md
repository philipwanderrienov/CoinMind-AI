# AI Context Architecture

CoinMind uses GPT-5.6 Luna as the first production AI provider. The AI layer is
event-driven and receives compact structured market features rather than raw tick
streams or chart screenshots.

## Flow

```text
Ticker + candles + indicators + order flow + ranked news
                        ↓
                MarketContextBuilder
                        ↓
              compact MarketContext
                        ↓
          event trigger / manual request
                        ↓
                 GPT-5.6 Luna
                        ↓
              structured JSON result
                        ↓
         analysis history + usage log
```

## Trigger policy

Automatic analysis is intentionally limited to meaningful events:

- finalized 15m candle
- finalized 1h candle
- finalized 4h candle
- finalized 1d candle
- fresh high-relevance BTC/ETH/SOL news with relevance >= 0.70
- high-relevance-news triggers use a 15-minute cooldown per symbol

The UI can also run a manual **Analyze now** request.

Selecting a symbol or timeframe only reads the latest persisted analysis and does not
create a paid model call.

## Provider

```text
AI_ENABLED=true
AI_PROVIDER=openai
AI_MODEL=gpt-5.6-luna
AI_REASONING_EFFORT=low
AI_MAX_OUTPUT_TOKENS=400
```

The provider uses the OpenAI Responses API with a strict JSON schema. Output contains:

- market bias
- confidence 0-100
- concise summary
- up to four supporting factors
- up to four risk factors

## Usage monitoring

Every successful Luna response records:

- input tokens
- cached input tokens
- output tokens
- reasoning tokens
- total tokens
- trigger type
- estimated input/output/total USD cost

The dashboard API is:

```text
GET /api/v1/ai/usage/summary?days=14
```

The pricing values are configuration rather than hard-coded business logic so they
can be updated if provider pricing changes.

## Billing reminder and Web Push

CoinMind supports an internal configurable monthly AI billing date. It does not infer
the provider's actual billing date.

```text
AI_BILLING_REMINDER_ENABLED=true
AI_BILLING_DUE_DAY=1
AI_BILLING_REMINDER_DAYS=2
AI_BILLING_TIMEZONE=Asia/Jakarta
```

The daily scheduler sends H-2, H-1 and due-day reminders when Web Push is configured.
Delivery history is persisted to prevent duplicate SENT reminders.

Browser Push requires a secure HTTPS origin. Configure VAPID keys and access the PWA
through HTTPS before enabling notifications.

Useful endpoints:

```text
GET  /api/v1/ai/context/BTCUSDT/1h
GET  /api/v1/ai/analysis/latest/BTCUSDT/1h
POST /api/v1/ai/analysis/BTCUSDT/1h
GET  /api/v1/ai/usage/summary?days=14

GET  /api/v1/billing/status

GET  /api/v1/push/public-key
POST /api/v1/push/subscribe
POST /api/v1/push/test
```
