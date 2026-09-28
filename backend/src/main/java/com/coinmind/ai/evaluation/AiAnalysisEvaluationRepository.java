package com.coinmind.ai.evaluation;

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
public class AiAnalysisEvaluationRepository {

    private final DatabaseClient databaseClient;

    public AiAnalysisEvaluationRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Flux<AnalysisRow> findRecentAnalyses(
            String symbol,
            String interval,
            int limit
    ) {
        return databaseClient.sql("""
                SELECT id, symbol, interval, trigger_type, market_bias, confidence,
                       summary, model, entry_price, signal_score, analyzed_at
                FROM ai_analysis_history
                WHERE symbol = :symbol
                  AND interval = :interval
                ORDER BY analyzed_at DESC
                LIMIT :limit
                """)
                .bind("symbol", symbol.toUpperCase())
                .bind("interval", interval.toLowerCase())
                .bind("limit", Math.max(1, Math.min(limit, 100)))
                .map((row, metadata) -> new AnalysisRow(
                        row.get("id", String.class),
                        row.get("symbol", String.class),
                        row.get("interval", String.class),
                        row.get("trigger_type", String.class),
                        row.get("market_bias", String.class),
                        row.get("confidence", Integer.class) == null
                                ? 0
                                : row.get("confidence", Integer.class),
                        row.get("summary", String.class),
                        row.get("model", String.class),
                        row.get("entry_price", BigDecimal.class),
                        row.get("signal_score", BigDecimal.class),
                        row.get("analyzed_at", Instant.class)
                ))
                .all();
    }

    public Flux<EvaluationRow> findEvaluations(String analysisId) {
        return databaseClient.sql("""
                SELECT horizon, exit_price, return_pct,
                       direction_correct, evaluated_at
                FROM ai_analysis_evaluations
                WHERE analysis_id = :analysisId
                ORDER BY CASE horizon
                    WHEN '1h' THEN 1
                    WHEN '4h' THEN 2
                    WHEN '24h' THEN 3
                    ELSE 99
                END
                """)
                .bind("analysisId", analysisId)
                .map((row, metadata) -> new EvaluationRow(
                        row.get("horizon", String.class),
                        row.get("exit_price", BigDecimal.class),
                        row.get("return_pct", BigDecimal.class),
                        Boolean.TRUE.equals(row.get("direction_correct", Boolean.class)),
                        row.get("evaluated_at", Instant.class)
                ))
                .all();
    }

    public Flux<Candidate> findCandidates(
            String horizon,
            Instant eligibleBefore,
            int limit
    ) {
        return databaseClient.sql("""
                SELECT id, symbol, interval, market_bias, entry_price, analyzed_at
                FROM ai_analysis_history a
                WHERE a.entry_price IS NOT NULL
                  AND a.analyzed_at <= :eligibleBefore
                  AND NOT EXISTS (
                      SELECT 1
                      FROM ai_analysis_evaluations e
                      WHERE e.analysis_id = a.id
                        AND e.horizon = :horizon
                  )
                ORDER BY a.analyzed_at ASC
                LIMIT :limit
                """)
                .bind("eligibleBefore", eligibleBefore)
                .bind("horizon", horizon)
                .bind("limit", Math.max(1, Math.min(limit, 500)))
                .map((row, metadata) -> new Candidate(
                        row.get("id", String.class),
                        row.get("symbol", String.class),
                        row.get("interval", String.class),
                        row.get("market_bias", String.class),
                        row.get("entry_price", BigDecimal.class),
                        row.get("analyzed_at", Instant.class)
                ))
                .all();
    }

    public Mono<BigDecimal> findNearestClose(
            String symbol,
            Instant from,
            Instant to,
            Instant target
    ) {
        return databaseClient.sql("""
                SELECT close_price
                FROM market_candles
                WHERE symbol = :symbol
                  AND interval = '1m'
                  AND open_time BETWEEN :from AND :to
                ORDER BY ABS(EXTRACT(EPOCH FROM (open_time - :target))) ASC
                LIMIT 1
                """)
                .bind("symbol", symbol.toUpperCase())
                .bind("from", from)
                .bind("to", to)
                .bind("target", target)
                .map((row, metadata) -> row.get("close_price", BigDecimal.class))
                .one();
    }

    public Mono<Void> upsertEvaluation(
            String analysisId,
            String horizon,
            Instant targetTime,
            BigDecimal exitPrice,
            BigDecimal returnPct,
            boolean directionCorrect
    ) {
        return databaseClient.sql("""
                INSERT INTO ai_analysis_evaluations (
                    id, analysis_id, horizon, target_time, exit_price,
                    return_pct, direction_correct, evaluated_at
                ) VALUES (
                    :id, :analysisId, :horizon, :targetTime, :exitPrice,
                    :returnPct, :directionCorrect, NOW()
                )
                ON CONFLICT (analysis_id, horizon)
                DO UPDATE SET
                    target_time = EXCLUDED.target_time,
                    exit_price = EXCLUDED.exit_price,
                    return_pct = EXCLUDED.return_pct,
                    direction_correct = EXCLUDED.direction_correct,
                    evaluated_at = NOW()
                """)
                .bind("id", UUID.randomUUID().toString())
                .bind("analysisId", analysisId)
                .bind("horizon", horizon)
                .bind("targetTime", targetTime)
                .bind("exitPrice", exitPrice)
                .bind("returnPct", returnPct)
                .bind("directionCorrect", directionCorrect)
                .then();
    }

    public record AnalysisRow(
            String id,
            String symbol,
            String interval,
            String triggerType,
            String marketBias,
            int confidence,
            String summary,
            String model,
            BigDecimal entryPrice,
            BigDecimal signalScore,
            Instant analyzedAt
    ) {
    }

    public record EvaluationRow(
            String horizon,
            BigDecimal exitPrice,
            BigDecimal returnPct,
            boolean directionCorrect,
            Instant evaluatedAt
    ) {
    }

    public record Candidate(
            String id,
            String symbol,
            String interval,
            String marketBias,
            BigDecimal entryPrice,
            Instant analyzedAt
    ) {
    }
}
