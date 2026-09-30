package com.coinmind.trade.outcome;

import com.coinmind.trade.model.TradeSetup;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Repository
@ConditionalOnProperty(
        prefix = "coinmind.persistence",
        name = "enabled",
        havingValue = "true"
)
public class TradeOutcomeRepository {

    private final DatabaseClient databaseClient;

    public TradeOutcomeRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Mono<Void> recordIfNew(TradeSetup setup) {
        if ("WAIT".equals(setup.action())
                || setup.entryLow() == null
                || setup.entryHigh() == null
                || setup.invalidationPrice() == null) {
            return Mono.empty();
        }

        return databaseClient.sql("""
                INSERT INTO trade_outcomes (
                    id, symbol, interval, action, side, confidence,
                    signal_score, alignment_score, market_regime,
                    market_activity, market_activity_score,
                    entry_low, entry_high, invalidation_price,
                    target1, target2, status, generated_at, updated_at
                )
                SELECT
                    :id, :symbol, :interval, :action, :side, :confidence,
                    :signalScore, :alignmentScore, :marketRegime,
                    :marketActivity, :marketActivityScore,
                    :entryLow, :entryHigh, :invalidationPrice,
                    :target1, :target2, 'PENDING', :generatedAt, NOW()
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM trade_outcomes
                    WHERE symbol = :symbol
                      AND action = :action
                      AND generated_at >= :dedupeAfter
                      AND status IN ('PENDING', 'ENTRY_HIT', 'TP1_HIT')
                )
                """)
                .bind("id", UUID.randomUUID().toString())
                .bind("symbol", setup.symbol().toUpperCase())
                .bind("interval", setup.interval().toLowerCase())
                .bind("action", setup.action())
                .bind("side", setup.side())
                .bind("confidence", setup.confidence())
                .bind("signalScore", setup.signalScore())
                .bind("alignmentScore", setup.alignmentScore())
                .bind("marketRegime", setup.marketRegime())
                .bind("marketActivity", setup.marketActivity())
                .bind("marketActivityScore", setup.marketActivityScore())
                .bind("entryLow", setup.entryLow())
                .bind("entryHigh", setup.entryHigh())
                .bind("invalidationPrice", setup.invalidationPrice())
                .bind("target1", setup.target1())
                .bind("target2", setup.target2())
                .bind("generatedAt", setup.generatedAt())
                .bind("dedupeAfter", setup.generatedAt().minusSeconds(15 * 60L))
                .then();
    }

    public Flux<TradeOutcome> findRecent(String symbol, int limit) {
        return databaseClient.sql("""
                SELECT *
                FROM trade_outcomes
                WHERE symbol = :symbol
                ORDER BY generated_at DESC
                LIMIT :limit
                """)
                .bind("symbol", symbol.toUpperCase())
                .bind("limit", Math.max(1, Math.min(limit, 100)))
                .map((row, metadata) -> map(row))
                .all();
    }

    public Flux<TradeOutcome> findOpen(int limit) {
        return databaseClient.sql("""
                SELECT *
                FROM trade_outcomes
                WHERE status IN ('PENDING', 'ENTRY_HIT', 'TP1_HIT')
                ORDER BY generated_at ASC
                LIMIT :limit
                """)
                .bind("limit", Math.max(1, Math.min(limit, 500)))
                .map((row, metadata) -> map(row))
                .all();
    }

    public Flux<CandleRange> findCandles(String symbol, Instant from, Instant to) {
        return databaseClient.sql("""
                SELECT open_time, high_price, low_price, close_price
                FROM market_candles
                WHERE symbol = :symbol
                  AND interval = '1m'
                  AND open_time >= :from
                  AND open_time <= :to
                  AND closed = TRUE
                ORDER BY open_time ASC
                """)
                .bind("symbol", symbol.toUpperCase())
                .bind("from", from)
                .bind("to", to)
                .map((row, metadata) -> new CandleRange(
                        row.get("open_time", Instant.class),
                        row.get("high_price", BigDecimal.class),
                        row.get("low_price", BigDecimal.class),
                        row.get("close_price", BigDecimal.class)
                ))
                .all();
    }

    public Mono<Void> updateEvaluation(
            String id,
            String status,
            Instant entryHitAt,
            BigDecimal entryPrice,
            Instant target1HitAt,
            Instant target2HitAt,
            Instant stoppedAt,
            Instant expiredAt,
            Instant evaluatedAt
    ) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                UPDATE trade_outcomes
                SET status = :status,
                    entry_hit_at = COALESCE(:entryHitAt, entry_hit_at),
                    entry_price = COALESCE(:entryPrice, entry_price),
                    target1_hit_at = COALESCE(:target1HitAt, target1_hit_at),
                    target2_hit_at = COALESCE(:target2HitAt, target2_hit_at),
                    stopped_at = COALESCE(:stoppedAt, stopped_at),
                    expired_at = COALESCE(:expiredAt, expired_at),
                    evaluated_at = :evaluatedAt,
                    updated_at = NOW()
                WHERE id = :id
                """)
                .bind("id", id)
                .bind("status", status)
                .bind("evaluatedAt", evaluatedAt);

        spec = bindNullable(spec, "entryHitAt", entryHitAt, Instant.class);
        spec = bindNullable(spec, "entryPrice", entryPrice, BigDecimal.class);
        spec = bindNullable(spec, "target1HitAt", target1HitAt, Instant.class);
        spec = bindNullable(spec, "target2HitAt", target2HitAt, Instant.class);
        spec = bindNullable(spec, "stoppedAt", stoppedAt, Instant.class);
        spec = bindNullable(spec, "expiredAt", expiredAt, Instant.class);

        return spec.then();
    }

    public Mono<TradeOutcomeSummary> summary(String symbol) {
        return databaseClient.sql("""
                SELECT
                    COUNT(*) AS total,
                    COUNT(*) FILTER (WHERE status = 'PENDING') AS pending,
                    COUNT(*) FILTER (
                        WHERE entry_hit_at IS NOT NULL
                          AND target1_hit_at IS NULL
                          AND stopped_at IS NULL
                    ) AS entry_hit,
                    COUNT(*) FILTER (WHERE target1_hit_at IS NOT NULL) AS target1_hit,
                    COUNT(*) FILTER (WHERE target2_hit_at IS NOT NULL) AS target2_hit,
                    COUNT(*) FILTER (WHERE stopped_at IS NOT NULL) AS stopped,
                    COUNT(*) FILTER (WHERE expired_at IS NOT NULL) AS expired
                FROM trade_outcomes
                WHERE symbol = :symbol
                """)
                .bind("symbol", symbol.toUpperCase())
                .map((row, metadata) -> {
                    int total = intValue(row.get("total", Long.class));
                    int pending = intValue(row.get("pending", Long.class));
                    int entryHit = intValue(row.get("entry_hit", Long.class));
                    int target1Hit = intValue(row.get("target1_hit", Long.class));
                    int target2Hit = intValue(row.get("target2_hit", Long.class));
                    int stopped = intValue(row.get("stopped", Long.class));
                    int expired = intValue(row.get("expired", Long.class));

                    int entered = entryHit + target1Hit + stopped;

                    return new TradeOutcomeSummary(
                            symbol.toUpperCase(),
                            total,
                            pending,
                            entryHit,
                            target1Hit,
                            target2Hit,
                            stopped,
                            expired,
                            pct(entered, total),
                            pct(target1Hit + target2Hit, entered),
                            pct(target2Hit, entered),
                            pct(stopped, entered)
                    );
                })
                .one()
                .defaultIfEmpty(new TradeOutcomeSummary(
                        symbol.toUpperCase(), 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0
                ));
    }

    private TradeOutcome map(io.r2dbc.spi.Row row) {
        return new TradeOutcome(
                row.get("id", String.class),
                row.get("symbol", String.class),
                row.get("interval", String.class),
                row.get("action", String.class),
                row.get("side", String.class),
                intValue(row.get("confidence", Integer.class)),
                row.get("signal_score", BigDecimal.class),
                intValue(row.get("alignment_score", Integer.class)),
                row.get("market_regime", String.class),
                row.get("market_activity", String.class),
                row.get("market_activity_score", BigDecimal.class),
                row.get("entry_low", BigDecimal.class),
                row.get("entry_high", BigDecimal.class),
                row.get("invalidation_price", BigDecimal.class),
                row.get("target1", BigDecimal.class),
                row.get("target2", BigDecimal.class),
                row.get("status", String.class),
                row.get("entry_hit_at", Instant.class),
                row.get("entry_price", BigDecimal.class),
                row.get("target1_hit_at", Instant.class),
                row.get("target2_hit_at", Instant.class),
                row.get("stopped_at", Instant.class),
                row.get("expired_at", Instant.class),
                row.get("generated_at", Instant.class),
                row.get("evaluated_at", Instant.class)
        );
    }

    private <T> DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec,
            String name,
            T value,
            Class<T> type
    ) {
        return value == null
                ? spec.bindNull(name, type)
                : spec.bind(name, value);
    }

    private int intValue(Number value) {
        return value == null ? 0 : value.intValue();
    }

    private double pct(int part, int total) {
        if (total <= 0) return 0;
        return Math.round((part * 10000.0) / total) / 100.0;
    }

    public record CandleRange(
            Instant openTime,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close
    ) {
    }
}
