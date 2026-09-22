package com.coinmind.market.service;

import com.coinmind.market.model.Candlestick;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MarketCandlestickService {

    private final Map<String, Candlestick> latestBySymbolAndInterval = new ConcurrentHashMap<>();
    private final Sinks.Many<Candlestick> updates =
            Sinks.many().multicast().onBackpressureBuffer();

    public void publish(Candlestick candlestick) {
        latestBySymbolAndInterval.put(candlestick.key(), candlestick);
        updates.tryEmitNext(candlestick);
    }

    public Optional<Candlestick> getLatest(String symbol, String interval) {
        return Optional.ofNullable(
                latestBySymbolAndInterval.get(symbol.toUpperCase() + ":" + interval.toLowerCase())
        );
    }

    public List<Candlestick> getLatestBySymbol(String symbol) {
        String normalized = symbol.toUpperCase();
        return latestBySymbolAndInterval.values().stream()
                .filter(candle -> candle.symbol().equals(normalized))
                .sorted(Comparator.comparing(Candlestick::interval))
                .toList();
    }

    public List<Candlestick> getAllLatest() {
        return latestBySymbolAndInterval.values().stream()
                .sorted(Comparator.comparing(Candlestick::symbol)
                        .thenComparing(Candlestick::interval))
                .toList();
    }

    public Flux<Candlestick> stream() {
        return updates.asFlux();
    }
}
