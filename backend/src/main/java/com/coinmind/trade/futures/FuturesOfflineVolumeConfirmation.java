package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Frozen, opt-in OFFLINE research filter. Data must come from a separately
 * checksum-validated historical 5m sidecar, never generated from OHLC.
 * The volume at index i is from the CLOSED signal bar, and RVOL20 compares it
 * with the preceding 20 bars. No production entry signal uses this class.
 */
public final class FuturesOfflineVolumeConfirmation {
    private static final MathContext MC = MathContext.DECIMAL64;
    public record Bar(Instant openTime, BigDecimal baseVolume, BigDecimal quoteVolume,
                      long tradeCount, BigDecimal takerBuyBaseVolume,
                      BigDecimal takerBuyQuoteVolume) {}

    private final List<Bar> bars;
    public FuturesOfflineVolumeConfirmation(List<Bar> data,
                                             List<FuturesReplayEngine.Candle> candles) {
        if (data == null || candles == null || data.size() != candles.size() || data.size() < 23)
            throw new IllegalArgumentException("Complete aligned 5m volume dataset required");
        Instant previous = null;
        for (int i = 0; i < data.size(); i++) {
            Bar bar = data.get(i);
            if (bar == null || bar.openTime() == null || bar.baseVolume() == null
                    || bar.quoteVolume() == null || bar.takerBuyBaseVolume() == null
                    || bar.takerBuyQuoteVolume() == null || bar.tradeCount() < 0
                    || !bar.openTime().equals(candles.get(i).time())
                    || previous != null && !bar.openTime().equals(previous.plus(Duration.ofMinutes(5))))
                throw new IllegalArgumentException("Missing or unaligned historical volume candle");
            if (bar.baseVolume().signum() < 0 || bar.quoteVolume().signum() < 0
                    || bar.takerBuyBaseVolume().signum() < 0
                    || bar.takerBuyQuoteVolume().signum() < 0
                    || bar.takerBuyBaseVolume().compareTo(bar.baseVolume()) > 0
                    || bar.takerBuyQuoteVolume().compareTo(bar.quoteVolume()) > 0)
                throw new IllegalArgumentException("Invalid exchange volume");
            previous = bar.openTime();
        }
        bars = List.copyOf(data);
    }

    public boolean permits(int closedSignalIndex, Instant decisionAt, String side,
                           List<FuturesReplayEngine.Candle> candles) {
        if (closedSignalIndex < 20 || closedSignalIndex >= bars.size()
                || candles == null || candles.size() != bars.size()
                || decisionAt == null || !decisionAt.equals(bars.get(closedSignalIndex)
                    .openTime().plus(Duration.ofMinutes(5)))
                || !List.of("LONG", "SHORT").contains(side))
            throw new IllegalArgumentException("Closed 5m decision and explicit side required");
        BigDecimal precedingVolume = BigDecimal.ZERO;
        for (int i = closedSignalIndex - 20; i < closedSignalIndex; i++)
            precedingVolume = precedingVolume.add(bars.get(i).baseVolume());
        if (precedingVolume.signum() <= 0) return false;
        Bar signal = bars.get(closedSignalIndex);
        if (signal.baseVolume().signum() <= 0) return false;
        // Exact RVOL >= 1, avoiding intermediate rounding.
        if (signal.baseVolume().multiply(BigDecimal.valueOf(20))
                .compareTo(precedingVolume) < 0) return false;
        int pressure = signal.takerBuyBaseVolume().multiply(BigDecimal.valueOf(2))
                .compareTo(signal.baseVolume());
        if ("LONG".equals(side) && pressure <= 0 || "SHORT".equals(side) && pressure >= 0)
            return false;
        if (closedSignalIndex < 2) return false;
        int direction = "LONG".equals(side) ? 1 : -1;
        BigDecimal priorChange = candles.get(closedSignalIndex - 1).close()
                .subtract(candles.get(closedSignalIndex - 2).close())
                .multiply(BigDecimal.valueOf(direction), MC);
        BigDecimal latestChange = candles.get(closedSignalIndex).close()
                .subtract(candles.get(closedSignalIndex - 1).close())
                .multiply(BigDecimal.valueOf(direction), MC);
        return latestChange.compareTo(priorChange) >= 0;
    }
}
