package com.coinmind.market.service;

import com.coinmind.market.model.Candlestick;
import com.coinmind.market.model.MarketActivityProfile;
import com.coinmind.market.persistence.CandlestickRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MarketActivityProfileService {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final int HISTORY_LIMIT = 720;

    private final MarketHistoryService historyService;
    private final ObjectProvider<CandlestickRepository> repositoryProvider;

    public MarketActivityProfileService(
            MarketHistoryService historyService,
            ObjectProvider<CandlestickRepository> repositoryProvider
    ) {
        this.historyService = historyService;
        this.repositoryProvider = repositoryProvider;
    }

    public Mono<MarketActivityProfile> build(String symbol) {
        String normalized = symbol.toUpperCase();
        CandlestickRepository repository = repositoryProvider.getIfAvailable();

        if (repository == null) {
            return Mono.just(buildProfile(
                    normalized,
                    historyService.get(normalized, "1h", HISTORY_LIMIT)
            ));
        }

        return repository.findRecent(normalized, "1h", HISTORY_LIMIT)
                .collectList()
                .map(candles -> candles.isEmpty()
                        ? historyService.get(normalized, "1h", HISTORY_LIMIT)
                        : candles)
                .map(candles -> buildProfile(normalized, candles));
    }

    private MarketActivityProfile buildProfile(
            String symbol,
            List<Candlestick> source
    ) {
        List<Candlestick> candles = source.stream()
                .filter(Candlestick::closed)
                .filter(candle -> candle.open() != null
                        && candle.open().signum() > 0
                        && candle.quoteVolume() != null)
                .toList();

        Map<Integer, Accumulator> byHour = new HashMap<>();
        Set<LocalDate> dates = new HashSet<>();

        for (Candlestick candle : candles) {
            ZonedDateTime local = candle.openTime().atZone(JAKARTA);
            int hour = local.getHour();
            dates.add(local.toLocalDate());

            BigDecimal volatilityPct = candle.high()
                    .subtract(candle.low(), MC)
                    .abs()
                    .divide(candle.open(), MC)
                    .multiply(BigDecimal.valueOf(100), MC);

            byHour.computeIfAbsent(hour, ignored -> new Accumulator())
                    .add(
                            candle.quoteVolume(),
                            BigDecimal.valueOf(candle.tradeCount()),
                            volatilityPct
                    );
        }

        BigDecimal maxVolume = BigDecimal.ZERO;
        BigDecimal maxTrades = BigDecimal.ZERO;
        BigDecimal maxVolatility = BigDecimal.ZERO;

        for (Accumulator accumulator : byHour.values()) {
            maxVolume = maxVolume.max(accumulator.averageVolume());
            maxTrades = maxTrades.max(accumulator.averageTrades());
            maxVolatility = maxVolatility.max(accumulator.averageVolatility());
        }

        List<MarketActivityProfile.HourlyActivity> hours = new ArrayList<>();

        for (int hour = 0; hour < 24; hour++) {
            Accumulator accumulator = byHour.get(hour);

            if (accumulator == null || accumulator.samples == 0) {
                hours.add(new MarketActivityProfile.HourlyActivity(
                        hour,
                        BigDecimal.ZERO,
                        "LOW",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        0
                ));
                continue;
            }

            BigDecimal volumeScore = normalize(
                    accumulator.averageVolume(),
                    maxVolume
            );
            BigDecimal tradeScore = normalize(
                    accumulator.averageTrades(),
                    maxTrades
            );
            BigDecimal volatilityScore = normalize(
                    accumulator.averageVolatility(),
                    maxVolatility
            );

            BigDecimal activityScore = volumeScore
                    .multiply(BigDecimal.valueOf(0.50), MC)
                    .add(tradeScore.multiply(BigDecimal.valueOf(0.25), MC), MC)
                    .add(volatilityScore.multiply(BigDecimal.valueOf(0.25), MC), MC);

            hours.add(new MarketActivityProfile.HourlyActivity(
                    hour,
                    scale(activityScore),
                    level(activityScore),
                    scale(accumulator.averageVolume()),
                    scale(accumulator.averageTrades()),
                    scale(accumulator.averageVolatility()),
                    accumulator.samples
            ));
        }

        int currentHour = ZonedDateTime.now(JAKARTA).getHour();
        MarketActivityProfile.HourlyActivity current = hours.get(currentHour);

        List<Integer> peakHours = hours.stream()
                .filter(item -> item.samples() > 0)
                .sorted(Comparator
                        .comparing(MarketActivityProfile.HourlyActivity::activityScore)
                        .reversed())
                .limit(4)
                .map(MarketActivityProfile.HourlyActivity::hour)
                .sorted()
                .toList();

        return new MarketActivityProfile(
                symbol,
                JAKARTA.getId(),
                dates.size(),
                currentHour,
                current.activityLevel(),
                current.activityScore(),
                peakHours,
                List.copyOf(hours),
                Instant.now()
        );
    }

    private BigDecimal normalize(BigDecimal value, BigDecimal max) {
        if (value == null || max == null || max.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        return value.divide(max, MC)
                .multiply(BigDecimal.valueOf(100), MC)
                .min(BigDecimal.valueOf(100))
                .max(BigDecimal.ZERO);
    }

    private String level(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return "VERY_HIGH";
        }
        if (score.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "HIGH";
        }
        if (score.compareTo(BigDecimal.valueOf(35)) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static final class Accumulator {
        private BigDecimal volume = BigDecimal.ZERO;
        private BigDecimal trades = BigDecimal.ZERO;
        private BigDecimal volatility = BigDecimal.ZERO;
        private int samples = 0;

        private void add(
                BigDecimal quoteVolume,
                BigDecimal tradeCount,
                BigDecimal volatilityPct
        ) {
            volume = volume.add(quoteVolume, MC);
            trades = trades.add(tradeCount, MC);
            volatility = volatility.add(volatilityPct, MC);
            samples++;
        }

        private BigDecimal averageVolume() {
            return average(volume);
        }

        private BigDecimal averageTrades() {
            return average(trades);
        }

        private BigDecimal averageVolatility() {
            return average(volatility);
        }

        private BigDecimal average(BigDecimal value) {
            if (samples == 0) {
                return BigDecimal.ZERO;
            }
            return value.divide(BigDecimal.valueOf(samples), MC);
        }
    }
}
