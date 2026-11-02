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
    /**
     * All four CSVs are aggregated from the SAME rising 5m fixture.
     * Tests a nonempty realized curve, arithmetic PnL reconciliation and DB readback.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "FUTURES_ARCHIVE_TEST_CONFIRM",
            matches = "ISOLATED_TEST_DATABASE_ONLY")
    void trendingCsvProducesPersistedRealizedPnl() throws Exception {
        var input = FuturesOfflineCsvBundle.read("SYNTHETIC_FIXTURE",
                new StringReader(trendingCsv(1, 384)),
                new StringReader(trendingCsv(3, 128)),
                new StringReader(trendingCsv(12, 32)),
                new StringReader(trendingCsv(48, 8)),
                2, BigDecimal.ONE, new BigDecimal("2"), BigDecimal.ONE,
                new BigDecimal("0.05"), new BigDecimal("0.05"));
        var factory = FuturesArchiveTestConnection.create();
        var client = DatabaseClient.create(factory);
        var registry = new FuturesPaperReplayRegistry();
        var archive = new FuturesPaperReplayArchive(client, true);
        var equity = new FuturesPaperEquityArchive(client, true);
        var persistence = new FuturesPaperReplayPersistence(registry, archive, equity, factory);
        var job = new FuturesOfflineReplayJob(
                new FuturesPaperReplayWorkflow(registry, archive), persistence, true);
        var history = new FuturesPaperReplayHistory(archive, equity);
        UUID id = null;
        try {
            var saved = job.execute(input,
                    new FuturesHistoricalWalkForwardRunner.PaperSizing(
                            new BigDecimal("1000"), BigDecimal.ONE, new BigDecimal("3")),
                    new BigDecimal("2")).block();
            assertNotNull(saved);
            id = saved.id();
            assertTrue(saved.executed() > 0, "Rising fixture should produce completed paper trades");
            var readBack = history.findComplete(id).block();
            assertNotNull(readBack);
            assertEquals(saved.executed(), readBack.equityCurve().size());
            assertTrue(readBack.equityCurve().stream()
                    .anyMatch(point -> point.netPnlUsdt().signum() != 0));
            var realized = readBack.equityCurve().stream()
                    .map(FuturesPaperEquityArchive.Point::netPnlUsdt)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, saved.initialEquity().add(realized)
                    .compareTo(readBack.summary().finalEquity()));
            assertFalse(job.isRunning());
        } finally {
            if (id != null) client.sql("DELETE FROM futures_paper_replay_archive WHERE id=:id")
                    .bind("id", id).fetch().rowsUpdated().block();
        }
    }

    private static String trendingCsv(int fiveMinuteBarsPerCandle, int count) {
        var csv = new StringBuilder("open_time_utc,open,high,low,close\n");
        for (int i = 0; i < count; i++) {
            int first = i * fiveMinuteBarsPerCandle;
            int last = first + fiveMinuteBarsPerCandle - 1;
            var open = BigDecimal.valueOf(100).add(BigDecimal.valueOf(first, 2));
            var close = BigDecimal.valueOf(100).add(BigDecimal.valueOf(last, 2));
            csv.append(START.plus(Duration.ofMinutes(5L * first))).append(',')
                    .append(open.toPlainString()).append(',')
                    .append(close.toPlainString()).append(',')
                    .append(open.toPlainString()).append(',')
                    .append(close.toPlainString()).append('\n');
        }
        return csv.toString();
    }

}
