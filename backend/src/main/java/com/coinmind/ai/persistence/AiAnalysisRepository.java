package com.coinmind.ai.persistence;

import com.coinmind.ai.model.AiAnalysisResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Repository
@ConditionalOnProperty(
        prefix = "coinmind.persistence",
        name = "enabled",
        havingValue = "true"
)
public class AiAnalysisRepository {

    private final DatabaseClient databaseClient;

    public AiAnalysisRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Mono<AiAnalysisResult> findLatest(String symbol, String interval) {
        return databaseClient.sql("""
                SELECT symbol, interval, market_bias, confidence, summary,
                       supporting_factors, risk_factors, model, analyzed_at
                FROM ai_analysis_history
                WHERE symbol = :symbol
                  AND interval = :interval
                ORDER BY analyzed_at DESC
                LIMIT 1
                """)
                .bind("symbol", symbol.toUpperCase())
                .bind("interval", interval.toLowerCase())
                .map((row, metadata) -> new AiAnalysisResult(
                        row.get("symbol", String.class),
                        row.get("interval", String.class),
                        row.get("market_bias", String.class),
                        number(row.get("confidence", Integer.class)),
                        row.get("summary", String.class),
                        split(row.get("supporting_factors", String.class)),
                        split(row.get("risk_factors", String.class)),
                        row.get("model", String.class),
                        row.get("analyzed_at", Instant.class)
                ))
                .one();
    }

    public Mono<Void> insert(
            AiAnalysisResult result,
            String triggerType,
            BigDecimal entryPrice,
            BigDecimal signalScore
    ) {
        return databaseClient.sql("""
                INSERT INTO ai_analysis_history (
                    id, symbol, interval, trigger_type, market_bias, confidence,
                    summary, supporting_factors, risk_factors, model, analyzed_at,
                    entry_price, signal_score
                ) VALUES (
                    :id, :symbol, :interval, :triggerType, :marketBias, :confidence,
                    :summary, :supportingFactors, :riskFactors, :model, :analyzedAt,
                    :entryPrice, :signalScore
                )
                """)
                .bind("id", UUID.randomUUID().toString())
                .bind("symbol", result.symbol())
                .bind("interval", result.interval())
                .bind("triggerType", triggerType)
                .bind("marketBias", result.marketBias())
                .bind("confidence", result.confidence())
                .bind("summary", result.summary())
                .bind("supportingFactors", String.join(" | ", result.supportingFactors()))
                .bind("riskFactors", String.join(" | ", result.riskFactors()))
                .bind("model", result.model())
                .bind("analyzedAt", result.analyzedAt())
                .bind("entryPrice", entryPrice)
                .bind("signalScore", signalScore)
                .then();
    }

    private int number(Integer value) {
        return value == null ? 0 : value;
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Arrays.stream(value.split("\\s*\\|\\s*"))
                .filter(item -> !item.isBlank())
                .toList();
    }
}
