package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/** Integration commit/rollback checks against disposable test PostgreSQL only. */
class FuturesPaperReplayTransactionIT {
    private static final Instant AT = Instant.parse("2026-11-02T03:00:00Z");

    @Test
    @EnabledIfEnvironmentVariable(named="FUTURES_ARCHIVE_TEST_CONFIRM", matches="ISOLATED_TEST_DATABASE_ONLY")
    void commitsBothTablesAndRollsBackOnCurveFailure() {
        var factory = FuturesArchiveTestConnection.create();
        var client = DatabaseClient.create(factory);
        var summary = new FuturesPaperReplayArchive(client, true);
        var equity = new FuturesPaperEquityArchive(client, true);
        var tx = TransactionalOperator.create(new R2dbcTransactionManager(factory));
        UUID success = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        var points = List.of(new FuturesHistoricalWalkForwardRunner.EquityPoint(
                AT.minusSeconds(60), new BigDecimal("992"), new BigDecimal("-8")));
        try {
            tx.transactional(summary.saveTrusted(snapshot(success)).flatMap(saved ->
                    equity.saveTrusted(success, new BigDecimal("1000"), AT, points).thenReturn(saved))).block();
            assertNotNull(summary.find(success).block());
            assertEquals(1, equity.points(success).collectList().block().size());
            var history = new FuturesPaperReplayHistory(summary, equity).findComplete(success).block();
            assertNotNull(history);
            assertEquals(1, history.equityCurve().size());
            assertEquals(0, history.summary().finalEquity().compareTo(new BigDecimal("992")));

            // Deliberate DB failure AFTER summary INSERT, inside the same transaction.
            // A duplicate existing point index triggers PostgreSQL unique violation.
            assertThrows(RuntimeException.class, () -> tx.transactional(
                    summary.saveTrusted(snapshot(failed)).flatMap(saved ->
                            client.sql("INSERT INTO futures_paper_replay_equity "
                                    + "(replay_id, point_index, realized_at, equity_usdt, net_pnl_usdt) "
                                    + "VALUES (:id, 0, :at, 992, -8)")
                                    .bind("id", failed).bind("at", AT.minusSeconds(60))
                                    .fetch().rowsUpdated()
                                    .then(client.sql("INSERT INTO futures_paper_replay_equity "
                                            + "(replay_id, point_index, realized_at, equity_usdt, net_pnl_usdt) "
                                            + "VALUES (:id, 0, :at, 992, -8)")
                                            .bind("id", failed).bind("at", AT.minusSeconds(60))
                                            .fetch().rowsUpdated())
                                    .thenReturn(saved))).block());
            assertNull(summary.find(failed).block(), "Summary must roll back on equity write failure");
            assertEquals(0, equity.points(failed).count().block());
            assertNull(new FuturesPaperReplayHistory(summary, equity).findComplete(failed).block());
        } finally {
            for (UUID id : List.of(success, failed)) {
                client.sql("DELETE FROM futures_paper_replay_archive WHERE id=:id")
                        .bind("id", id).fetch().rowsUpdated().block();
            }
        }
    }

    private static FuturesPaperReplayRegistry.Snapshot snapshot(UUID id) {
        var risk = new FuturesPaperRiskStatus.Snapshot("OFFLINE_ONLY", false,
                "LOSS_MONITORING", true, LocalDate.parse("2026-11-02"),
                new BigDecimal("20"), new BigDecimal("8"), new BigDecimal("12"),
                new BigDecimal("992"), "Test only");
        return new FuturesPaperReplayRegistry.Snapshot(id, AT, "SYNTHETIC_FIXTURE",
                new BigDecimal("1000"), new BigDecimal("992"), new BigDecimal("8"), 1, risk);
    }
}
