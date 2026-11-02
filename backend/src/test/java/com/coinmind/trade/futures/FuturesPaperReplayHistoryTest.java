package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FuturesPaperReplayHistoryTest {
    private static final Instant COMPLETED = Instant.parse("2026-11-02T04:00:00Z");
    private static FuturesPaperReplayArchive.Archived summary(int executed, String finalEquity) {
        return new FuturesPaperReplayArchive.Archived(UUID.randomUUID(), COMPLETED,
                "SYNTHETIC_FIXTURE", new BigDecimal("1000"), new BigDecimal(finalEquity),
                new BigDecimal("10"), executed, "LOSS_MONITORING", LocalDate.parse("2026-11-02"),
                new BigDecimal("20"), new BigDecimal("10"), new BigDecimal("10"));
    }
    private static FuturesPaperEquityArchive.Point point(int idx, String at, String equity, String pnl) {
        return new FuturesPaperEquityArchive.Point(idx, Instant.parse(at),
                new BigDecimal(equity), new BigDecimal(pnl));
    }

    @Test void acceptsCompleteRealizedCurve() {
        var result = FuturesPaperReplayHistory.verify(summary(2,"1005"), List.of(
                point(0,"2026-11-02T02:00:00Z","990","-10"),
                point(1,"2026-11-02T03:00:00Z","1005","15")));
        assertEquals(2,result.equityCurve().size());
    }
    @Test void rejectsMissingPoints() {
        assertThrows(IllegalStateException.class, () ->
                FuturesPaperReplayHistory.verify(summary(1,"990"), List.of()));
    }
    @Test void rejectsIncorrectPointIndex() {
        assertThrows(IllegalStateException.class, () ->
                FuturesPaperReplayHistory.verify(summary(1,"990"), List.of(
                        point(2,"2026-11-02T02:00:00Z","990","-10"))));
    }
    @Test void rejectsInvalidPnl() {
        assertThrows(IllegalStateException.class, () ->
                FuturesPaperReplayHistory.verify(summary(1,"990"), List.of(
                        point(0,"2026-11-02T02:00:00Z","990","-5"))));
    }
    @Test void rejectsIncorrectFinalEquity() {
        assertThrows(IllegalStateException.class, () ->
                FuturesPaperReplayHistory.verify(summary(1,"999"), List.of(
                        point(0,"2026-11-02T02:00:00Z","990","-10"))));
    }
    @Test void allowsNoTradesOnlyWhenEquityUnchanged() {
        assertEquals(0,FuturesPaperReplayHistory.verify(summary(0,"1000"), List.of()).equityCurve().size());
        assertThrows(IllegalStateException.class, () ->
                FuturesPaperReplayHistory.verify(summary(0,"990"), List.of()));
    }
}
