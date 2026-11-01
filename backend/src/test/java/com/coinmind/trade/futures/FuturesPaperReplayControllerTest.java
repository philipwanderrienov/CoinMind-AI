package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class FuturesPaperReplayControllerTest {
    private static final Instant T = Instant.parse("2026-10-30T03:00:00Z");
    private static FuturesHistoricalWalkForwardRunner.DynamicReport replay() {
        return new FuturesHistoricalWalkForwardRunner.DynamicReport("OFFLINE_ONLY", false,
                new BigDecimal("1000"), new BigDecimal("990"), new BigDecimal("10"),
                0, 0, 1, List.of(new FuturesHistoricalWalkForwardRunner.EquityPoint(
                        T.minusSeconds(60), new BigDecimal("990"), new BigDecimal("-10"))),
                null, "TEST ONLY");
    }
    @Test void disabledEndpointFailsClosed() {
        var store = new FuturesPaperReplayRegistry();
        UUID id = store.recordTrustedReplay("SYNTHETIC_FIXTURE", replay(), new BigDecimal("2"), T);
        assertThrows(ResponseStatusException.class,
                () -> new FuturesPaperReplayController(store, false).risk(id));
    }
    @Test void enabledEndpointOnlyReadsRegisteredOfflineReport() {
        var store = new FuturesPaperReplayRegistry();
        var controller = new FuturesPaperReplayController(store, true);
        UUID id = store.recordTrustedReplay("SYNTHETIC_FIXTURE", replay(), new BigDecimal("2"), T);
        var result = controller.risk(id);
        assertEquals(id, result.id());
        assertEquals("LOSS_MONITORING", result.risk().status());
        assertFalse(result.risk().liveSignalEligible());
        assertThrows(ResponseStatusException.class, () -> controller.risk(UUID.randomUUID()));
    }
    @Test void registryRejectsLiveReports() {
        var store = new FuturesPaperReplayRegistry();
        assertThrows(IllegalArgumentException.class, () ->
                store.recordTrustedReplay("LIVE_EXCHANGE", replay(), new BigDecimal("2"), T));
    }
    @Test void registryRejectsFutureSettlement() {
        var store = new FuturesPaperReplayRegistry();
        assertThrows(IllegalArgumentException.class, () ->
                store.recordTrustedReplay("SYNTHETIC_FIXTURE", replay(), new BigDecimal("2"), T.minusSeconds(90)));
    }
}
