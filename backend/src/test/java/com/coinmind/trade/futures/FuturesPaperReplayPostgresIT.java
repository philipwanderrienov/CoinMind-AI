package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.r2dbc.core.DatabaseClient;
import io.r2dbc.spi.ConnectionFactories;
import reactor.core.publisher.Mono;

/**
 * Manual integration test: never runs during ordinary mvn verify.
 * Point FUTURES_ARCHIVE_TEST_R2DBC_URL exclusively to an isolated disposable test DB.
 * Install the replay archive schema there before running this test.
 */
class FuturesPaperReplayPostgresIT {
    @Test
    @EnabledIfEnvironmentVariable(named = "FUTURES_ARCHIVE_TEST_CONFIRM", matches = "ISOLATED_TEST_DATABASE_ONLY")
    void insertsReadsListsAndPersistsAcrossNewRepositoryInstance() {
        String url = System.getenv("FUTURES_ARCHIVE_TEST_R2DBC_URL");
        if (url == null || !url.startsWith("r2dbc:postgresql://"))
            throw new IllegalArgumentException("Explicit dedicated PostgreSQL test URL required");
        // Fail closed on accidentally reusing the standard application coinmind database.
        if (url.matches("(?i).*/coinmind(?:\\?.*)?$"))
            throw new IllegalArgumentException("Never run integration test against coinmind production database");

        DatabaseClient client = DatabaseClient.create(ConnectionFactories.get(url));
        FuturesPaperReplayArchive writer = new FuturesPaperReplayArchive(client, true);
        FuturesPaperReplayArchive readerAfterRestart = new FuturesPaperReplayArchive(client, true);
        UUID id = UUID.randomUUID();
        Instant completed = Instant.parse("2026-11-02T03:00:00Z");
        var risk = new FuturesPaperRiskStatus.Snapshot("OFFLINE_ONLY", false,
                "LOSS_MONITORING", true, LocalDate.parse("2026-11-02"),
                new BigDecimal("20"), new BigDecimal("8"), new BigDecimal("12"),
                new BigDecimal("992"), "Integration test only");
        var snapshot = new FuturesPaperReplayRegistry.Snapshot(id, completed, "SYNTHETIC_FIXTURE",
                new BigDecimal("1000"), new BigDecimal("992"), new BigDecimal("8"), 1, risk);
        try {
            var saved = writer.saveTrusted(snapshot).block();
            assertNotNull(saved);
            assertEquals(id, saved.id());
            assertEquals("LOSS_MONITORING", saved.riskStatus());
            var read = readerAfterRestart.find(id).block();
            assertNotNull(read);
            assertEquals(0, read.finalEquity().compareTo(new BigDecimal("992")));
            assertEquals("SYNTHETIC_FIXTURE", read.provenance());
            assertTrue(readerAfterRestart.recent(100).collectList().block().stream()
                    .anyMatch(entry -> entry.id().equals(id)));
            // ON CONFLICT must not overwrite a previously archived result.
            assertEquals(0, writer.saveTrusted(snapshot).block().finalEquity()
                    .compareTo(new BigDecimal("992")));
        } finally {
            client.sql("DELETE FROM futures_paper_replay_archive WHERE id = :id")
                    .bind("id", id).fetch().rowsUpdated().onErrorResume(error -> Mono.empty()).block();
        }
    }
}
