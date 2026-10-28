package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Independent fail-closed gate for prospective intraday signals.
 * No synthetic or historic replay inputs are eligible for live recommendations.
 * Gate approval does not replace exchange-specific liquidation and margin checks.
 */
public final class FuturesSignalGate {
    private static final Set<String> SYMBOLS = Set.of("BTCUSDT", "ETHUSDT", "SOLUSDT");
    private FuturesSignalGate() {}

    public static Decision evaluate(Request r, Instant now) {
        if (r == null || now == null) throw new IllegalArgumentException("Request and evaluation time required");
        if (r.symbol() == null || !SYMBOLS.contains(r.symbol())) return new Decision(false, "INVALID_SYMBOL");
        if (!"LIVE_EXCHANGE".equals(r.provenance())) return new Decision(false, "NON_LIVE_DATA");
        if (r.eventTime() == null || r.receivedAt() == null
                || r.eventTime().isAfter(r.receivedAt())
                || r.receivedAt().isAfter(now)
                || Duration.between(r.eventTime(), now).isNegative()
                || Duration.between(r.eventTime(), now).compareTo(Duration.ofSeconds(30)) > 0)
            return new Decision(false, "STALE_OR_INVALID_TIMESTAMP");
        if (r.venue() == null || r.venue().isBlank()
                || r.contractId() == null || r.contractId().isBlank()
                || !r.contractId().contains(r.symbol()))
            return new Decision(false, "CONTRACT_MISMATCH");
        if (r.markPrice() == null || r.markPrice().signum() <= 0
                || r.indexPrice() == null || r.indexPrice().signum() <= 0
                || r.openInterest() == null || r.openInterest().signum() <= 0
                || r.fundingRate() == null)
            return new Decision(false, "INCOMPLETE_DERIVATIVES_DATA");
        if (!r.executionCandlesFresh() || !r.orderBookFresh() || !r.contractRulesVerified()
                || !r.liquidationModelVerified())
            return new Decision(false, "EXECUTION_DATA_INCOMPLETE");
        return new Decision(true, "DATA_VALIDATED_ONLY");
    }

    public record Request(String symbol, String venue, String contractId, String provenance,
                          Instant eventTime, Instant receivedAt,
                          BigDecimal markPrice, BigDecimal indexPrice,
                          BigDecimal openInterest, BigDecimal fundingRate,
                          boolean executionCandlesFresh, boolean orderBookFresh,
                          boolean contractRulesVerified, boolean liquidationModelVerified) {}
    public record Decision(boolean eligible, String reason) {}
}
