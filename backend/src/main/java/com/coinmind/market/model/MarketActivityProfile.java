package com.coinmind.market.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MarketActivityProfile(
        String symbol,
        String timezone,
        int sampleDays,
        int currentHour,
        String currentActivity,
        BigDecimal currentScore,
        List<Integer> peakHours,
        List<HourlyActivity> hours,
        Instant calculatedAt
) {
    public record HourlyActivity(
            int hour,
            BigDecimal activityScore,
            String activityLevel,
            BigDecimal averageQuoteVolume,
            BigDecimal averageTradeCount,
            BigDecimal averageVolatilityPct,
            int samples
    ) {
    }
}
