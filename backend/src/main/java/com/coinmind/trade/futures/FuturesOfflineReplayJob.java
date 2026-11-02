package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Explicitly invoked internal offline replay job. Never scheduled, never exposed via HTTP.
 * Opt-in only. Trusted callers supply vetted historical/fixture candles and must subscribe.
 */
@Service
public final class FuturesOfflineReplayJob {
    private static final int MAX_M5_CANDLES = 100_000;
    private final FuturesPaperReplayWorkflow workflow;
    private final FuturesPaperReplayPersistence persistence;
    private final boolean enabled;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public FuturesOfflineReplayJob(FuturesPaperReplayWorkflow workflow,
                                   FuturesPaperReplayPersistence persistence,
                                   @Value("${coinmind.futures.offline-replay-job-enabled:false}") boolean enabled) {
        this.workflow = workflow;
        this.persistence = persistence;
        this.enabled = enabled;
    }

    /** Fails closed when disabled; at most one concurrent job per backend process. */
    public Mono<FuturesPaperReplayArchive.Archived> execute(
            FuturesHistoricalWalkForwardRunner.Input input,
            FuturesHistoricalWalkForwardRunner.PaperSizing sizing,
            BigDecimal dailyLossLimitPercent) {
        return Mono.defer(() -> {
            if (!enabled) return Mono.error(new IllegalStateException("Offline replay job disabled"));
            if (input == null || input.m5() == null || input.m5().size() > MAX_M5_CANDLES
                    || (!"HISTORICAL_REPLAY".equals(input.provenance())
                        && !"SYNTHETIC_FIXTURE".equals(input.provenance())))
                return Mono.error(new IllegalArgumentException("Bounded offline candle input required"));
            if (!running.compareAndSet(false, true))
                return Mono.error(new IllegalStateException("An offline replay job is already running"));
            try {
                Instant completedAt = Instant.now(Clock.systemUTC());
                return workflow.runAndArchive(input, sizing, dailyLossLimitPercent,
                        completedAt, persistence)
                        .subscribeOn(Schedulers.boundedElastic())
                        .doFinally(signal -> running.set(false));
            } catch (RuntimeException ex) {
                running.set(false);
                return Mono.error(ex);
            }
        });
    }

    public boolean isRunning() { return running.get(); }
}
