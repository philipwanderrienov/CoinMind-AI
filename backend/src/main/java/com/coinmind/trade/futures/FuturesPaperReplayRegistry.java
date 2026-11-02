package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Process-local, server-produced OFFLINE replay snapshots.
 * No public writes, no account state, no persistent history.
 */
@Component
public final class FuturesPaperReplayRegistry {
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public UUID recordTrustedReplay(String provenance,
            FuturesHistoricalWalkForwardRunner.DynamicReport report,
            BigDecimal dailyLossLimitPercent, Instant completedAt) {
        if (completedAt == null || report == null || report.equityCurve() == null
                || (!"HISTORICAL_REPLAY".equals(provenance)
                    && !"SYNTHETIC_FIXTURE".equals(provenance))
                || !"OFFLINE_ONLY".equals(report.mode()) || report.liveSignalEligible()) {
            throw new IllegalArgumentException("Trusted offline replay required");
        }
        // Validates chronology, numeric consistency, and no future settlement.
        FuturesPaperRiskReport.fromReplay(provenance, report, dailyLossLimitPercent, completedAt);
        UUID id = UUID.randomUUID();
        entries.put(id, new Entry(provenance, report, dailyLossLimitPercent, completedAt));
        return id;
    }

    public Snapshot find(UUID id) {
        if (id == null) return null;
        Entry entry = entries.get(id);
        if (entry == null) return null;
        return new Snapshot(id, entry.completedAt(), entry.provenance(),
                entry.report().initialEquity(), entry.report().finalEquity(),
                entry.report().maxDrawdownQuote(), entry.report().executed(),
                FuturesPaperRiskReport.fromReplay(entry.provenance(), entry.report(),
                        entry.dailyLossLimitPercent(), entry.completedAt()));
    }

    /** Copy of validated server-computed realized equity points; never user supplied. */
    public java.util.List<FuturesHistoricalWalkForwardRunner.EquityPoint> equityCurve(UUID id) {
        if (id == null) return java.util.List.of();
        Entry entry = entries.get(id);
        return entry == null ? java.util.List.of() : java.util.List.copyOf(entry.report().equityCurve());
    }

    private record Entry(String provenance,
            FuturesHistoricalWalkForwardRunner.DynamicReport report,
            BigDecimal dailyLossLimitPercent, Instant completedAt) {}

    public record Snapshot(UUID id, Instant completedAt, String provenance,
            BigDecimal initialEquity, BigDecimal finalEquity, BigDecimal maxDrawdownQuote,
            int executed, FuturesPaperRiskStatus.Snapshot risk) {}
}
