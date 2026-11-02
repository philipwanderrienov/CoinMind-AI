package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FuturesPaperEquityArchiveTest {
    private static final Instant COMPLETE = Instant.parse("2026-11-02T09:00:00Z");
    private static FuturesHistoricalWalkForwardRunner.EquityPoint p(String time, String equity, String pnl) {
        return new FuturesHistoricalWalkForwardRunner.EquityPoint(
                Instant.parse(time), new BigDecimal(equity), new BigDecimal(pnl));
    }

    @Test void acceptsVerifiedChronologicalRealizedCurve() {
        assertDoesNotThrow(() -> FuturesPaperEquityArchive.validate(new BigDecimal("1000"), COMPLETE,
                List.of(p("2026-11-02T08:00:00Z","990","-10"),
                        p("2026-11-02T08:30:00Z","1005","15"))));
    }

    @Test void acceptsNoTradeReplay() {
        assertDoesNotThrow(() -> FuturesPaperEquityArchive.validate(
                new BigDecimal("1000"), COMPLETE, List.of()));
    }

    @Test void rejectsEquityMismatch() {
        assertThrows(IllegalArgumentException.class, () -> FuturesPaperEquityArchive.validate(
                new BigDecimal("1000"), COMPLETE,
                List.of(p("2026-11-02T08:00:00Z","900","-10"))));
    }

    @Test void rejectsOutOfOrderTimeAndFutureSettlement() {
        assertThrows(IllegalArgumentException.class, () -> FuturesPaperEquityArchive.validate(
                new BigDecimal("1000"), COMPLETE,
                List.of(p("2026-11-02T08:30:00Z","990","-10"),
                        p("2026-11-02T08:00:00Z","1000","10"))));
        assertThrows(IllegalArgumentException.class, () -> FuturesPaperEquityArchive.validate(
                new BigDecimal("1000"), COMPLETE,
                List.of(p("2026-11-02T10:00:00Z","990","-10"))));
    }

    @Test void rejectsMissingCurveOrInvalidEquity() {
        assertThrows(IllegalArgumentException.class, () -> FuturesPaperEquityArchive.validate(
                BigDecimal.ZERO, COMPLETE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> FuturesPaperEquityArchive.validate(
                new BigDecimal("1000"), COMPLETE, null));
    }
}
