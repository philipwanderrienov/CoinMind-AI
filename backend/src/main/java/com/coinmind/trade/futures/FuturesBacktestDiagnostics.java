package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/** Read-only analysis of realized offline trade diagnostics; never supplies decisions. */
public final class FuturesBacktestDiagnostics {
    private static final MathContext MC = MathContext.DECIMAL64;
    private FuturesBacktestDiagnostics() {}

    public record Group(String category, int count, int wins, int losses,
                        BigDecimal grossPnl, BigDecimal fees, BigDecimal netPnl,
                        BigDecimal averageNetPnl) {}

    public static List<Group> bySide(FuturesHistoricalWalkForwardRunner.DynamicReport report) {
        return summarize(report, List.of("LONG", "SHORT"), true);
    }

    public static List<Group> byExit(FuturesHistoricalWalkForwardRunner.DynamicReport report) {
        return summarize(report, List.of("STOP", "TARGET", "TIME_EXIT"), false);
    }

    private static List<Group> summarize(FuturesHistoricalWalkForwardRunner.DynamicReport report,
                                         List<String> categories, boolean side) {
        if (report == null || !"OFFLINE_ONLY".equals(report.mode()) || report.liveSignalEligible()
                || report.trades() == null || report.executed() != report.trades().size())
            throw new IllegalArgumentException("Complete offline trade diagnostics required");
        var output = new ArrayList<Group>();
        for (String category : categories) {
            int count = 0, wins = 0, losses = 0;
            BigDecimal gross = BigDecimal.ZERO, fees = BigDecimal.ZERO, net = BigDecimal.ZERO;
            for (var t : report.trades()) {
                if (t == null || t.grossPnl() == null || t.tradingFees() == null || t.netPnl() == null
                        || !List.of("LONG","SHORT").contains(t.side())
                        || !List.of("STOP","TARGET","TIME_EXIT").contains(t.exitReason()))
                    throw new IllegalArgumentException("Incomplete trade row");
                if (!(side ? t.side() : t.exitReason()).equals(category)) continue;
                count++;
                if (t.netPnl().signum() > 0) wins++;
                if (t.netPnl().signum() < 0) losses++;
                gross = gross.add(t.grossPnl());
                fees = fees.add(t.tradingFees());
                net = net.add(t.netPnl());
            }
            output.add(new Group(category, count, wins, losses, gross, fees, net,
                    count == 0 ? null : net.divide(BigDecimal.valueOf(count), MC)));
        }
        return List.copyOf(output);
    }
}
