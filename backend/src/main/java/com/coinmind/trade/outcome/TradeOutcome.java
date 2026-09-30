package com.coinmind.trade.outcome;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeOutcome(
        String id,
        String symbol,
        String interval,
        String action,
        String side,
        int confidence,
        BigDecimal signalScore,
        int alignmentScore,
        String marketRegime,
        String marketActivity,
        BigDecimal marketActivityScore,
        BigDecimal entryLow,
        BigDecimal entryHigh,
        BigDecimal invalidationPrice,
        BigDecimal target1,
        BigDecimal target2,
        String status,
        Instant entryHitAt,
        BigDecimal entryPrice,
        Instant target1HitAt,
        Instant target2HitAt,
        Instant stoppedAt,
        Instant expiredAt,
        Instant generatedAt,
        Instant evaluatedAt
) {
}
