package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class FuturesOfflineReplayJobTest {
    @Test void disabledJobNeverCallsPersistence() {
        var job = new FuturesOfflineReplayJob(null, null, false);
        StepVerifier.create(job.execute(null, null, null))
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("disabled")).verify();
        assertFalse(job.isRunning());
    }

    @Test void enabledJobRejectsNonOfflineProvenanceBeforeExecution() {
        var job = new FuturesOfflineReplayJob(null, null, true);
        var input = new FuturesHistoricalWalkForwardRunner.Input("LIVE_EXCHANGE",
                List.of(), List.of(), List.of(), List.of(), 2,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO);
        StepVerifier.create(job.execute(input, null, null))
                .expectError(IllegalArgumentException.class).verify();
        assertFalse(job.isRunning());
    }

    @Test void enabledJobRejectsMissingInput() {
        var job = new FuturesOfflineReplayJob(null, null, true);
        StepVerifier.create(job.execute(null, null, null))
                .expectError(IllegalArgumentException.class).verify();
        assertFalse(job.isRunning());
    }
}
