package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Opt-in PostgreSQL archive for trusted server-generated replay summary snapshots.
 * Schema is installed separately; no public write routes. All rows are OFFLINE_ONLY.
 */
@Repository
public final class FuturesPaperReplayArchive {
    private final DatabaseClient db;
    private final boolean enabled;

    public FuturesPaperReplayArchive(DatabaseClient db,
            @Value("${coinmind.futures.paper-replay-archive-enabled:false}") boolean enabled) {
        this.db = db;
        this.enabled = enabled;
    }

    public Mono<Archived> saveTrusted(FuturesPaperReplayRegistry.Snapshot snapshot) {
        if (!enabled) return Mono.error(new IllegalStateException("Replay archive disabled"));
        validate(snapshot);
        return db.sql("""
                INSERT INTO futures_paper_replay_archive
                  (id, completed_at, provenance, initial_equity, final_equity,
                   max_drawdown_quote, executed, risk_status, risk_day_utc,
                   daily_loss_limit_quote, realized_loss_quote, remaining_loss_budget_quote)
                VALUES (:id, :completed, :source, :initial, :final, :drawdown,
                        :executed, :status, :day, :limit, :loss, :remaining)
                ON CONFLICT (id) DO NOTHING
                """)
                .bind("id", snapshot.id()).bind("completed", snapshot.completedAt())
                .bind("source", snapshot.provenance())
                .bind("initial", snapshot.initialEquity())
                .bind("final", snapshot.finalEquity())
                .bind("drawdown", snapshot.maxDrawdownQuote())
                .bind("executed", snapshot.executed())
                .bind("status", snapshot.risk().status())
                .bind("day", snapshot.risk().riskDayUtc())
                .bind("limit", snapshot.risk().dailyLossLimitQuote())
                .bind("loss", snapshot.risk().realizedLossQuote())
                .bind("remaining", snapshot.risk().remainingLossBudgetQuote())
                .fetch().rowsUpdated()
                .then(find(snapshot.id()));
    }

    public Mono<Archived> find(UUID id) {
        if (!enabled) return Mono.empty();
        return db.sql("SELECT * FROM futures_paper_replay_archive WHERE id = :id")
                .bind("id", id).map((row, meta) -> map(row)).one();
    }

    public Flux<Archived> recent(int limit) {
        if (!enabled) return Flux.empty();
        if (limit < 1 || limit > 100) return Flux.error(new IllegalArgumentException("limit must be 1..100"));
        return db.sql("SELECT * FROM futures_paper_replay_archive ORDER BY completed_at DESC, id DESC LIMIT :limit")
                .bind("limit", limit).map((row, meta) -> map(row)).all();
    }

    private static Archived map(io.r2dbc.spi.Row row) {
        return new Archived(row.get("id", UUID.class), row.get("completed_at", Instant.class),
                row.get("provenance", String.class),
                row.get("initial_equity", BigDecimal.class), row.get("final_equity", BigDecimal.class),
                row.get("max_drawdown_quote", BigDecimal.class), row.get("executed", Integer.class),
                row.get("risk_status", String.class), row.get("risk_day_utc", java.time.LocalDate.class),
                row.get("daily_loss_limit_quote", BigDecimal.class),
                row.get("realized_loss_quote", BigDecimal.class),
                row.get("remaining_loss_budget_quote", BigDecimal.class));
    }

    public static void validate(FuturesPaperReplayRegistry.Snapshot s) {
        if (s == null || s.id() == null || s.completedAt() == null || s.risk() == null
                || (!"SYNTHETIC_FIXTURE".equals(s.provenance()) && !"HISTORICAL_REPLAY".equals(s.provenance()))
                || !"OFFLINE_ONLY".equals(s.risk().mode()) || s.risk().liveSignalEligible()
                || s.initialEquity() == null || s.initialEquity().signum() <= 0
                || s.finalEquity() == null || s.maxDrawdownQuote() == null
                || s.executed() < 0 || s.risk().riskDayUtc() == null
                || !s.completedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate().equals(s.risk().riskDayUtc())
                || s.risk().dailyLossLimitQuote() == null || s.risk().realizedLossQuote() == null
                || s.risk().remainingLossBudgetQuote() == null
                || !java.util.Set.of("NORMAL","LOSS_MONITORING","DAILY_RISK_LOCK").contains(s.risk().status()))
            throw new IllegalArgumentException("Invalid offline archive snapshot");
    }

    public record Archived(UUID id, Instant completedAt, String provenance,
            BigDecimal initialEquity, BigDecimal finalEquity, BigDecimal maxDrawdownQuote,
            Integer executed, String riskStatus, java.time.LocalDate riskDayUtc,
            BigDecimal dailyLossLimitQuote, BigDecimal realizedLossQuote,
            BigDecimal remainingLossBudgetQuote) {}
}
