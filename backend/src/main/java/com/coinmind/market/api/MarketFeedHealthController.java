package com.coinmind.market.api;

import com.coinmind.market.service.MarketCandlestickService;
import com.coinmind.market.service.MarketTickerService;
import com.coinmind.market.service.OrderBookService;
import com.coinmind.market.service.TradeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/market/feed-health")
public class MarketFeedHealthController {

    private static final Duration STALE_AFTER = Duration.ofSeconds(30);

    private final MarketTickerService tickerService;
    private final MarketCandlestickService candlestickService;
    private final OrderBookService orderBookService;
    private final TradeService tradeService;

    public MarketFeedHealthController(
            MarketTickerService tickerService,
            MarketCandlestickService candlestickService,
            OrderBookService orderBookService,
            TradeService tradeService
    ) {
        this.tickerService = tickerService;
        this.candlestickService = candlestickService;
        this.orderBookService = orderBookService;
        this.tradeService = tradeService;
    }

    @GetMapping
    public FeedHealthResponse health() {
        Instant now = Instant.now();

        Map<String, StreamHealth> streams = new LinkedHashMap<>();
        streams.put("ticker", streamHealth(tickerService.lastEventAt(), now));
        streams.put("kline", streamHealth(candlestickService.lastEventAt(), now));
        streams.put("orderBook", streamHealth(orderBookService.lastEventAt(), now));
        streams.put("trade", streamHealth(tradeService.lastEventAt(), now));

        boolean anyStarting = streams.values().stream()
                .anyMatch(stream -> "STARTING".equals(stream.status()));
        boolean anyStale = streams.values().stream()
                .anyMatch(stream -> "STALE".equals(stream.status()));

        String status = anyStale
                ? "DEGRADED"
                : anyStarting
                    ? "STARTING"
                    : "UP";

        return new FeedHealthResponse(status, now, streams);
    }

    private StreamHealth streamHealth(Instant lastEventAt, Instant now) {
        if (lastEventAt == null) {
            return new StreamHealth("STARTING", null, null);
        }

        long ageSeconds = Math.max(0, Duration.between(lastEventAt, now).toSeconds());
        String status = ageSeconds > STALE_AFTER.toSeconds() ? "STALE" : "UP";

        return new StreamHealth(status, lastEventAt, ageSeconds);
    }

    public record FeedHealthResponse(
            String status,
            Instant checkedAt,
            Map<String, StreamHealth> streams
    ) {
    }

    public record StreamHealth(
            String status,
            Instant lastEventAt,
            Long ageSeconds
    ) {
    }
}
