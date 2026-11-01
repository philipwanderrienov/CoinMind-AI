package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FuturesPaperReplayArchiveTest {
    private FuturesPaperReplayRegistry.Snapshot snapshot(String source, boolean live) {
        var risk = new FuturesPaperRiskStatus.Snapshot("OFFLINE_ONLY", live,
                "LOSS_MONITORING", true, LocalDate.parse("2026-11-01"),
                new BigDecimal("20"), new BigDecimal("8"), new BigDecimal("12"),
                new BigDecimal("992"), "paper");
        return new FuturesPaperReplayRegistry.Snapshot(UUID.randomUUID(),
                Instant.parse("2026-11-01T12:00:00Z"), source,
                new BigDecimal("1000"), new BigDecimal("992"),
                new BigDecimal("8"), 1, risk);
    }
    @Test void acceptsOfflineSyntheticSummary() {
        assertDoesNotThrow(() -> FuturesPaperReplayArchive.validate(
                snapshot("SYNTHETIC_FIXTURE", false)));
    }
    @Test void acceptsOfflineHistoricalSummary() {
        assertDoesNotThrow(() -> FuturesPaperReplayArchive.validate(
                snapshot("HISTORICAL_REPLAY", false)));
    }
    @Test void rejectsLiveProvenanceAndEligibility() {
        assertThrows(IllegalArgumentException.class,
                () -> FuturesPaperReplayArchive.validate(snapshot("LIVE_EXCHANGE", false)));
        assertThrows(IllegalArgumentException.class,
                () -> FuturesPaperReplayArchive.validate(snapshot("SYNTHETIC_FIXTURE", true)));
    }
    @Test void rejectsNullSnapshot() {
        assertThrows(IllegalArgumentException.class, () -> FuturesPaperReplayArchive.validate(null));
    }
}
