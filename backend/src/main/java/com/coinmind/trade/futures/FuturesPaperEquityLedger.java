package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline-only, chronological realized-equity accounting.
 * A daily stop blocks all further entries for that UTC day; there is no live order routing.
 * Supply realized outcomes chronologically, at the moment they become known.
 */
public final class FuturesPaperEquityLedger {
    private static final MathContext MC = MathContext.DECIMAL64;
    private FuturesPaperEquityLedger() {}

    public static Report evaluate(BigDecimal initialEquity, BigDecimal dailyLossLimitPercent,
                                  List<Trade> trades) {
        if (initialEquity == null || initialEquity.signum() <= 0
                || dailyLossLimitPercent == null || dailyLossLimitPercent.signum() <= 0
                || dailyLossLimitPercent.compareTo(new BigDecimal("100")) > 0
                || trades == null)
            throw new IllegalArgumentException("Invalid paper ledger configuration");
        BigDecimal equity = initialEquity;
        BigDecimal peak = initialEquity;
        BigDecimal drawdown = BigDecimal.ZERO;
        BigDecimal dayStart = initialEquity;
        BigDecimal dayPnl = BigDecimal.ZERO;
        LocalDate day = null;
        Instant last = null;
        int accepted = 0;
        int blocked = 0;
        List<Point> points = new ArrayList<>();
        for (Trade trade : trades) {
            if (trade == null || trade.realizedAt() == null || trade.netPnl() == null
                    || (!"SYNTHETIC_FIXTURE".equals(trade.provenance())
                        && !"HISTORICAL_REPLAY".equals(trade.provenance()))
                    || (last != null && !trade.realizedAt().isAfter(last)))
                throw new IllegalArgumentException("Only strictly chronological offline realized trades accepted");
            last = trade.realizedAt();
            LocalDate currentDay = trade.realizedAt().atZone(ZoneOffset.UTC).toLocalDate();
            if (!currentDay.equals(day)) {
                day = currentDay;
                dayStart = equity;
                dayPnl = BigDecimal.ZERO;
            }
            BigDecimal limit = dayStart.multiply(dailyLossLimitPercent, MC)
                    .divide(new BigDecimal("100"), MC);
            if (dayPnl.negate().compareTo(limit) >= 0 || equity.signum() <= 0) {
                blocked++;
                points.add(new Point(trade.realizedAt(), equity, false, "DAILY_STOP_OR_DEPLETED"));
                continue;
            }
            // A prior realized loss can only block later trades, never retroactively cancel itself.
            equity = equity.add(trade.netPnl(), MC);
            dayPnl = dayPnl.add(trade.netPnl(), MC);
            peak = peak.max(equity);
            drawdown = drawdown.max(peak.subtract(equity, MC));
            accepted++;
            points.add(new Point(trade.realizedAt(), equity, true, "ACCEPTED"));
        }
        return new Report("OFFLINE_ONLY", false, initialEquity, equity, drawdown,
                accepted, blocked, List.copyOf(points),
                "Realized-only accounting; timestamps are externally supplied. No compounding position sizing, "
                + "unrealized PnL, funding, liquidation or pre-entry account synchronization.");
    }

    public record Trade(String provenance, Instant realizedAt, BigDecimal netPnl) {}
    public record Point(Instant realizedAt, BigDecimal equity, boolean accepted, String reason) {}
    public record Report(String mode, boolean liveSignalEligible, BigDecimal initialEquity,
                         BigDecimal finalEquity, BigDecimal maxDrawdownQuote,
                         int acceptedTrades, int blockedTrades, List<Point> equityCurve,
                         String limitations) {}
}
