package com.coinmind.ai.service;

import com.coinmind.ai.config.AiProperties;
import com.coinmind.market.model.Candlestick;
import com.coinmind.market.service.MarketCandlestickService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(
        prefix = "coinmind.ai",
        name = "enabled",
        havingValue = "true"
)
public class AiTriggerService {

    private static final Logger log = LoggerFactory.getLogger(AiTriggerService.class);

    private final MarketCandlestickService candlestickService;
    private final AiAnalysisService analysisService;
    private final MarketContextBuilder contextBuilder;
    private final AiExecutionPolicyService executionPolicy;
    private final AiProperties properties;
    private final Map<String, Instant> lastAnalyzedClose = new ConcurrentHashMap<>();

    private Disposable subscription;

    public AiTriggerService(
            MarketCandlestickService candlestickService,
            AiAnalysisService analysisService,
            MarketContextBuilder contextBuilder,
            AiExecutionPolicyService executionPolicy,
            AiProperties properties
    ) {
        this.candlestickService = candlestickService;
        this.analysisService = analysisService;
        this.contextBuilder = contextBuilder;
        this.executionPolicy = executionPolicy;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        subscription = candlestickService.stream()
                .filter(Candlestick::closed)
                .filter(this::isTriggerInterval)
                .filter(this::isNewClose)
                .flatMap(candle -> {
                    if (!executionPolicy.providerReadyForAutomaticCalls()) {
                        return reactor.core.publisher.Mono.empty();
                    }

                    return reactor.core.publisher.Mono
                            .fromSupplier(() -> contextBuilder.build(
                                    candle.symbol(),
                                    candle.interval()
                            ))
                            .filter(executionPolicy::allowSignalTriggered)
                            .flatMap(context ->
                                    analysisService.analyze(
                                            context,
                                            "CANDLE_CLOSE_SIGNAL"
                                    )
                            )
                            .doOnNext(result -> log.info(
                                    "AI signal analysis completed. symbol={}, interval={}, bias={}, confidence={}",
                                    result.symbol(),
                                    result.interval(),
                                    result.marketBias(),
                                    result.confidence()
                            ))
                            .onErrorResume(error -> {
                                log.warn(
                                        "AI candle-close analysis failed. symbol={}, interval={}",
                                        candle.symbol(),
                                        candle.interval(),
                                        error
                                );
                                return reactor.core.publisher.Mono.empty();
                            });
                })
                .subscribe();
    }

    private boolean isTriggerInterval(Candlestick candle) {
        return properties.triggerIntervals() != null
                && properties.triggerIntervals().stream()
                .anyMatch(interval -> interval.equalsIgnoreCase(candle.interval()));
    }

    private boolean isNewClose(Candlestick candle) {
        String key = candle.symbol() + ":" + candle.interval();
        Instant previous = lastAnalyzedClose.put(key, candle.closeTime());

        return previous == null || candle.closeTime().isAfter(previous);
    }
}
