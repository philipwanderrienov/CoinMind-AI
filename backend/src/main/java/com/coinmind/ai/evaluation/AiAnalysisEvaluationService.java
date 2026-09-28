package com.coinmind.ai.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AiAnalysisEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisEvaluationService.class);
    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);

    private final ObjectProvider<AiAnalysisEvaluationRepository> repositoryProvider;

    public AiAnalysisEvaluationService(
            ObjectProvider<AiAnalysisEvaluationRepository> repositoryProvider
    ) {
        this.repositoryProvider = repositoryProvider;
    }

    @Scheduled(cron = "0 */15 * * * *")
    public void evaluatePendingAnalyses() {
        AiAnalysisEvaluationRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return;
        }

        Flux.concat(
                evaluateHorizon(repository, "1h", Duration.ofHours(1)),
                evaluateHorizon(repository, "4h", Duration.ofHours(4)),
                evaluateHorizon(repository, "24h", Duration.ofHours(24))
        ).subscribe(
                ignored -> { },
                error -> log.warn("AI evaluation cycle failed", error)
        );
    }

    public Mono<AiEvaluationSummary> summary(
            String symbol,
            String interval
    ) {
        AiAnalysisEvaluationRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return Mono.just(new AiEvaluationSummary(
                    symbol.toUpperCase(),
                    interval.toLowerCase(),
                    0,
                    0,
                    List.of(),
                    List.of(),
                    List.of()
            ));
        }

        Mono<Integer> totalAnalyses = repository.countAnalyses(symbol, interval);

        Mono<List<AiEvaluationSummary.HorizonSummary>> horizons =
                repository.aggregateByHorizon(symbol, interval)
                        .map(row -> new AiEvaluationSummary.HorizonSummary(
                                row.horizon(),
                                row.evaluated(),
                                row.correct(),
                                percentage(row.correct(), row.evaluated()),
                                scale(row.averageReturnPct())
                        ))
                        .collectList();

        Mono<List<AiEvaluationSummary.BiasSummary>> biases =
                repository.aggregateByBias(symbol, interval)
                        .map(row -> new AiEvaluationSummary.BiasSummary(
                                row.marketBias(),
                                row.evaluated(),
                                row.correct(),
                                percentage(row.correct(), row.evaluated())
                        ))
                        .collectList();

        Mono<List<AiEvaluationSummary.ModelSummary>> models =
                repository.aggregateByModel(symbol, interval)
                        .map(row -> new AiEvaluationSummary.ModelSummary(
                                row.model(),
                                row.evaluated(),
                                row.correct(),
                                percentage(row.correct(), row.evaluated()),
                                scale(row.averageReturnPct())
                        ))
                        .collectList();

        return Mono.zip(totalAnalyses, horizons, biases, models)
                .map(tuple -> {
                    int totalEvaluations = tuple.getT2().stream()
                            .mapToInt(AiEvaluationSummary.HorizonSummary::evaluated)
                            .sum();

                    return new AiEvaluationSummary(
                            symbol.toUpperCase(),
                            interval.toLowerCase(),
                            tuple.getT1(),
                            totalEvaluations,
                            tuple.getT2(),
                            tuple.getT3(),
                            tuple.getT4()
                    );
                });
    }

    public Flux<AiAnalysisHistoryItem> history(
            String symbol,
            String interval,
            int limit
    ) {
        AiAnalysisEvaluationRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return Flux.empty();
        }

        return repository.findRecentAnalyses(symbol, interval, limit)
                .concatMap(row ->
                        repository.findEvaluations(row.id())
                                .map(eval -> new AiAnalysisHistoryItem.Evaluation(
                                        eval.horizon(),
                                        eval.exitPrice(),
                                        eval.returnPct(),
                                        eval.directionCorrect(),
                                        eval.evaluatedAt()
                                ))
                                .collectList()
                                .map(evaluations -> new AiAnalysisHistoryItem(
                                        row.id(),
                                        row.symbol(),
                                        row.interval(),
                                        row.triggerType(),
                                        row.marketBias(),
                                        row.confidence(),
                                        row.summary(),
                                        row.model(),
                                        row.entryPrice(),
                                        row.signalScore(),
                                        row.analyzedAt(),
                                        evaluations
                                ))
                );
    }

    private BigDecimal percentage(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100), MC)
                .divide(BigDecimal.valueOf(denominator), MC)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO
                : value.setScale(4, RoundingMode.HALF_UP);
    }

    private Flux<Void> evaluateHorizon(
            AiAnalysisEvaluationRepository repository,
            String horizon,
            Duration duration
    ) {
        Instant now = Instant.now();
        Instant eligibleBefore = now.minus(duration);

        return repository.findCandidates(horizon, eligibleBefore, 100)
                .flatMap(candidate -> {
                    Instant target = candidate.analyzedAt().plus(duration);
                    Instant from = target.minus(Duration.ofMinutes(10));
                    Instant to = target.plus(Duration.ofMinutes(30));

                    return repository.findNearestClose(
                                    candidate.symbol(),
                                    from,
                                    to,
                                    target
                            )
                            .flatMap(exitPrice -> saveEvaluation(
                                    repository,
                                    candidate,
                                    horizon,
                                    target,
                                    exitPrice
                            ))
                            .switchIfEmpty(Mono.empty());
                });
    }

    private Mono<Void> saveEvaluation(
            AiAnalysisEvaluationRepository repository,
            AiAnalysisEvaluationRepository.Candidate candidate,
            String horizon,
            Instant target,
            BigDecimal exitPrice
    ) {
        BigDecimal entryPrice = candidate.entryPrice();

        if (entryPrice == null || entryPrice.signum() == 0 || exitPrice == null) {
            return Mono.empty();
        }

        BigDecimal returnPct = exitPrice
                .subtract(entryPrice, MC)
                .divide(entryPrice, MC)
                .multiply(BigDecimal.valueOf(100), MC)
                .setScale(8, RoundingMode.HALF_UP);

        boolean directionCorrect = switch (candidate.marketBias()) {
            case "BULLISH" -> returnPct.signum() > 0;
            case "BEARISH" -> returnPct.signum() < 0;
            default -> returnPct.abs().compareTo(BigDecimal.valueOf(0.25)) <= 0;
        };

        return repository.upsertEvaluation(
                candidate.id(),
                horizon,
                target,
                exitPrice,
                returnPct,
                directionCorrect
        );
    }
}
