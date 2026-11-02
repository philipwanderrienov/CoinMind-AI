package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Internal, read-only, fail-closed projection for a complete archived offline replay.
 * This service does not expose HTTP routes and does not enable developer endpoints.
 */
@Service
public final class FuturesPaperReplayHistory {
    private final FuturesPaperReplayArchive summaries;
    private final FuturesPaperEquityArchive curves;

    public FuturesPaperReplayHistory(FuturesPaperReplayArchive summaries,
                                     FuturesPaperEquityArchive curves) {
        this.summaries = summaries;
        this.curves = curves;
    }

    public Mono<History> findComplete(UUID id) {
        if (id == null) return Mono.error(new IllegalArgumentException("Replay ID required"));
        return summaries.find(id).flatMap(summary -> curves.points(id).collectList()
                .map(points -> verify(summary, points)));
    }

    static History verify(FuturesPaperReplayArchive.Archived summary,
                          List<FuturesPaperEquityArchive.Point> points) {
        if (summary == null || points == null
                || summary.executed() == null || summary.executed() != points.size()
                || summary.initialEquity() == null || summary.finalEquity() == null
                || summary.completedAt() == null
                || (!"SYNTHETIC_FIXTURE".equals(summary.provenance())
                    && !"HISTORICAL_REPLAY".equals(summary.provenance())))
            throw new IllegalStateException("Incomplete or invalid offline replay archive");
        BigDecimal running = summary.initialEquity();
        java.time.Instant previous = null;
        for (int i = 0; i < points.size(); i++) {
            var p = points.get(i);
            if (p == null || p.pointIndex() != i || p.realizedAt() == null
                    || p.netPnlUsdt() == null || p.equityUsdt() == null
                    || p.realizedAt().isAfter(summary.completedAt())
                    || (previous != null && !p.realizedAt().isAfter(previous)))
                throw new IllegalStateException("Invalid archived equity sequence");
            running = running.add(p.netPnlUsdt());
            if (running.compareTo(p.equityUsdt()) != 0)
                throw new IllegalStateException("Archived equity/PnL mismatch");
            previous = p.realizedAt();
        }
        if (running.compareTo(summary.finalEquity()) != 0)
            throw new IllegalStateException("Archived final equity mismatch");
        return new History(summary, List.copyOf(points));
    }

    public record History(FuturesPaperReplayArchive.Archived summary,
                          List<FuturesPaperEquityArchive.Point> equityCurve) {}
}
