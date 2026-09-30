package com.coinmind.polymarket.api;

import com.coinmind.polymarket.model.PolymarketIntelligence;
import com.coinmind.polymarket.service.PolymarketIntelligenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/polymarket")
public class PolymarketController {

    private final PolymarketIntelligenceService service;

    public PolymarketController(PolymarketIntelligenceService service) {
        this.service = service;
    }

    @GetMapping("/{symbol}")
    public Mono<PolymarketIntelligence> latest(@PathVariable String symbol) {
        PolymarketIntelligence cached = service.latestFresh(symbol);
        return cached == null
                ? service.refresh(symbol)
                : Mono.just(cached);
    }

    @PostMapping("/{symbol}/refresh")
    public Mono<PolymarketIntelligence> refresh(@PathVariable String symbol) {
        return service.refresh(symbol);
    }
}
