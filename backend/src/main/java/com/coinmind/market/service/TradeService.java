package com.coinmind.market.service;

import com.coinmind.market.model.MarketMicrostructure;
import com.coinmind.market.model.TradeSnapshot;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TradeService {

    private static final int MAX_TRADES_PER_SYMBOL = 500;
    private static final MathContext MC = MathContext.DECIMAL64;

    private final Map<String, Deque<TradeSnapshot>> recentTrades = new ConcurrentHashMap<>();
    private final Sinks.Many<TradeSnapshot> updates =
            Sinks.many().multicast().onBackpressureBuffer();

    public synchronized void publish(TradeSnapshot trade) {
        Deque<TradeSnapshot> trades = recentTrades.computeIfAbsent(
                trade.symbol(),
                ignored -> new ArrayDeque<>()
        );

        trades.addLast(trade);
        while (trades.size() > MAX_TRADES_PER_SYMBOL) {
            trades.removeFirst();
        }

        updates.tryEmitNext(trade);
    }

    public synchronized Optional<TradeSnapshot> getLatest(String symbol) {
        Deque<TradeSnapshot> trades = recentTrades.get(symbol.toUpperCase());
        return trades == null || trades.isEmpty()
                ? Optional.empty()
                : Optional.ofNullable(trades.peekLast());
    }

    public synchronized MarketMicrostructure summarize(
            String symbol,
            BigDecimal spread,
            BigDecimal midPrice
    ) {
        String normalized = symbol.toUpperCase();
        Deque<TradeSnapshot> trades = recentTrades.getOrDefault(normalized, new ArrayDeque<>());

        BigDecimal buyVolume = BigDecimal.ZERO;
        BigDecimal sellVolume = BigDecimal.ZERO;

        for (TradeSnapshot trade : trades) {
            if ("BUY".equals(trade.aggressorSide())) {
                buyVolume = buyVolume.add(trade.quoteQuantity());
            } else {
                sellVolume = sellVolume.add(trade.quoteQuantity());
            }
        }

        BigDecimal ratio = sellVolume.signum() == 0
                ? (buyVolume.signum() == 0 ? BigDecimal.ONE : buyVolume)
                : buyVolume.divide(sellVolume, MC);

        BigDecimal spreadBps = midPrice != null && midPrice.signum() != 0
                ? spread.divide(midPrice, MC).multiply(BigDecimal.valueOf(10_000))
                : BigDecimal.ZERO;

        return new MarketMicrostructure(
                normalized,
                spread,
                spreadBps,
                midPrice,
                buyVolume,
                sellVolume,
                ratio,
                Instant.now()
        );
    }

    public Flux<TradeSnapshot> stream() {
        return updates.asFlux();
    }
}
