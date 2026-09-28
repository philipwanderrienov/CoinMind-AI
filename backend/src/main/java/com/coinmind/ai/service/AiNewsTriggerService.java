package com.coinmind.ai.service;

import com.coinmind.ai.event.HighRelevanceNewsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(
        prefix = "coinmind.ai",
        name = "enabled",
        havingValue = "true"
)
public class AiNewsTriggerService {

    private static final Logger log = LoggerFactory.getLogger(AiNewsTriggerService.class);
    private static final Duration COOLDOWN = Duration.ofMinutes(15);

    private final AiAnalysisService analysisService;
    private final AiExecutionPolicyService executionPolicy;
    private final Map<String, Instant> lastTriggerBySymbol = new ConcurrentHashMap<>();

    public AiNewsTriggerService(
            AiAnalysisService analysisService,
            AiExecutionPolicyService executionPolicy
    ) {
        this.analysisService = analysisService;
        this.executionPolicy = executionPolicy;
    }

    @EventListener
    public void onHighRelevanceNews(HighRelevanceNewsEvent event) {
        if (!executionPolicy.providerReadyForAutomaticCalls()) {
            return;
        }

        var article = event.article();

        for (String baseSymbol : article.symbols()) {
            String symbol = baseSymbol + "USDT";

            if (!shouldTrigger(symbol) || !executionPolicy.allowHighRelevanceNews()) {
                continue;
            }

            analysisService.analyze(symbol, "1h", "HIGH_RELEVANCE_NEWS")
                    .doOnNext(result -> log.info(
                            "AI news analysis completed. symbol={}, bias={}, confidence={}, relevance={}",
                            symbol,
                            result.marketBias(),
                            result.confidence(),
                            article.relevanceScore()
                    ))
                    .onErrorResume(error -> {
                        log.warn(
                                "AI high-relevance-news analysis failed. symbol={}, article={}",
                                symbol,
                                article.id(),
                                error
                        );
                        return reactor.core.publisher.Mono.empty();
                    })
                    .subscribe();
        }
    }

    private boolean shouldTrigger(String symbol) {
        Instant now = Instant.now();
        Instant previous = lastTriggerBySymbol.get(symbol);

        if (previous != null && previous.plus(COOLDOWN).isAfter(now)) {
            return false;
        }

        lastTriggerBySymbol.put(symbol, now);
        return true;
    }
}
