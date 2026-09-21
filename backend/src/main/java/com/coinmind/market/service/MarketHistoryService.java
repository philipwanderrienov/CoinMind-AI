package com.coinmind.market.service;

import com.coinmind.market.model.Candlestick;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MarketHistoryService {

    private static final int MAX_CANDLES_PER_SERIES = 1500;

    private final Map<String, List<Candlestick>> candlesBySeries = new ConcurrentHashMap<>();

    public synchronized void replace(String symbol, String interval, List<Candlestick> candles) {
        String key = key(symbol, interval);

        List<Candlestick> normalized = candles.stream()
                .sorted(Comparator.comparing(Candlestick::openTime))
                .skip(Math.max(0, candles.size() - MAX_CANDLES_PER_SERIES))
                .toList();

        candlesBySeries.put(key, new ArrayList<>(normalized));
    }

    public synchronized void upsert(Candlestick candle) {
        String key = key(candle.symbol(), candle.interval());
        List<Candlestick> series = new ArrayList<>(
                candlesBySeries.getOrDefault(key, List.of())
        );

        series.removeIf(existing -> existing.openTime().equals(candle.openTime()));
        series.add(candle);
        series.sort(Comparator.comparing(Candlestick::openTime));

        if (series.size() > MAX_CANDLES_PER_SERIES) {
            series = new ArrayList<>(
                    series.subList(series.size() - MAX_CANDLES_PER_SERIES, series.size())
            );
        }

        candlesBySeries.put(key, series);
    }

    public synchronized List<Candlestick> get(
            String symbol,
            String interval,
            int limit
    ) {
        List<Candlestick> series = candlesBySeries.getOrDefault(
                key(symbol, interval),
                List.of()
        );

        int safeLimit = Math.max(1, Math.min(limit, MAX_CANDLES_PER_SERIES));
        int from = Math.max(0, series.size() - safeLimit);

        return List.copyOf(series.subList(from, series.size()));
    }

    public int seriesCount() {
        return candlesBySeries.size();
    }

    private String key(String symbol, String interval) {
        return symbol.toUpperCase() + ":" + interval.toLowerCase();
    }
}
