package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Experimental deterministic intraday decision rules for offline evaluation only.
 * Inputs must be calculated solely from completed candles at or before the decision time.
 * This does not generate live Futures recommendations or exchange orders.
 */
public final class FuturesIntradayDecisionEngine {
    private static final BigDecimal ENTRY_THRESHOLD = new BigDecimal("55");
    private static final BigDecimal CONFIRMATION_THRESHOLD = new BigDecimal("20");
    private FuturesIntradayDecisionEngine() {}

    public static Decision evaluate(Input input) {
        if (input == null) throw new IllegalArgumentException("Input required");
        if (!"SYNTHETIC_FIXTURE".equals(input.provenance())
                && !"HISTORICAL_REPLAY".equals(input.provenance()))
            return new Decision("WAIT", "NON_OFFLINE_DATA", BigDecimal.ZERO);
        if (input.fiveMinuteScore() == null || input.fifteenMinuteScore() == null
                || input.oneHourTrend() == null || input.fourHourTrend() == null)
            return new Decision("WAIT", "INCOMPLETE_TIMEFRAMES", BigDecimal.ZERO);
        if (!input.completedCandlesOnly())
            return new Decision("WAIT", "UNCONFIRMED_CANDLES", BigDecimal.ZERO);

        BigDecimal entry = input.fiveMinuteScore().multiply(new BigDecimal("0.6"))
                .add(input.fifteenMinuteScore().multiply(new BigDecimal("0.4")));
        int sign = entry.signum();
        if (sign == 0 || entry.abs().compareTo(ENTRY_THRESHOLD) < 0)
            return new Decision("WAIT", "WEAK_ENTRY", entry);
        if (input.oneHourTrend().signum() != sign || input.fourHourTrend().signum() != sign
                || input.oneHourTrend().abs().compareTo(CONFIRMATION_THRESHOLD) < 0
                || input.fourHourTrend().abs().compareTo(CONFIRMATION_THRESHOLD) < 0)
            return new Decision("WAIT", "HIGHER_TIMEFRAME_CONFLICT", entry);
        return new Decision(sign > 0 ? "WATCH_LONG" : "WATCH_SHORT",
                "OFFLINE_CANDIDATE_ONLY", entry);
    }

    public record Input(String provenance, boolean completedCandlesOnly,
                        BigDecimal fiveMinuteScore, BigDecimal fifteenMinuteScore,
                        BigDecimal oneHourTrend, BigDecimal fourHourTrend) {}
    public record Decision(String action, String reason, BigDecimal entryScore) {}
}
