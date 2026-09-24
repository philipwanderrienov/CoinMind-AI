package com.coinmind.market.service;

import com.coinmind.market.model.TickerSnapshot;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MarketTickerService {

    private final Map<String, TickerSnapshot> latestBySymbol = new ConcurrentHashMap<>();
    private final Sinks.Many<TickerSnapshot> updates =
            Sinks.many().multicast().onBackpressureBuffer(256, false);

    public void publish(TickerSnapshot snapshot) {
        latestBySymbol.put(snapshot.symbol(), snapshot);
        updates.tryEmitNext(snapshot);
    }

    public Optional<TickerSnapshot> getLatest(String symbol) {
        return Optional.ofNullable(latestBySymbol.get(symbol.toUpperCase()));
    }

    public List<TickerSnapshot> getAllLatest() {
        return latestBySymbol.values().stream()
                .sorted(Comparator.comparing(TickerSnapshot::symbol))
                .toList();
    }

    public Flux<TickerSnapshot> stream() {
        return updates.asFlux();
    }
}
