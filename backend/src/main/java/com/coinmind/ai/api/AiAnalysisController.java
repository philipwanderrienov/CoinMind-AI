package com.coinmind.ai.api;

import com.coinmind.ai.model.AiAnalysisResult;
import com.coinmind.ai.model.MarketContext;
import com.coinmind.ai.service.AiAnalysisService;
import com.coinmind.ai.service.MarketContextBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/ai")
public class AiAnalysisController {

    private final MarketContextBuilder contextBuilder;
    private final AiAnalysisService aiAnalysisService;

    public AiAnalysisController(
            MarketContextBuilder contextBuilder,
            AiAnalysisService aiAnalysisService
    ) {
        this.contextBuilder = contextBuilder;
        this.aiAnalysisService = aiAnalysisService;
    }

    @GetMapping("/context/{symbol}/{interval}")
    public ResponseEntity<MarketContext> getContext(
            @PathVariable String symbol,
            @PathVariable String interval
    ) {
        return ResponseEntity.ok(contextBuilder.build(symbol, interval));
    }

    @GetMapping("/analysis/{symbol}/{interval}")
    public Mono<AiAnalysisResult> analyze(
            @PathVariable String symbol,
            @PathVariable String interval
    ) {
        return aiAnalysisService.analyze(symbol, interval);
    }
}
