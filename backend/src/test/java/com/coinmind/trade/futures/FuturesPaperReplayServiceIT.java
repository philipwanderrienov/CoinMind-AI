package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.r2dbc.core.DatabaseClient;

/** Full internal service round-trip on isolated, disposable PostgreSQL test DB. */
class FuturesPaperReplayServiceIT {
    @Test
    @EnabledIfEnvironmentVariable(named="FUTURES_ARCHIVE_TEST_CONFIRM", matches="ISOLATED_TEST_DATABASE_ONLY")
    void servicePersistsValidatedSummaryAndRealizedCurve() {
        var factory = FuturesArchiveTestConnection.create();
        var client = DatabaseClient.create(factory);
        var registry = new FuturesPaperReplayRegistry();
        var summaries = new FuturesPaperReplayArchive(client, true);
        var curves = new FuturesPaperEquityArchive(client, true);
        var persistence = new FuturesPaperReplayPersistence(registry, summaries, curves, factory);
        var history = new FuturesPaperReplayHistory(summaries, curves);
        Instant completed = Instant.parse("2026-11-02T04:00:00Z");
        var points = List.of(new FuturesHistoricalWalkForwardRunner.EquityPoint(
                completed.minusSeconds(3600), new BigDecimal("990"), new BigDecimal("-10")),
                new FuturesHistoricalWalkForwardRunner.EquityPoint(
                        completed.minusSeconds(1800), new BigDecimal("1005"), new BigDecimal("15")));
        var report = new FuturesHistoricalWalkForwardRunner.DynamicReport(
                "OFFLINE_ONLY", false, new BigDecimal("1000"), new BigDecimal("1005"),
                new BigDecimal("10"), 0, 0, 2, points, null, "Isolated integration fixture");
        var id = registry.recordTrustedReplay("SYNTHETIC_FIXTURE", report,
                new BigDecimal("2"), completed);
        try {
            assertEquals(id, persistence.archiveRegisteredWithCurve(id).block().id());
            var persisted = history.findComplete(id).block();
            assertNotNull(persisted);
            assertEquals(2, persisted.equityCurve().size());
            assertEquals(0, persisted.summary().finalEquity().compareTo(new BigDecimal("1005")));
            assertEquals(id, persistence.archiveRegisteredWithCurve(id).block().id(),
                    "Archiving the same replay ID should be retryable");
        } finally {
            client.sql("DELETE FROM futures_paper_replay_archive WHERE id=:id")
                    .bind("id", id).fetch().rowsUpdated().block();
        }
    }
}
