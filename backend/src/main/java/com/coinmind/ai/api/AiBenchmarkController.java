package com.coinmind.ai.api;

import com.coinmind.ai.config.AiProperties;
import com.coinmind.ai.model.AiPolymarketBenchmarkResult;
import com.coinmind.ai.service.AiPolymarketBenchmarkService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/ai/benchmark")
public class AiBenchmarkController {

    private final AiPolymarketBenchmarkService benchmarkService;
    private final AiProperties properties;

    public AiBenchmarkController(
            AiPolymarketBenchmarkService benchmarkService,
            AiProperties properties
    ) {
        this.benchmarkService = benchmarkService;
        this.properties = properties;
    }

    @GetMapping("/config")
    public BenchmarkConfig config() {
        return new BenchmarkConfig(
                properties.enabled(),
                properties.provider(),
                properties.effectiveRoutineModel(),
                properties.effectiveDeepModel()
        );
    }

    @PostMapping("/forecast/{symbol}")
    public Mono<AiPolymarketBenchmarkResult> benchmarkForecast(
            @PathVariable String symbol
    ) {
        return benchmarkService.benchmark(symbol);
    }

    public record BenchmarkConfig(
            boolean aiEnabled,
            String provider,
            String routineModel,
            String deepModel
    ) {
    }
}
