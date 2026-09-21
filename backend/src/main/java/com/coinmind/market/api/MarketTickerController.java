package com.coinmind.market.api;

import com.coinmind.market.model.TickerSnapshot;
import com.coinmind.market.service.MarketTickerService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market/tickers")
public class MarketTickerController {

    private final MarketTickerService tickerService;

    public MarketTickerController(MarketTickerService tickerService) {
        this.tickerService = tickerService;
    }

    @GetMapping
    public List<TickerSnapshot> getLatestTickers() {
        return tickerService.getAllLatest();
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<TickerSnapshot> getLatestTicker(@PathVariable String symbol) {
        return tickerService.getLatest(symbol)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<TickerSnapshot> streamTickers() {
        return tickerService.stream();
    }
}
