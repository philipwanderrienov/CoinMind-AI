package com.coinmind.ai.usage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Repository
@ConditionalOnProperty(
        prefix = "coinmind.persistence",
        name = "enabled",
        havingValue = "true"
)
public class AiUsageRepository {

    private final DatabaseClient databaseClient;

    public AiUsageRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Mono<Void> insert(AiUsageRecord usage) {
        return databaseClient.sql("""
                INSERT INTO ai_usage_log (
                    id, request_id, symbol, interval, trigger_type, model,
                    input_tokens, cached_input_tokens, output_tokens, reasoning_tokens,
                    total_tokens, estimated_input_cost_usd,
                    estimated_output_cost_usd, estimated_total_cost_usd, created_at
                ) VALUES (
                    :id, :requestId, :symbol, :interval, :triggerType, :model,
                    :inputTokens, :cachedInputTokens, :outputTokens, :reasoningTokens,
                    :totalTokens, :inputCost, :outputCost, :totalCost, :createdAt
                )
                """)
                .bind("id", usage.id())
                .bind("requestId", nullable(usage.requestId()))
                .bind("symbol", usage.symbol())
                .bind("interval", usage.interval())
                .bind("triggerType", usage.triggerType())
                .bind("model", usage.model())
                .bind("inputTokens", usage.inputTokens())
                .bind("cachedInputTokens", usage.cachedInputTokens())
                .bind("outputTokens", usage.outputTokens())
                .bind("reasoningTokens", usage.reasoningTokens())
                .bind("totalTokens", usage.totalTokens())
                .bind("inputCost", usage.estimatedInputCostUsd())
                .bind("outputCost", usage.estimatedOutputCostUsd())
                .bind("totalCost", usage.estimatedTotalCostUsd())
                .bind("createdAt", usage.createdAt())
                .then();
    }

    public Mono<Aggregate> aggregate(Instant from) {
        return databaseClient.sql("""
                SELECT
                    COUNT(*) AS calls,
                    COALESCE(SUM(input_tokens), 0) AS input_tokens,
                    COALESCE(SUM(cached_input_tokens), 0) AS cached_input_tokens,
                    COALESCE(SUM(output_tokens), 0) AS output_tokens,
                    COALESCE(SUM(reasoning_tokens), 0) AS reasoning_tokens,
                    COALESCE(SUM(total_tokens), 0) AS total_tokens,
                    COALESCE(SUM(estimated_total_cost_usd), 0) AS cost
                FROM ai_usage_log
                WHERE created_at >= :from
                """)
                .bind("from", from)
                .map((row, metadata) -> new Aggregate(
                        number(row.get("calls", Long.class)),
                        number(row.get("input_tokens", BigDecimal.class)),
                        number(row.get("cached_input_tokens", BigDecimal.class)),
                        number(row.get("output_tokens", BigDecimal.class)),
                        number(row.get("reasoning_tokens", BigDecimal.class)),
                        number(row.get("total_tokens", BigDecimal.class)),
                        decimal(row.get("cost", BigDecimal.class))
                ))
                .one();
    }

    public Flux<AiUsageSummary.DailyUsage> daily(int days, String timezone) {
        int safeDays = Math.max(1, Math.min(days, 90));
        ZoneId zoneId = ZoneId.of(timezone);
        Instant from = LocalDate.now(zoneId)
                .minusDays(safeDays - 1L)
                .atStartOfDay(zoneId)
                .toInstant();

        return databaseClient.sql("""
                SELECT
                    (created_at AT TIME ZONE :timezone)::date AS usage_date,
                    COUNT(*) AS calls,
                    COALESCE(SUM(input_tokens), 0) AS input_tokens,
                    COALESCE(SUM(output_tokens), 0) AS output_tokens,
                    COALESCE(SUM(estimated_total_cost_usd), 0) AS cost
                FROM ai_usage_log
                WHERE created_at >= :from
                GROUP BY usage_date
                ORDER BY usage_date ASC
                """)
                .bind("timezone", timezone)
                .bind("from", from)
                .map((row, metadata) -> new AiUsageSummary.DailyUsage(
                        row.get("usage_date", LocalDate.class),
                        number(row.get("calls", Long.class)),
                        number(row.get("input_tokens", BigDecimal.class)),
                        number(row.get("output_tokens", BigDecimal.class)),
                        decimal(row.get("cost", BigDecimal.class))
                ))
                .all();
    }

    private long number(Long value) {
        return value == null ? 0L : value;
    }

    private long number(BigDecimal value) {
        return value == null ? 0L : value.longValue();
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }

    public record Aggregate(
            long calls,
            long inputTokens,
            long cachedInputTokens,
            long outputTokens,
            long reasoningTokens,
            long totalTokens,
            BigDecimal cost
    ) {
    }
}
