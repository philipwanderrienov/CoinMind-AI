package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.util.List;

/**
 * Stateless, fail-closed preflight for a futures intraday advisor.
 * Caller must provide authoritative account/position state; no orders are placed.
 */
public final class FuturesIntradayRiskGuard {
    private FuturesIntradayRiskGuard() {}

    public static Decision evaluate(Request request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        if (request.equity() == null || request.equity().signum() <= 0 ||
                request.dailyRealizedPnl() == null || request.dailyLossLimitPercent() == null ||
                request.dailyLossLimitPercent().signum() <= 0 ||
                request.dailyLossLimitPercent().compareTo(new BigDecimal("100")) > 0 ||
                request.openPositions() < 0 || request.maxOpenPositions() < 1 ||
                request.consecutiveLosses() < 0 || request.maxConsecutiveLosses() < 1) {
            throw new IllegalArgumentException("Invalid account risk state");
        }
        if (!"LIVE_EXCHANGE".equals(request.provenance()) ||
                !request.futuresDataFresh() || !request.accountStateFresh()) {
            return new Decision(false, "NO_DATA", "Authenticated live futures/account data required");
        }
        BigDecimal allowedLoss = request.equity()
                .multiply(request.dailyLossLimitPercent())
                .divide(new BigDecimal("100"), java.math.MathContext.DECIMAL64);
        if (request.dailyRealizedPnl().negate().compareTo(allowedLoss) >= 0) {
            return new Decision(false, "DAILY_LOSS_LIMIT", "Daily realized loss threshold reached");
        }
        if (request.openPositions() >= request.maxOpenPositions()) {
            return new Decision(false, "MAX_POSITIONS", "Open position limit reached");
        }
        if (request.consecutiveLosses() >= request.maxConsecutiveLosses()) {
            return new Decision(false, "LOSS_COOLDOWN", "Consecutive loss cooldown required");
        }
        return new Decision(true, "PASS", "Risk preflight passed; does not authorize order execution");
    }

    public record Request(String provenance, boolean futuresDataFresh, boolean accountStateFresh,
            BigDecimal equity, BigDecimal dailyRealizedPnl, BigDecimal dailyLossLimitPercent,
            int openPositions, int maxOpenPositions, int consecutiveLosses, int maxConsecutiveLosses) {}
    public record Decision(boolean allowed, String code, String reason) {}
}
