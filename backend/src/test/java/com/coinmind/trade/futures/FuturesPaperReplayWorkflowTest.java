package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class FuturesPaperReplayWorkflowTest {
    private FuturesPaperReplayWorkflow workflow() {
        return new FuturesPaperReplayWorkflow(new FuturesPaperReplayRegistry(), null);
    }

    @Test void rejectsMissingInputBeforeAnyReplayOrArchive() {
        assertThrows(IllegalArgumentException.class, () ->
                workflow().runAndRegister(null,
                        new FuturesHistoricalWalkForwardRunner.PaperSizing(
                                new BigDecimal("1000"), BigDecimal.ONE, new BigDecimal("3")),
                        new BigDecimal("2"), Instant.parse("2026-11-02T00:00:00Z")));
    }

    @Test void rejectsLiveSourceBeforeRegistration() {
        var in = new FuturesHistoricalWalkForwardRunner.Input("LIVE_EXCHANGE",
                List.of(), List.of(), List.of(), List.of(), 2,
                BigDecimal.ONE, new BigDecimal("2"), BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () ->
                workflow().runAndRegister(in, null, BigDecimal.ONE, Instant.now()));
    }

    @Test void refusesToArchiveUnknownIdWithoutDatabaseCall() {
        StepVerifier.create(workflow().archiveRegistered(UUID.randomUUID()))
                .expectError(IllegalArgumentException.class).verify();
    }
}
