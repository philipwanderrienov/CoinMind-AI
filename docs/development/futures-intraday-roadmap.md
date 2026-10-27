# Futures Intraday Advisor readiness (2026-10-27)

Status: PARTIAL — not production ready. Advisor-only, no order execution.

## Implemented on feature branch
- Paper-only futures risk preview endpoint: POST /api/v1/futures/risk/preview.
- Inputs: equity, side, entry, stop, risk percent, leverage, fee and slippage assumptions.
- Conservative caps: 1% risk per idea, 5x leverage and 20% initial margin-to-equity ceiling.
- Rejects incorrectly oriented stops and invalid numerical inputs.
- Estimated stop loss includes round-trip fee/slippage assumptions; it is not an exchange liquidation model.
- Unit tests added; must run Maven on phive-server before merge or deploy.

## Remaining blockers before futures signal readiness
1. Replace spot Binance feed with explicit futures contract/venue data and stable funding/open-interest/long-short-ratio collection. Check Indonesian access/licensing and never bypass provider restrictions.
2. Introduce mark/index prices, contract specifications (tick/step/min-notional), basis, maintenance margin tiers and exchange-specific liquidation-buffer estimation.
3. Update 5m/15m execution analysis and 1h/4h trend confirmation using backtested weights instead of arbitrarily reweighting.
4. Integrate live spread, depth, aggressive order flow, trading fees and realized slippage; avoid ungrounded liquidation-heatmap claims.
5. Hard-gate stale/missing futures inputs to WAIT/NO_DATA; prohibit misleading confidence and suppress BUY/SELL recommendations until validated.
6. Add real intraday time exits, daily loss cap, cooldown, max concurrent exposure, event blackout and isolated-margin assumptions.
7. Backtest after-cost across walk-forward regimes, run paper-trade journal and forward test before real-money usage.
8. Remove ALL Polymarket Gamma/CLOB and Polymarket-named code/config/routes/types/UI/AI references, migrate the current Manifold parsing to Manifold-only services with context-only semantics.
9. Run Java unit/integration tests, Angular build, server smoke tests and update production deployment separately.

## Safety
Do not label CoinMind as fully futures-ready. No automated futures trading, position creation, exchange API keys or order placement.
