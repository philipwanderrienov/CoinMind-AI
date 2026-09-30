package com.coinmind.trade.outcome;

import com.coinmind.trade.model.TradeSetup;
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
public class TradeOutcomeService {

    private static final Duration ENTRY_EXPIRY = Duration.ofHours(24);
    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);

    private final ObjectProvider<TradeOutcomeRepository> repositoryProvider;

    public TradeOutcomeService(
            ObjectProvider<TradeOutcomeRepository> repositoryProvider
    ) {
        this.repositoryProvider = repositoryProvider;
    }

    public Mono<Void> record(TradeSetup setup) {
        TradeOutcomeRepository repository = repositoryProvider.getIfAvailable();
        return repository == null
                ? Mono.empty()
                : repository.recordIfNew(setup);
    }

    public Flux<TradeOutcome> recent(String symbol, int limit) {
        TradeOutcomeRepository repository = repositoryProvider.getIfAvailable();
        return repository == null
                ? Flux.empty()
                : repository.findRecent(symbol, limit);
    }

    public Mono<TradeOutcomeSummary> summary(String symbol) {
        TradeOutcomeRepository repository = repositoryProvider.getIfAvailable();
        return repository == null
                ? Mono.just(new TradeOutcomeSummary(
                        symbol.toUpperCase(), 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0
                ))
                : repository.summary(symbol);
    }

    public Mono<Integer> evaluateOpen() {
        TradeOutcomeRepository repository = repositoryProvider.getIfAvailable();

        if (repository == null) {
            return Mono.just(0);
        }

        Instant now = Instant.now();

        return repository.findOpen(250)
                .concatMap(outcome -> evaluate(repository, outcome, now))
                .reduce(0, Integer::sum);
    }

    @Scheduled(fixedDelayString = "${coinmind.trade-outcome.evaluation-interval-ms:60000}")
    public void scheduledEvaluation() {
        evaluateOpen().subscribe();
    }

    private Mono<Integer> evaluate(
            TradeOutcomeRepository repository,
            TradeOutcome outcome,
            Instant now
    ) {
        Instant expiresAt = outcome.generatedAt().plus(ENTRY_EXPIRY);
        Instant queryTo = now.isBefore(expiresAt) ? now : expiresAt;

        return repository.findCandles(
                        outcome.symbol(),
                        outcome.generatedAt(),
                        queryTo
                )
                .collectList()
                .flatMap(candles -> {
                    Evaluation evaluation = evaluateCandles(outcome, candles);

                    if (!evaluation.entered()
                            && !now.isBefore(expiresAt)) {
                        evaluation = evaluation.expire(expiresAt);
                    }

                    if (!evaluation.changed()) {
                        return Mono.just(0);
                    }

                    return repository.updateEvaluation(
                                    outcome.id(),
                                    evaluation.status(),
                                    evaluation.entryHitAt(),
                                    evaluation.entryPrice(),
                                    evaluation.target1HitAt(),
                                    evaluation.target2HitAt(),
                                    evaluation.stoppedAt(),
                                    evaluation.expiredAt(),
                                    now
                            )
                            .thenReturn(1);
                });
    }

    private Evaluation evaluateCandles(
            TradeOutcome outcome,
            List<TradeOutcomeRepository.CandleRange> candles
    ) {
        Evaluation evaluation = Evaluation.from(outcome);
        boolean longSide = "LONG".equals(outcome.side());

        for (TradeOutcomeRepository.CandleRange candle : candles) {
            if (!evaluation.entered()) {
                boolean entryTouched = candle.low().compareTo(outcome.entryHigh()) <= 0
                        && candle.high().compareTo(outcome.entryLow()) >= 0;

                if (!entryTouched) {
                    continue;
                }

                BigDecimal entryPrice = midpoint(
                        outcome.entryLow(),
                        outcome.entryHigh()
                );

                evaluation = evaluation.enter(candle.openTime(), entryPrice);
            }

            boolean stopTouched = longSide
                    ? candle.low().compareTo(outcome.invalidationPrice()) <= 0
                    : candle.high().compareTo(outcome.invalidationPrice()) >= 0;

            boolean target2Touched = outcome.target2() != null && (longSide
                    ? candle.high().compareTo(outcome.target2()) >= 0
                    : candle.low().compareTo(outcome.target2()) <= 0);

            boolean target1Touched = outcome.target1() != null && (longSide
                    ? candle.high().compareTo(outcome.target1()) >= 0
                    : candle.low().compareTo(outcome.target1()) <= 0);

            // Conservative handling for ambiguous intrabar ordering.
            if (stopTouched) {
                return evaluation.stop(candle.openTime());
            }

            if (target2Touched) {
                return evaluation.target2(candle.openTime());
            }

            if (target1Touched) {
                evaluation = evaluation.target1(candle.openTime());
            }
        }

        return evaluation;
    }

    private BigDecimal midpoint(BigDecimal low, BigDecimal high) {
        return low.add(high, MC)
                .divide(BigDecimal.valueOf(2), MC);
    }

    private record Evaluation(
            String status,
            Instant entryHitAt,
            BigDecimal entryPrice,
            Instant target1HitAt,
            Instant target2HitAt,
            Instant stoppedAt,
            Instant expiredAt,
            boolean changed
    ) {
        static Evaluation from(TradeOutcome outcome) {
            return new Evaluation(
                    outcome.status(),
                    outcome.entryHitAt(),
                    outcome.entryPrice(),
                    outcome.target1HitAt(),
                    outcome.target2HitAt(),
                    outcome.stoppedAt(),
                    outcome.expiredAt(),
                    false
            );
        }

        boolean entered() {
            return entryHitAt != null;
        }

        Evaluation enter(Instant at, BigDecimal price) {
            return new Evaluation(
                    "ENTRY_HIT", at, price,
                    target1HitAt, target2HitAt, stoppedAt, expiredAt, true
            );
        }

        Evaluation target1(Instant at) {
            return new Evaluation(
                    "TP1_HIT", entryHitAt, entryPrice,
                    target1HitAt == null ? at : target1HitAt,
                    target2HitAt, stoppedAt, expiredAt, true
            );
        }

        Evaluation target2(Instant at) {
            return new Evaluation(
                    "TP2_HIT", entryHitAt, entryPrice,
                    target1HitAt == null ? at : target1HitAt,
                    at, stoppedAt, expiredAt, true
            );
        }

        Evaluation stop(Instant at) {
            return new Evaluation(
                    "STOPPED", entryHitAt, entryPrice,
                    target1HitAt, target2HitAt, at, expiredAt, true
            );
        }

        Evaluation expire(Instant at) {
            return new Evaluation(
                    "EXPIRED", entryHitAt, entryPrice,
                    target1HitAt, target2HitAt, stoppedAt, at, true
            );
        }
    }
}
