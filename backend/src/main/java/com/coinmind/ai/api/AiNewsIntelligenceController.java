package com.coinmind.ai.api;

import com.coinmind.ai.model.AiNewsIntelligence;
import com.coinmind.ai.service.AiNewsIntelligenceService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/ai/news-intelligence")
public class AiNewsIntelligenceController {

    private final AiNewsIntelligenceService service;

    public AiNewsIntelligenceController(AiNewsIntelligenceService service) {
        this.service = service;
    }

    @PostMapping("/{symbol}")
    public Mono<AiNewsIntelligence> analyze(@PathVariable String symbol) {
        return service.analyze(symbol);
    }
}
