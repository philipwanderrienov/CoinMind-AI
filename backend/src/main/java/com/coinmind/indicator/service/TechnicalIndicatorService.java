package com.coinmind.indicator.service;

import com.coinmind.indicator.model.TechnicalIndicators;
import com.coinmind.market.model.Candlestick;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class TechnicalIndicatorService {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);

    public TechnicalIndicators calculate(
            String symbol,
            String interval,
            List<Candlestick> candles
    ) {
        if (candles == null || candles.size() < 30) {
            throw new IllegalArgumentException("At least 30 candles are required");
        }

        List<BigDecimal> closes = candles.stream().map(Candlestick::close).toList();
        List<BigDecimal> volumes = candles.stream().map(Candlestick::quoteVolume).toList();

        BigDecimal ema20 = ema(closes, 20);
        BigDecimal ema50 = ema(closes, 50);
        BigDecimal ema200 = ema(closes, Math.min(200, closes.size()));

        BigDecimal rsi14 = rsi(closes, 14);

        List<BigDecimal> ema12Series = emaSeries(closes, 12);
        List<BigDecimal> ema26Series = emaSeries(closes, 26);
        List<BigDecimal> macdSeries = new ArrayList<>();

        int offset = ema12Series.size() - ema26Series.size();
        for (int i = 0; i < ema26Series.size(); i++) {
            macdSeries.add(ema12Series.get(i + offset).subtract(ema26Series.get(i), MC));
        }

        BigDecimal macd = macdSeries.get(macdSeries.size() - 1);
        BigDecimal macdSignal = ema(macdSeries, Math.min(9, macdSeries.size()));
        BigDecimal macdHistogram = macd.subtract(macdSignal, MC);

        BigDecimal atr14 = atr(candles, 14);

        Bollinger bollinger = bollinger(closes, 20, BigDecimal.valueOf(2));
        BigDecimal volumeRatio = volumeRatio(volumes, 20);

        BigDecimal trendScore = trendScore(closes.get(closes.size() - 1), ema20, ema50, ema200);
        BigDecimal momentumScore = momentumScore(rsi14, macdHistogram);
        BigDecimal volatilityScore = volatilityScore(atr14, closes.get(closes.size() - 1));

        return new TechnicalIndicators(
                symbol.toUpperCase(),
                interval.toLowerCase(),
                closes.get(closes.size() - 1),
                scale(ema20),
                scale(ema50),
                scale(ema200),
                scale(rsi14),
                scale(macd),
                scale(macdSignal),
                scale(macdHistogram),
                scale(atr14),
                scale(bollinger.middle()),
                scale(bollinger.upper()),
                scale(bollinger.lower()),
                scale(volumeRatio),
                scale(trendScore),
                scale(momentumScore),
                scale(volatilityScore),
                Instant.now()
        );
    }

    private BigDecimal ema(List<BigDecimal> values, int period) {
        return emaSeries(values, period).get(emaSeries(values, period).size() - 1);
    }

    private List<BigDecimal> emaSeries(List<BigDecimal> values, int period) {
        if (values.isEmpty()) {
            return List.of(BigDecimal.ZERO);
        }

        int safePeriod = Math.max(1, Math.min(period, values.size()));
        BigDecimal multiplier = BigDecimal.valueOf(2)
                .divide(BigDecimal.valueOf(safePeriod + 1L), MC);

        BigDecimal current = average(values.subList(0, safePeriod));
        List<BigDecimal> result = new ArrayList<>();
        result.add(current);

        for (int i = safePeriod; i < values.size(); i++) {
            current = values.get(i)
                    .subtract(current, MC)
                    .multiply(multiplier, MC)
                    .add(current, MC);
            result.add(current);
        }

        return result;
    }

    private BigDecimal rsi(List<BigDecimal> closes, int period) {
        int start = Math.max(1, closes.size() - period);
        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;

        for (int i = start; i < closes.size(); i++) {
            BigDecimal delta = closes.get(i).subtract(closes.get(i - 1), MC);
            if (delta.signum() > 0) {
                gains = gains.add(delta, MC);
            } else {
                losses = losses.add(delta.abs(), MC);
            }
        }

        BigDecimal divisor = BigDecimal.valueOf(closes.size() - start);
        BigDecimal avgGain = gains.divide(divisor, MC);
        BigDecimal avgLoss = losses.divide(divisor, MC);

        if (avgLoss.signum() == 0) {
            return BigDecimal.valueOf(100);
        }

        BigDecimal rs = avgGain.divide(avgLoss, MC);
        return BigDecimal.valueOf(100)
                .subtract(
                        BigDecimal.valueOf(100)
                                .divide(BigDecimal.ONE.add(rs, MC), MC),
                        MC
                );
    }

    private BigDecimal atr(List<Candlestick> candles, int period) {
        int start = Math.max(1, candles.size() - period);
        BigDecimal total = BigDecimal.ZERO;

        for (int i = start; i < candles.size(); i++) {
            Candlestick current = candles.get(i);
            BigDecimal previousClose = candles.get(i - 1).close();

            BigDecimal highLow = current.high().subtract(current.low(), MC).abs();
            BigDecimal highPrevClose = current.high().subtract(previousClose, MC).abs();
            BigDecimal lowPrevClose = current.low().subtract(previousClose, MC).abs();

            BigDecimal trueRange = highLow.max(highPrevClose).max(lowPrevClose);
            total = total.add(trueRange, MC);
        }

        return total.divide(BigDecimal.valueOf(candles.size() - start), MC);
    }

    private Bollinger bollinger(
            List<BigDecimal> closes,
            int period,
            BigDecimal stdMultiplier
    ) {
        int start = Math.max(0, closes.size() - period);
        List<BigDecimal> window = closes.subList(start, closes.size());
        BigDecimal mean = average(window);

        BigDecimal variance = window.stream()
                .map(value -> value.subtract(mean, MC).pow(2, MC))
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC))
                .divide(BigDecimal.valueOf(window.size()), MC);

        BigDecimal std = sqrt(variance);
        BigDecimal offset = std.multiply(stdMultiplier, MC);

        return new Bollinger(
                mean,
                mean.add(offset, MC),
                mean.subtract(offset, MC)
        );
    }

    private BigDecimal volumeRatio(List<BigDecimal> volumes, int period) {
        if (volumes.size() < 2) {
            return BigDecimal.ONE;
        }

        BigDecimal latest = volumes.get(volumes.size() - 1);
        int start = Math.max(0, volumes.size() - period - 1);
        int end = volumes.size() - 1;
        List<BigDecimal> previous = volumes.subList(start, end);

        BigDecimal average = previous.isEmpty()
                ? BigDecimal.ONE
                : average(previous);

        return average.signum() == 0
                ? BigDecimal.ONE
                : latest.divide(average, MC);
    }

    private BigDecimal trendScore(
            BigDecimal price,
            BigDecimal ema20,
            BigDecimal ema50,
            BigDecimal ema200
    ) {
        int score = 0;
        if (price.compareTo(ema20) > 0) score += 25; else score -= 25;
        if (ema20.compareTo(ema50) > 0) score += 25; else score -= 25;
        if (ema50.compareTo(ema200) > 0) score += 50; else score -= 50;
        return BigDecimal.valueOf(score);
    }

    private BigDecimal momentumScore(
            BigDecimal rsi,
            BigDecimal macdHistogram
    ) {
        BigDecimal rsiComponent = rsi.subtract(BigDecimal.valueOf(50), MC)
                .multiply(BigDecimal.valueOf(2), MC);

        BigDecimal macdComponent = BigDecimal.valueOf(macdHistogram.signum() * 25L);

        return clamp(rsiComponent.add(macdComponent, MC));
    }

    private BigDecimal volatilityScore(
            BigDecimal atr,
            BigDecimal price
    ) {
        if (price.signum() == 0) {
            return BigDecimal.ZERO;
        }

        return clamp(
                atr.divide(price, MC)
                        .multiply(BigDecimal.valueOf(10_000), MC)
                        .divide(BigDecimal.valueOf(5), MC)
        );
    }

    private BigDecimal average(List<BigDecimal> values) {
        return values.stream()
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC))
                .divide(BigDecimal.valueOf(values.size()), MC);
    }

    private BigDecimal sqrt(BigDecimal value) {
        return value.sqrt(MC);
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BigDecimal.valueOf(100);
        }
        if (value.compareTo(BigDecimal.valueOf(-100)) < 0) {
            return BigDecimal.valueOf(-100);
        }
        return value;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private record Bollinger(
            BigDecimal middle,
            BigDecimal upper,
            BigDecimal lower
    ) {
    }
}
