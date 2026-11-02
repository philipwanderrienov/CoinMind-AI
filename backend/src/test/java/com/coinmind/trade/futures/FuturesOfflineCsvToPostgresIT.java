package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Real isolated PostgreSQL: 4 fixture CSV inputs -> validated replay -> internal job
 * -> atomic archive -> independent readback. No production connection or HTTP.
 */
class FuturesOfflineCsvToPostgresIT {
    private static final Instant START = Instant.parse("2026-10-26T00:00:00Z");

    private static String flatCsv(Duration frame, int count) {
        var csv = new StringBuilder("open_time_utc,open,high,low,close\n");
        for (int i = 0; i < count; i++)
            csv.append(START.plus(frame.multipliedBy(i)))
                    .append(",100,100,100,100\n");
        return csv.toString();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "FUTURES_ARCHIVE_TEST_CONFIRM",
            matches = "ISOLATED_TEST_DATABASE_ONLY")
    void fourCsvInputsExecuteJobAndRoundTripPostgres() throws Exception {
        var input = FuturesOfflineCsvBundle.read("SYNTHETIC_FIXTURE",
                new StringReader(flatCsv(Duration.ofMinutes(5), 325)),
                new StringReader(flatCsv(Duration.ofMinutes(15), 109)),
                new StringReader(flatCsv(Duration.ofHours(1), 28)),
                new StringReader(flatCsv(Duration.ofHours(4), 7)),
                2, BigDecimal.ONE, new BigDecimal("2"), BigDecimal.ONE,
                new BigDecimal("0.05"), new BigDecimal("0.05"));
        var factory = FuturesArchiveTestConnection.create();
        var client = DatabaseClient.create(factory);
        var registry = new FuturesPaperReplayRegistry();
        var archive = new FuturesPaperReplayArchive(client, true);
        var equity = new FuturesPaperEquityArchive(client, true);
        var persistence = new FuturesPaperReplayPersistence(registry, archive, equity, factory);
        var workflow = new FuturesPaperReplayWorkflow(registry, archive);
        var job = new FuturesOfflineReplayJob(workflow, persistence, true);
        var history = new FuturesPaperReplayHistory(archive, equity);
        UUID id = null;
        try {
            var saved = job.execute(input,
                    new FuturesHistoricalWalkForwardRunner.PaperSizing(
                            new BigDecimal("1000"), BigDecimal.ONE, new BigDecimal("3")),
                    new BigDecimal("2")).block();
            assertNotNull(saved);
            id = saved.id();
            assertEquals("SYNTHETIC_FIXTURE", saved.provenance());
            assertEquals(0, saved.executed());
            assertEquals(0, saved.initialEquity().compareTo(saved.finalEquity()));
            var readBack = history.findComplete(id).block();
            assertNotNull(readBack);
            assertEquals(id, readBack.summary().id());
            assertTrue(readBack.equityCurve().isEmpty());
            assertFalse(job.isRunning());
        } finally {
            if (id != null)
                client.sql("DELETE FROM futures_paper_replay_archive WHERE id=:id")
                        .bind("id", id).fetch().rowsUpdated().block();
        }
    }
}
