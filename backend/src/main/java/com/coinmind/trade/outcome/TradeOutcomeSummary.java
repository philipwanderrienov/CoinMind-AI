package com.coinmind.trade.outcome;

public record TradeOutcomeSummary(
        String symbol,
        int total,
        int pending,
        int entryHit,
        int target1Hit,
        int target2Hit,
        int stopped,
        int expired,
        double entryRatePct,
        double target1RatePct,
        double target2RatePct,
        double stopRatePct
) {
}
