package com.coinmind.ai.persistence;

import com.coinmind.ai.model.AiAnalysisResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

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

    public Mono<Void> insert(AiAnalysisResult result, String triggerType) {
        return databaseClient.sql("""
                INSERT INTO ai_analysis_history (
                    id, symbol, interval, trigger_type, market_bias, confidence,
                    summary, supporting_factors, risk_factors, model, analyzed_at
                ) VALUES (
                    :id, :symbol, :interval, :triggerType, :marketBias, :confidence,
                    :summary, :supportingFactors, :riskFactors, :model, :analyzedAt
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
                .then();
    }
}
