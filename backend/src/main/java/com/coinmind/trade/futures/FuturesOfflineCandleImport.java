package com.coinmind.trade.futures;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Strict offline CSV candle ingestion. No network, file system, exchange or live signal I/O.
 * Rows: open_time_utc,open,high,low,close; UTC timestamp must be ISO-8601 instant.
 * Caller must verify the dataset's origin independently before claiming historical authenticity.
 */
public final class FuturesOfflineCandleImport {
    private FuturesOfflineCandleImport() {}
    private static final int MAX_ROWS = 100_000;

    public static List<FuturesReplayEngine.Candle> parse(Reader reader, Duration frame) throws IOException {
        if (reader == null || frame == null
                || !List.of(Duration.ofMinutes(5),Duration.ofMinutes(15),
                    Duration.ofHours(1),Duration.ofHours(4)).contains(frame))
            throw new IllegalArgumentException("Supported timeframe and reader required");
        var rows = new ArrayList<FuturesReplayEngine.Candle>();
        var input = new BufferedReader(reader);
        String line = input.readLine();
        if (!"open_time_utc,open,high,low,close".equals(line))
            throw new IllegalArgumentException("Exact CSV header required");
        Instant previous = null;
        while ((line = input.readLine()) != null) {
            if (rows.size() >= MAX_ROWS) throw new IllegalArgumentException("Too many candles");
            if (line.isBlank() || line.length() > 512) throw new IllegalArgumentException("Blank or oversized CSV row");
            String[] cells = line.split(",", -1);
            if (cells.length != 5) throw new IllegalArgumentException("Exactly five CSV fields required");
            try {
                Instant time = Instant.parse(cells[0]);
                var open = new BigDecimal(cells[1]);
                var high = new BigDecimal(cells[2]);
                var low = new BigDecimal(cells[3]);
                var close = new BigDecimal(cells[4]);
                if (time.getNano() != 0
                    || Math.floorMod(time.getEpochSecond(), frame.toSeconds()) != 0
                    || (previous != null && !time.equals(previous.plus(frame)))
                    || low.signum() <= 0 || high.compareTo(low) < 0
                    || open.compareTo(low) < 0 || open.compareTo(high) > 0
                    || close.compareTo(low) < 0 || close.compareTo(high) > 0)
                    throw new IllegalArgumentException("Invalid, missing or unaligned OHLC candle");
                rows.add(new FuturesReplayEngine.Candle(time, open, high, low, close));
                previous = time;
            } catch (java.time.format.DateTimeParseException | NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid candle time or decimal", ex);
            }
        }
        if (rows.size() < 3) throw new IllegalArgumentException("At least three candles required");
        return List.copyOf(rows);
    }

    public static FuturesHistoricalWalkForwardRunner.Input assemble(
            String provenance, List<FuturesReplayEngine.Candle> m5,
            List<FuturesReplayEngine.Candle> m15, List<FuturesReplayEngine.Candle> h1,
            List<FuturesReplayEngine.Candle> h4, int horizonBars,
            BigDecimal stopPercent, BigDecimal targetPercent, BigDecimal quantity,
            BigDecimal feePercentPerSide, BigDecimal slippagePercentPerSide) {
        if (!"HISTORICAL_REPLAY".equals(provenance) && !"SYNTHETIC_FIXTURE".equals(provenance))
            throw new IllegalArgumentException("Offline provenance required");
        check(m5,Duration.ofMinutes(5)); check(m15,Duration.ofMinutes(15));
        check(h1,Duration.ofHours(1)); check(h4,Duration.ofHours(4));
        validateCrossTimeframe(m5,m15,Duration.ofMinutes(15));
        validateCrossTimeframe(m5,h1,Duration.ofHours(1));
        validateCrossTimeframe(m5,h4,Duration.ofHours(4));
        return new FuturesHistoricalWalkForwardRunner.Input(provenance, List.copyOf(m5),
                List.copyOf(m15), List.copyOf(h1), List.copyOf(h4), horizonBars,
                stopPercent, targetPercent, quantity, feePercentPerSide, slippagePercentPerSide);
    }


    /**
     * Only compare higher-timeframe bars with complete 5m coverage.
     * Partial edge windows are not compared; downstream replay still enforces closed-bar rules.
     */
    static void validateCrossTimeframe(List<FuturesReplayEngine.Candle> m5,
                                      List<FuturesReplayEngine.Candle> higher, Duration frame) {
        int step = Math.toIntExact(frame.toMinutes() / 5);
        if (m5.size() < step) return;
        java.util.Map<Instant, FuturesReplayEngine.Candle> byTime = new java.util.HashMap<>();
        for (var candle : m5) byTime.put(candle.time(),candle);
        for (var parent : higher) {
            var first = byTime.get(parent.time());
            if (first == null) continue;
            var last = byTime.get(parent.time().plus(Duration.ofMinutes(5L*(step-1))));
            if (last == null) continue;
            BigDecimal high = first.high(), low = first.low();
            boolean complete = true;
            for (int j=1;j<step;j++) {
                var child = byTime.get(parent.time().plus(Duration.ofMinutes(5L*j)));
                if (child == null) { complete = false; break; }
                high = high.max(child.high());
                low = low.min(child.low());
            }
            if (!complete) continue;
            if (parent.open().compareTo(first.open()) != 0
                    || parent.close().compareTo(last.close()) != 0
                    || parent.high().compareTo(high) != 0
                    || parent.low().compareTo(low) != 0)
                throw new IllegalArgumentException("OHLC mismatch between 5m and "
                        + frame.toMinutes() + "m candle at " + parent.time());
        }
    }

    private static void check(List<FuturesReplayEngine.Candle> candles, Duration frame) {
        if (candles == null || candles.size() < 3 || candles.size() > MAX_ROWS)
            throw new IllegalArgumentException("Insufficient or excessive timeframe candles");
        Instant previous = null;
        for (var c : candles) {
            if (c == null || c.time() == null || c.open() == null || c.high() == null
                    || c.low() == null || c.close() == null
                    || c.time().getNano() != 0
                    || Math.floorMod(c.time().getEpochSecond(), frame.toSeconds()) != 0
                    || (previous != null && !c.time().equals(previous.plus(frame)))
                    || c.low().signum() <= 0 || c.high().compareTo(c.low()) < 0
                    || c.open().compareTo(c.low()) < 0 || c.open().compareTo(c.high()) > 0
                    || c.close().compareTo(c.low()) < 0 || c.close().compareTo(c.high()) > 0)
                throw new IllegalArgumentException("Invalid timeframe candle sequence");
            previous = c.time();
        }
    }
}
