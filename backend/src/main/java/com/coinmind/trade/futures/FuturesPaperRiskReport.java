package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Computes an offline-only daily risk snapshot from an internally produced dynamic replay.
 * The query instant must not precede the latest equity event (no historical look-ahead).
 */
public final class FuturesPaperRiskReport {
    private FuturesPaperRiskReport() {}

    public static FuturesPaperRiskStatus.Snapshot fromReplay(
            String provenance, FuturesHistoricalWalkForwardRunner.DynamicReport report,
            BigDecimal dailyLossLimitPercent, Instant asOf) {
        if (report == null || asOf == null || !"OFFLINE_ONLY".equals(report.mode())
                || report.liveSignalEligible()
                || (!"HISTORICAL_REPLAY".equals(provenance) && !"SYNTHETIC_FIXTURE".equals(provenance)))
            throw new IllegalArgumentException("Offline dynamic replay required");
        var curve = report.equityCurve();
        BigDecimal current = report.initialEquity();
        BigDecimal start = report.initialEquity();
        BigDecimal pnl = BigDecimal.ZERO;
        LocalDate riskDay = asOf.atZone(ZoneOffset.UTC).toLocalDate();
        Instant last = null;
        boolean seenToday = false;
        for (var point : curve) {
            if (point == null || point.realizedAt() == null || point.equityUsdt() == null
                    || point.netPnlUsdt() == null || point.realizedAt().isAfter(asOf)
                    || (last != null && !point.realizedAt().isAfter(last))
                    || current.add(point.netPnlUsdt()).compareTo(point.equityUsdt()) != 0)
                throw new IllegalArgumentException("Invalid or future realized equity event");
            last = point.realizedAt();
            LocalDate settlementDay = point.realizedAt().atZone(ZoneOffset.UTC).toLocalDate();
            if (settlementDay.equals(riskDay)) {
                if (!seenToday) { start = current; seenToday = true; }
                pnl = pnl.add(point.netPnlUsdt());
            }
            current = point.equityUsdt();
        }
        if (!seenToday) start = current;
        return FuturesPaperRiskStatus.evaluate(new FuturesPaperRiskStatus.Input(
                provenance, asOf, start, current, pnl, dailyLossLimitPercent));
    }

}
