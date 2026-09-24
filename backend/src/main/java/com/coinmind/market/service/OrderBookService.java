package com.coinmind.market.service;

import com.coinmind.market.model.OrderBookSnapshot;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderBookService {

    private final Map<String, OrderBookSnapshot> latestBySymbol = new ConcurrentHashMap<>();
    private final Sinks.Many<OrderBookSnapshot> updates =
            Sinks.many().multicast().onBackpressureBuffer(256, false);

    public void publish(OrderBookSnapshot snapshot) {
        latestBySymbol.put(snapshot.symbol(), snapshot);
        updates.tryEmitNext(snapshot);
    }

    public Optional<OrderBookSnapshot> getLatest(String symbol) {
        return Optional.ofNullable(latestBySymbol.get(symbol.toUpperCase()));
    }

    public List<OrderBookSnapshot> getAllLatest() {
        return latestBySymbol.values().stream()
                .sorted(Comparator.comparing(OrderBookSnapshot::symbol))
                .toList();
    }

    public Flux<OrderBookSnapshot> stream() {
        return updates.asFlux();
    }
}
