package com.coinmind.trade.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TradeSetup(
        String symbol,
        String interval,
        String action,
        String side,
        int confidence,
        BigDecimal signalScore,
        String riskLevel,
        int alignmentScore,
        String alignmentLabel,
        String marketRegime,
        String marketActivity,
        BigDecimal marketActivityScore,
        List<TimeframeSignal> timeframes,
        BigDecimal marketPrice,
        BigDecimal entryLow,
        BigDecimal entryHigh,
        BigDecimal invalidationPrice,
        BigDecimal target1,
        BigDecimal target2,
        BigDecimal riskReward1,
        BigDecimal riskReward2,
        List<String> reasons,
        List<String> warnings,
        Instant generatedAt
) {
    public record TimeframeSignal(
            String interval,
            int weight,
            BigDecimal signalScore,
            BigDecimal trendScore,
            BigDecimal momentumScore,
            BigDecimal volatilityScore,
            String bias
    ) {
    }
}
