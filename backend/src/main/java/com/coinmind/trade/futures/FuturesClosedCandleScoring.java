package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Offline-only deterministic momentum scoring from previously closed candles.
 * Time is the candle OPEN; a candle is usable only when open + timeframe <= decisionTime.
 * No exchange candles, order execution, or prediction of future outcomes is implied.
 */
public final class FuturesClosedCandleScoring {
    private static final MathContext MC = MathContext.DECIMAL64;
    private FuturesClosedCandleScoring() {}

    public static FuturesIntradayDecisionEngine.Input score(String provenance, Instant decisionTime,
            List<FuturesReplayEngine.Candle> fiveMinutes,
            List<FuturesReplayEngine.Candle> fifteenMinutes,
            List<FuturesReplayEngine.Candle> oneHour,
            List<FuturesReplayEngine.Candle> fourHours) {
        if (!"SYNTHETIC_FIXTURE".equals(provenance) && !"HISTORICAL_REPLAY".equals(provenance))
            throw new IllegalArgumentException("Offline provenance required");
        if (decisionTime == null) throw new IllegalArgumentException("Decision time required");
        return new FuturesIntradayDecisionEngine.Input(provenance, true,
                momentum(fiveMinutes, Duration.ofMinutes(5), decisionTime),
                momentum(fifteenMinutes, Duration.ofMinutes(15), decisionTime),
                momentum(oneHour, Duration.ofHours(1), decisionTime),
                momentum(fourHours, Duration.ofHours(4), decisionTime));
    }

    private static BigDecimal momentum(List<FuturesReplayEngine.Candle> candles,
            Duration duration, Instant decisionTime) {
        if (candles == null || candles.size() < 3)
            throw new IllegalArgumentException("At least three closed candles per timeframe required");
        Instant previous = null;
        for (var candle : candles) {
            if (candle == null || candle.time() == null || candle.open() == null ||
                    candle.high() == null || candle.low() == null || candle.close() == null)
                throw new IllegalArgumentException("Incomplete candle");
            if (previous != null && !candle.time().isAfter(previous))
                throw new IllegalArgumentException("Candles must be strictly ascending");
            previous = candle.time();
            if (candle.time().plus(duration).isAfter(decisionTime))
                throw new IllegalArgumentException("Unclosed or future candle");
            if (candle.low().signum() <= 0 || candle.low().compareTo(candle.high()) > 0 ||
                    candle.open().compareTo(candle.low()) < 0 || candle.open().compareTo(candle.high()) > 0 ||
                    candle.close().compareTo(candle.low()) < 0 || candle.close().compareTo(candle.high()) > 0)
                throw new IllegalArgumentException("Invalid OHLC");
        }
        // Normalized close-to-close return over the two most recent intervals.
        BigDecimal first = candles.get(candles.size() - 3).close();
        BigDecimal last = candles.get(candles.size() - 1).close();
        return last.subtract(first).multiply(new BigDecimal("10000"), MC)
                .divide(first, MC).max(new BigDecimal("-100")).min(new BigDecimal("100"));
    }
}
