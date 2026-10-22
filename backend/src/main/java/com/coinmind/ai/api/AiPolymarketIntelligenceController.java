package com.coinmind.ai.api;

import com.coinmind.ai.model.AiPolymarketIntelligence;
import com.coinmind.ai.service.AiPolymarketIntelligenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/ai/polymarket-intelligence")
public class AiPolymarketIntelligenceController {

    private final AiPolymarketIntelligenceService service;

    public AiPolymarketIntelligenceController(AiPolymarketIntelligenceService service) {
        this.service = service;
    }

    @GetMapping("/{symbol}")
    public AiPolymarketIntelligence latest(@PathVariable String symbol) {
        AiPolymarketIntelligence cached = service.latestFresh(symbol);
        return cached == null
                ? service.deterministicFallback(symbol)
                : cached;
    }

    @PostMapping("/{symbol}")
    public Mono<AiPolymarketIntelligence> analyze(@PathVariable String symbol) {
        return service.analyze(symbol, true);
    }
}
