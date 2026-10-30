package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Pure presentation snapshot for OFFLINE paper risk only.
 * Inputs must be supplied from a trusted replay ledger, not user-provided "live" state.
 */
public final class FuturesPaperRiskStatus {
    private static final MathContext MC = MathContext.DECIMAL64;
    private FuturesPaperRiskStatus() {}

    public static Snapshot evaluate(Input input) {
        if (input == null || (!"SYNTHETIC_FIXTURE".equals(input.provenance())
                && !"HISTORICAL_REPLAY".equals(input.provenance()))
                || input.asOf() == null || input.dayStartEquity() == null
                || input.dayStartEquity().signum() <= 0 || input.currentEquity() == null
                || input.dailyRealizedPnl() == null || input.dailyLossLimitPercent() == null
                || input.dailyLossLimitPercent().signum() <= 0
                || input.dailyLossLimitPercent().compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("Complete offline daily risk state required");
        BigDecimal limit = input.dayStartEquity().multiply(input.dailyLossLimitPercent(), MC)
                .divide(new BigDecimal("100"), MC);
        BigDecimal loss = input.dailyRealizedPnl().negate().max(BigDecimal.ZERO);
        BigDecimal remaining = limit.subtract(loss).max(BigDecimal.ZERO);
        boolean locked = input.currentEquity().signum() <= 0 || loss.compareTo(limit) >= 0;
        String status = locked ? "DAILY_RISK_LOCK"
                : input.dailyRealizedPnl().signum() < 0 ? "LOSS_MONITORING" : "NORMAL";
        return new Snapshot("OFFLINE_ONLY", false, status, !locked,
                input.asOf().atZone(ZoneOffset.UTC).toLocalDate(), limit,
                loss, remaining, input.currentEquity(),
                "Realized-only paper snapshot; does not validate positions, unrealized PnL or live account state.");
    }

    public record Input(String provenance, Instant asOf, BigDecimal dayStartEquity,
                        BigDecimal currentEquity, BigDecimal dailyRealizedPnl,
                        BigDecimal dailyLossLimitPercent) {}
    public record Snapshot(String mode, boolean liveSignalEligible, String status,
                           boolean newEntriesAllowed, LocalDate riskDayUtc,
                           BigDecimal dailyLossLimitQuote, BigDecimal realizedLossQuote,
                           BigDecimal remainingLossBudgetQuote, BigDecimal currentEquity,
                           String limitations) {}
}
