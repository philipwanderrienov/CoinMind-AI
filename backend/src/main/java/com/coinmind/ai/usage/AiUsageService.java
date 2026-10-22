package com.coinmind.ai.usage;

import com.coinmind.ai.config.AiProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class AiUsageService {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);

    private final AiProperties properties;
    private final ObjectProvider<AiUsageRepository> repositoryProvider;

    public AiUsageService(
            AiProperties properties,
            ObjectProvider<AiUsageRepository> repositoryProvider
    ) {
        this.properties = properties;
        this.repositoryProvider = repositoryProvider;
    }

    public void record(
            String requestId,
            String symbol,
            String interval,
            String triggerType,
            String model,
            long inputTokens,
            long cachedInputTokens,
            long outputTokens,
            long reasoningTokens,
            long totalTokens
    ) {
        long uncachedInput = Math.max(0, inputTokens - cachedInputTokens);

        Pricing pricing = pricingFor(model);

        BigDecimal inputCost = tokenCost(
                uncachedInput,
                pricing.inputPriceMicrosPerMillion()
        ).add(tokenCost(
                cachedInputTokens,
                pricing.cachedInputPriceMicrosPerMillion()
        ));

        BigDecimal outputCost = tokenCost(
                outputTokens,
                pricing.outputPriceMicrosPerMillion()
        );

        AiUsageRecord record = new AiUsageRecord(
                UUID.randomUUID().toString(),
                requestId,
                symbol,
                interval,
                triggerType,
                model,
                inputTokens,
                cachedInputTokens,
                outputTokens,
                reasoningTokens,
                totalTokens,
                inputCost,
                outputCost,
                inputCost.add(outputCost).setScale(8, RoundingMode.HALF_UP),
                Instant.now()
        );

        repositoryProvider.ifAvailable(repository ->
                repository.insert(record).subscribe()
        );
    }

    public Mono<AiUsageSummary> summary(int days) {
        AiUsageRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return Mono.just(new AiUsageSummary(
                    0, 0, 0, 0, 0, 0, BigDecimal.ZERO,
                    0, 0, 0, BigDecimal.ZERO,
                    java.util.List.of()
            ));
        }

        ZoneId zoneId = ZoneId.of(properties.usageTimezone());

        Instant todayStart = LocalDate.now(zoneId)
                .atStartOfDay(zoneId)
                .toInstant();

        Instant monthStart = LocalDate.now(zoneId)
                .withDayOfMonth(1)
                .atStartOfDay(zoneId)
                .toInstant();

        return Mono.zip(
                repository.aggregate(todayStart),
                repository.aggregate(monthStart),
                repository.daily(days, properties.usageTimezone()).collectList()
        ).map(tuple -> {
            var today = tuple.getT1();
            var month = tuple.getT2();

            return new AiUsageSummary(
                    today.calls(),
                    today.inputTokens(),
                    today.cachedInputTokens(),
                    today.outputTokens(),
                    today.reasoningTokens(),
                    today.totalTokens(),
                    today.cost(),
                    month.calls(),
                    month.inputTokens(),
                    month.outputTokens(),
                    month.cost(),
                    tuple.getT3()
            );
        });
    }


    private Pricing pricingFor(String model) {
        if (model != null && model.equals(properties.effectiveDeepModel())) {
            return new Pricing(
                    properties.deepInputPriceMicrosPerMillion(),
                    properties.deepCachedInputPriceMicrosPerMillion(),
                    properties.deepOutputPriceMicrosPerMillion()
            );
        }

        if (model != null && model.equals(properties.effectiveRoutineModel())) {
            return new Pricing(
                    properties.routineInputPriceMicrosPerMillion(),
                    properties.routineCachedInputPriceMicrosPerMillion(),
                    properties.routineOutputPriceMicrosPerMillion()
            );
        }

        return new Pricing(
                properties.inputPriceMicrosPerMillion(),
                properties.cachedInputPriceMicrosPerMillion(),
                properties.outputPriceMicrosPerMillion()
        );
    }

    private record Pricing(
            long inputPriceMicrosPerMillion,
            long cachedInputPriceMicrosPerMillion,
            long outputPriceMicrosPerMillion
    ) {
    }

    private BigDecimal tokenCost(long tokens, long priceMicrosPerMillion) {
        BigDecimal usdPerMillion = BigDecimal.valueOf(priceMicrosPerMillion, 6);

        return BigDecimal.valueOf(tokens)
                .multiply(usdPerMillion)
                .divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
    }
}
