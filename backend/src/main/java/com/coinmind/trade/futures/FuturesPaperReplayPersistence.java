package com.coinmind.trade.futures;

import java.util.UUID;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * Explicit internal-only archival of summary followed by realized equity curve.
 * No HTTP route, scheduler or automatic subscription. Requires opt-in schema.
 * Summary and realized points commit as a single reactive R2DBC transaction.
 */
@Service
public final class FuturesPaperReplayPersistence {
    private final FuturesPaperReplayRegistry registry;
    private final FuturesPaperReplayArchive summaries;
    private final FuturesPaperEquityArchive curves;
    private final TransactionalOperator transactions;

    public FuturesPaperReplayPersistence(FuturesPaperReplayRegistry registry,
                                         FuturesPaperReplayArchive summaries,
                                         FuturesPaperEquityArchive curves,
                                         ConnectionFactory connectionFactory) {
        this.registry = registry;
        this.summaries = summaries;
        this.curves = curves;
        this.transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
    }

    public Mono<FuturesPaperReplayArchive.Archived> archiveRegisteredWithCurve(UUID replayId) {
        var snapshot = registry.find(replayId);
        if (snapshot == null)
            return Mono.error(new IllegalArgumentException("Unknown paper replay"));
        var points = registry.equityCurve(replayId);
        // Validate before inserting the summary. If the curve insert fails, do not
        // claim full success. The same ID can be retried safely because both
        // tables use ON CONFLICT DO NOTHING.
        FuturesPaperEquityArchive.validate(snapshot.initialEquity(), snapshot.completedAt(), points);
        if (snapshot.executed() != points.size())
            return Mono.error(new IllegalArgumentException("Executed count and curve length differ"));
        if (!points.isEmpty() && snapshot.finalEquity().compareTo(points.get(points.size() - 1).equityUsdt()) != 0)
            return Mono.error(new IllegalArgumentException("Final equity does not match curve"));
        if (points.isEmpty() && snapshot.finalEquity().compareTo(snapshot.initialEquity()) != 0)
            return Mono.error(new IllegalArgumentException("Empty curve must preserve initial equity"));
        return transactions.transactional(summaries.saveTrusted(snapshot)
                .flatMap(saved -> curves.saveTrusted(replayId, snapshot.initialEquity(),
                        snapshot.completedAt(), points).thenReturn(saved)));
    }
}
