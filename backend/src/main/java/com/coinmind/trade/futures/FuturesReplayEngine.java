package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, offline, chronological futures candle replay. NOT a live trading signal.
 * Only explicit synthetic/replay inputs are accepted. Stop-first on ambiguous candles.
 */
public final class FuturesReplayEngine {
    private static final MathContext MC = MathContext.DECIMAL64;
    private FuturesReplayEngine() {}

    public static Result run(Request r) {
        if (r == null || r.candles() == null || r.candles().isEmpty()) throw new IllegalArgumentException("candles required");
        if (!"SYNTHETIC_FIXTURE".equals(r.provenance()) && !"HISTORICAL_REPLAY".equals(r.provenance()))
            throw new IllegalArgumentException("Explicit replay provenance required");
        if (!"LONG".equals(r.side()) && !"SHORT".equals(r.side())) throw new IllegalArgumentException("side must be LONG or SHORT");
        positive(r.entry(), "entry"); positive(r.stop(), "stop"); positive(r.target(), "target");
        positive(r.quantity(), "quantity");
        nonnegative(r.feePercentPerSide(), "fee"); nonnegative(r.slippagePercentPerSide(), "slippage");
        boolean isLong = r.side().equals("LONG");
        if (isLong ? !(r.stop().compareTo(r.entry()) < 0 && r.target().compareTo(r.entry()) > 0)
                   : !(r.stop().compareTo(r.entry()) > 0 && r.target().compareTo(r.entry()) < 0))
            throw new IllegalArgumentException("stop/target direction invalid");

        // Validate the entire dataset first, including candles after a potential early exit.
        Instant validatedPrevious = null;
        for (Candle candle : r.candles()) {
            if (candle == null || candle.time() == null
                    || (validatedPrevious != null && !candle.time().isAfter(validatedPrevious)))
                throw new IllegalArgumentException("Candles must have unique ascending timestamps");
            validatedPrevious = candle.time();
            positive(candle.open(), "open"); positive(candle.high(), "high");
            positive(candle.low(), "low"); positive(candle.close(), "close");
            if (candle.low().compareTo(candle.high()) > 0
                    || candle.open().compareTo(candle.low()) < 0 || candle.open().compareTo(candle.high()) > 0
                    || candle.close().compareTo(candle.low()) < 0 || candle.close().compareTo(candle.high()) > 0)
                throw new IllegalArgumentException("Invalid OHLC candle");
        }

        Instant previous = null;
        boolean entered = false;
        BigDecimal fill = null;
        String exitReason = "NO_ENTRY";
        BigDecimal exit = null;
        Instant exitAt = null;
        for (Candle candle : r.candles()) {
            if (candle.time() == null || (previous != null && !candle.time().isAfter(previous)))
                throw new IllegalArgumentException("Candles must have unique ascending timestamps");
            previous = candle.time();
            positive(candle.open(), "open"); positive(candle.high(), "high");
            positive(candle.low(), "low"); positive(candle.close(), "close");
            if (candle.low().compareTo(candle.high()) > 0
                    || candle.open().compareTo(candle.low()) < 0 || candle.open().compareTo(candle.high()) > 0
                    || candle.close().compareTo(candle.low()) < 0 || candle.close().compareTo(candle.high()) > 0)
                throw new IllegalArgumentException("Invalid OHLC candle");
            boolean enteredThisCandle = false;
            if (!entered) {
                if (candle.low().compareTo(r.entry()) > 0 || candle.high().compareTo(r.entry()) < 0) continue;
                enteredThisCandle = true;
                entered = true;
                // Conservative gap treatment: if open is already worse than intended entry, fill at open.
                BigDecimal raw = isLong ? r.entry().max(candle.open()) : r.entry().min(candle.open());
                fill = slip(raw, r.slippagePercentPerSide(), isLong);
            }
            boolean hitStop = isLong ? candle.low().compareTo(r.stop()) <= 0 : candle.high().compareTo(r.stop()) >= 0;
            // A target touched on the entry candle may have occurred before the entry fill.
            // Never award this ambiguous profit without lower-timeframe execution evidence.
            boolean hitTarget = !enteredThisCandle && (isLong
                    ? candle.high().compareTo(r.target()) >= 0
                    : candle.low().compareTo(r.target()) <= 0);
            if (hitStop || hitTarget) {
                exitReason = hitStop ? "STOP" : "TARGET";
                BigDecimal raw = hitStop ? (isLong ? r.stop().min(candle.open()) : r.stop().max(candle.open())) : r.target();
                exit = slip(raw, r.slippagePercentPerSide(), !isLong);
                exitAt = candle.time();
                break;
            }
        }
        if (!entered) return new Result(r.provenance(), "NO_ENTRY", null, null, null, null, null, "No fill within replay candles");
        if (exit == null) {
            Candle last = r.candles().get(r.candles().size() - 1);
            exit = slip(last.close(), r.slippagePercentPerSide(), !isLong);
            exitAt = last.time();
            exitReason = "TIME_EXIT";
        }
        BigDecimal gross = (isLong ? exit.subtract(fill) : fill.subtract(exit)).multiply(r.quantity(), MC);
        BigDecimal fees = fill.add(exit).multiply(r.quantity(), MC)
                .multiply(r.feePercentPerSide(), MC).divide(new BigDecimal("100"), MC);
        BigDecimal net = gross.subtract(fees, MC);
        return new Result(r.provenance(), exitReason, fill, exit, gross, fees, net,
                "Candle-based replay only; STOP wins ambiguous bars, and target on entry bar is ignored. Funding, liquidation and venue constraints excluded.");
    }

    private static BigDecimal slip(BigDecimal price, BigDecimal percent, boolean buy) {
        BigDecimal change = price.multiply(percent, MC).divide(new BigDecimal("100"), MC);
        return buy ? price.add(change) : price.subtract(change);
    }
    private static void positive(BigDecimal n, String field) {
        if (n == null || n.signum() <= 0) throw new IllegalArgumentException(field + " must be positive");
    }
    private static void nonnegative(BigDecimal n, String field) {
        if (n == null || n.signum() < 0) throw new IllegalArgumentException(field + " must be nonnegative");
    }
    public record Candle(Instant time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {}
    public record Request(String provenance, String side, BigDecimal entry, BigDecimal stop,
                          BigDecimal target, BigDecimal quantity, BigDecimal feePercentPerSide,
                          BigDecimal slippagePercentPerSide, List<Candle> candles) {}
    public record Result(String provenance, String exitReason, BigDecimal entryFill, BigDecimal exitFill,
                         BigDecimal grossPnl, BigDecimal tradingFees, BigDecimal netPnl, String limitations) {}
}
