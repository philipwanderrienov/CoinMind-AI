package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Internal-only entry point to run, register, then optionally archive a paper replay.
 * No HTTP write endpoint; callers must provide vetted offline market candles.
 */
@Service
public final class FuturesPaperReplayWorkflow {
    private final FuturesPaperReplayRegistry registry;
    private final FuturesPaperReplayArchive archive;

    public FuturesPaperReplayWorkflow(FuturesPaperReplayRegistry registry,
                                      FuturesPaperReplayArchive archive) {
        this.registry = registry;
        this.archive = archive;
    }

    /** Validates replay and stores a server-generated snapshot in the process-local registry. */
    public UUID runAndRegister(FuturesHistoricalWalkForwardRunner.Input input,
                               FuturesHistoricalWalkForwardRunner.PaperSizing sizing,
                               BigDecimal dailyLossLimitPercent, Instant completedAt) {
        if (input == null || completedAt == null
                || (!"SYNTHETIC_FIXTURE".equals(input.provenance())
                    && !"HISTORICAL_REPLAY".equals(input.provenance()))) {
            throw new IllegalArgumentException("Vetted offline input required");
        }
        var report = FuturesHistoricalWalkForwardRunner.runWithDynamicEquity(
                input, sizing, dailyLossLimitPercent);
        return registry.recordTrustedReplay(input.provenance(), report,
                dailyLossLimitPercent, completedAt);
    }

    /** Explicit opt-in archival step. Never subscribe internally or swallow database errors. */
    public Mono<FuturesPaperReplayArchive.Archived> archiveRegistered(UUID id) {
        var snapshot = registry.find(id);
        if (snapshot == null) return Mono.error(new IllegalArgumentException("Unknown paper replay"));
        return archive.saveTrusted(snapshot);
    }
}
