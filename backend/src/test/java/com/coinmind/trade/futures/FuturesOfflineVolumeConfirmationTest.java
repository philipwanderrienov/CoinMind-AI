package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FuturesOfflineVolumeConfirmationTest {
    private static final Instant START = Instant.parse("2026-06-01T00:00:00Z");
    private List<FuturesReplayEngine.Candle> candles() {
        return IntStream.range(0, 325).mapToObj(i -> {
            var price = BigDecimal.valueOf(100L + i);
            return new FuturesReplayEngine.Candle(START.plus(Duration.ofMinutes(5L * i)),
                    price, price, price, price);
        }).toList();
    }

    private List<FuturesOfflineVolumeConfirmation.Bar> volumes(BigDecimal takerShare) {
        return candles().stream().map(c -> new FuturesOfflineVolumeConfirmation.Bar(
                c.time(), new BigDecimal("100"), new BigDecimal("10000"), 50,
                new BigDecimal("100").multiply(takerShare),
                new BigDecimal("10000").multiply(takerShare))).toList();
    }

    private List<FuturesReplayEngine.Candle> tf(long minutes, int count) {
        return IntStream.range(0, count).mapToObj(i -> {
            var price = BigDecimal.valueOf(100L + i);
            return new FuturesReplayEngine.Candle(START.plus(Duration.ofMinutes(minutes * i)),
                    price, price, price, price);
        }).toList();
    }

    private FuturesHistoricalWalkForwardRunner.Input input() {
        return new FuturesHistoricalWalkForwardRunner.Input(
                "HISTORICAL_REPLAY", candles(), tf(15, 109), tf(60, 28), tf(240, 7),
                12, BigDecimal.ONE, new BigDecimal("2"), BigDecimal.ONE,
                new BigDecimal("0.05"), new BigDecimal("0.05"));
    }

    @Test void usesOnlyLastClosedBarAndPriorTwentyVolumeBars() {
        var five = candles();
        var gate = new FuturesOfflineVolumeConfirmation(volumes(new BigDecimal("0.7")), five);
        Instant decision = five.get(24).time().plus(Duration.ofMinutes(5));
        assertTrue(gate.permits(24, decision, "LONG", five));
        assertFalse(gate.permits(24, decision, "SHORT", five));
        assertThrows(IllegalArgumentException.class,
                () -> gate.permits(24, five.get(24).time(), "LONG", five));
        assertThrows(IllegalArgumentException.class,
                () -> gate.permits(19, decision, "LONG", five));
    }

    @Test void rejectsMismatchedTimestampsOrImpossibleTakerVolume() {
        var five = candles();
        var wrong = new java.util.ArrayList<>(volumes(new BigDecimal("0.6")));
        var first = wrong.getFirst();
        wrong.set(0, new FuturesOfflineVolumeConfirmation.Bar(
                first.openTime().plusSeconds(5), first.baseVolume(), first.quoteVolume(),
                first.tradeCount(), first.takerBuyBaseVolume(), first.takerBuyQuoteVolume()));
        assertThrows(IllegalArgumentException.class,
                () -> new FuturesOfflineVolumeConfirmation(wrong, five));
        assertThrows(IllegalArgumentException.class,
                () -> new FuturesOfflineVolumeConfirmation(volumes(new BigDecimal("1.1")), five));
    }

    @Test void filtersBeforeOccupancyAndRecomputesEquityWithoutTouchingBaseline() {
        var input = input();
        var sizing = new FuturesHistoricalWalkForwardRunner.PaperSizing(
                new BigDecimal("1000"), BigDecimal.ONE, new BigDecimal("3"));
        var baseline = FuturesHistoricalWalkForwardRunner.runWithDynamicEquityExperimental(
                input, sizing, new BigDecimal("2"),
                FuturesHistoricalWalkForwardRunner.ExperimentalDirection.BOTH);
        assertTrue(baseline.executed() > 0, "Fixture needs real qualifying signals");
        var bullish = new FuturesOfflineVolumeConfirmation(volumes(new BigDecimal("0.7")), input.m5());
        var bearish = new FuturesOfflineVolumeConfirmation(volumes(new BigDecimal("0.3")), input.m5());
        var accepted = FuturesHistoricalWalkForwardRunner.runWithDynamicEquityVolumeResearch(
                input, sizing, new BigDecimal("2"),
                FuturesHistoricalWalkForwardRunner.ExperimentalDirection.BOTH, bullish);
        var blocked = FuturesHistoricalWalkForwardRunner.runWithDynamicEquityVolumeResearch(
                input, sizing, new BigDecimal("2"),
                FuturesHistoricalWalkForwardRunner.ExperimentalDirection.BOTH, bearish);
        assertEquals(baseline, accepted, "All qualifying signals should replay exactly as baseline");
        assertEquals(0, blocked.executed());
        assertEquals(0, blocked.finalEquity().compareTo(blocked.initialEquity()));
        assertTrue(blocked.equityCurve().isEmpty());
        assertFalse(blocked.liveSignalEligible());
    }
}
