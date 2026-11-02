package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Internal opt-in storage of realized-only paper equity points. Not a live feed. */
@Repository
public final class FuturesPaperEquityArchive {
    private final DatabaseClient db;
    private final boolean enabled;

    public FuturesPaperEquityArchive(DatabaseClient db,
            @Value("${coinmind.futures.paper-replay-archive-enabled:false}") boolean enabled) {
        this.db = db;
        this.enabled = enabled;
    }

    public Mono<Long> saveTrusted(UUID replayId, BigDecimal initialEquity,
                                  Instant completedAt,
                                  List<FuturesHistoricalWalkForwardRunner.EquityPoint> points) {
        if (!enabled) return Mono.error(new IllegalStateException("Replay archive disabled"));
        validate(initialEquity, completedAt, points);
        if (replayId == null) return Mono.error(new IllegalArgumentException("Replay ID required"));
        return Flux.range(0, points.size())
                .concatMap(index -> {
                    var point = points.get(index);
                    return db.sql("""
                            INSERT INTO futures_paper_replay_equity
                                (replay_id, point_index, realized_at, equity_usdt, net_pnl_usdt)
                            VALUES (:id, :idx, :at, :equity, :pnl)
                            ON CONFLICT (replay_id, point_index) DO NOTHING
                            """)
                            .bind("id", replayId).bind("idx", index)
                            .bind("at", point.realizedAt()).bind("equity", point.equityUsdt())
                            .bind("pnl", point.netPnlUsdt()).fetch().rowsUpdated();
                }).reduce(0L, Long::sum);
    }

    public Flux<Point> points(UUID replayId) {
        if (!enabled) return Flux.empty();
        if (replayId == null) return Flux.error(new IllegalArgumentException("Replay ID required"));
        return db.sql("""
                SELECT point_index, realized_at, equity_usdt, net_pnl_usdt
                FROM futures_paper_replay_equity WHERE replay_id=:id ORDER BY point_index ASC
                """).bind("id", replayId)
                .map((row, meta) -> new Point(row.get("point_index", Integer.class),
                        row.get("realized_at", Instant.class),
                        row.get("equity_usdt", BigDecimal.class),
                        row.get("net_pnl_usdt", BigDecimal.class))).all();
    }

    public static void validate(BigDecimal initialEquity, Instant completedAt,
                                 List<FuturesHistoricalWalkForwardRunner.EquityPoint> points) {
        if (initialEquity == null || initialEquity.signum() <= 0
                || completedAt == null || points == null || points.size() > 100_000)
            throw new IllegalArgumentException("Invalid equity curve metadata");
        Instant priorTime = null;
        BigDecimal previousEquity = initialEquity;
        for (var p : points) {
            if (p == null || p.realizedAt() == null || p.equityUsdt() == null
                    || p.netPnlUsdt() == null || p.realizedAt().isAfter(completedAt)
                    || (priorTime != null && !p.realizedAt().isAfter(priorTime))
                    || previousEquity.add(p.netPnlUsdt()).compareTo(p.equityUsdt()) != 0)
                throw new IllegalArgumentException("Invalid or inconsistent realized equity point");
            priorTime = p.realizedAt();
            previousEquity = p.equityUsdt();
        }
    }

    public record Point(int pointIndex, Instant realizedAt, BigDecimal equityUsdt,
                        BigDecimal netPnlUsdt) {}
}
