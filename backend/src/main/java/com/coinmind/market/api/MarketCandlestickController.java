package com.coinmind.market.api;

import com.coinmind.market.model.Candlestick;
import com.coinmind.market.service.MarketCandlestickService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market/candles")
public class MarketCandlestickController {

    private final MarketCandlestickService candlestickService;

    public MarketCandlestickController(MarketCandlestickService candlestickService) {
        this.candlestickService = candlestickService;
    }

    @GetMapping
    public List<Candlestick> getAllLatest() {
        return candlestickService.getAllLatest();
    }

    @GetMapping("/{symbol}")
    public List<Candlestick> getLatestBySymbol(@PathVariable String symbol) {
        return candlestickService.getLatestBySymbol(symbol);
    }

    @GetMapping("/{symbol}/{interval}")
    public ResponseEntity<Candlestick> getLatest(
            @PathVariable String symbol,
            @PathVariable String interval
    ) {
        return candlestickService.getLatest(symbol, interval)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Candlestick> stream() {
        return candlestickService.stream();
    }
}
