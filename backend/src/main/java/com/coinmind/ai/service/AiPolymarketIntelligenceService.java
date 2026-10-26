package com.coinmind.ai.service;

import com.coinmind.ai.config.AiProperties;
import com.coinmind.ai.model.AiPolymarketIntelligence;
import com.coinmind.ai.provider.AiProvider;
import com.coinmind.polymarket.model.PolymarketIntelligence;
import com.coinmind.polymarket.service.PolymarketIntelligenceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiPolymarketIntelligenceService {

    private final PolymarketIntelligenceService polymarketService;
    private final AiProvider aiProvider;
    private final AiProperties properties;
    private final AiBudgetGuardrailService budgetGuardrailService;
    private final Map<String, AiPolymarketIntelligence> latestBySymbol = new ConcurrentHashMap<>();
    private final Map<String, String> lastAnalyzedFingerprint = new ConcurrentHashMap<>();

    public AiPolymarketIntelligenceService(
            PolymarketIntelligenceService polymarketService,
            AiProvider aiProvider,
            AiProperties properties,
            AiBudgetGuardrailService budgetGuardrailService
    ) {
        this.polymarketService = polymarketService;
        this.aiProvider = aiProvider;
        this.properties = properties;
        this.budgetGuardrailService = budgetGuardrailService;
    }

    public Mono<AiPolymarketIntelligence> analyze(String symbol, boolean force) {
        String normalized = normalizeSymbol(symbol);
        PolymarketIntelligence cached = polymarketService.latestFresh(normalized);

        Mono<PolymarketIntelligence> source = cached == null
                ? polymarketService.refresh(normalized)
                : Mono.just(cached);

        return source.flatMap(intelligence -> analyze(intelligence, force));
    }

    public AiPolymarketIntelligence latestFresh(String symbol) {
        AiPolymarketIntelligence intelligence = latestBySymbol.get(normalizeSymbol(symbol));
        if (intelligence == null || intelligence.analyzedAt() == null) {
            return null;
        }

        Duration freshness = Duration.ofMinutes(Math.max(1, properties.polymarketCacheMinutes()));
        return intelligence.analyzedAt().plus(freshness).isAfter(Instant.now())
                ? intelligence
                : null;
    }

    public AiPolymarketIntelligence deterministicFallback(String symbol) {
        String normalized = normalizeSymbol(symbol);
        PolymarketIntelligence intelligence = polymarketService.latestFresh(normalized);

        if (intelligence == null) {
            return unavailable(normalized, "Belum ada data Polymarket yang tersedia.", 0, "NEUTRAL", 0);
        }

        int materiality = materialityScore(intelligence);
        return skipped(
                intelligence,
                materiality,
                "Analisis AI belum dijalankan; deterministic Polymarket signal tetap tersedia."
        );
    }

    private Mono<AiPolymarketIntelligence> analyze(
            PolymarketIntelligence intelligence,
            boolean force
    ) {
        int materiality = materialityScore(intelligence);

        if (intelligence.marketCount() <= 0) {
            return Mono.just(unavailable(
                    intelligence.symbol(),
                    "Tidak ada prediction market relevan untuk dianalisis.",
                    materiality,
                    intelligence.bias(),
                    intelligence.marketCount()
            ));
        }

        if (!properties.enabled() || !properties.polymarketEnabled()) {
            return Mono.just(skipped(
                    intelligence,
                    materiality,
                    "AI Polymarket Intelligence belum diaktifkan."
            ));
        }

        String fingerprint = fingerprint(intelligence);
        AiPolymarketIntelligence fresh = latestFresh(intelligence.symbol());

        if (!force && fresh != null && fingerprint.equals(lastAnalyzedFingerprint.get(intelligence.symbol()))) {
            return Mono.just(fresh);
        }

        if (!force && !"POLYMARKET_GAMMA".equals(intelligence.source())) {
            return Mono.just(skipped(
                    intelligence,
                    materiality,
                    "Fallback forecast source hanya digunakan sebagai context; automatic paid AI call dinonaktifkan."
            ));
        }

        if (!force && !isMaterial(intelligence, materiality)) {
            return Mono.just(skipped(
                    intelligence,
                    materiality,
                    "Perubahan prediction market belum cukup material untuk memakai token AI."
            ));
        }

        if (!force && !budgetGuardrailService.allowAutomaticCall()) {
            return Mono.just(skipped(
                    intelligence,
                    materiality,
                    "Automatic AI call diblokir oleh hourly/daily budget guardrail."
            ));
        }

        return aiProvider.analyzePolymarket(intelligence, materiality)
                .doOnNext(result -> {
                    latestBySymbol.put(intelligence.symbol(), result);
                    lastAnalyzedFingerprint.put(intelligence.symbol(), fingerprint);
                })
                .onErrorResume(error -> Mono.just(unavailable(
                        intelligence.symbol(),
                        "AI Polymarket analysis gagal; deterministic signal tetap digunakan.",
                        materiality,
                        intelligence.bias(),
                        intelligence.marketCount()
                )));
    }

    private boolean isMaterial(PolymarketIntelligence intelligence, int materiality) {
        BigDecimal minChange = properties.polymarketMinProbabilityChange() == null
                ? BigDecimal.valueOf(0.03)
                : properties.polymarketMinProbabilityChange();

        int maxRelevance = intelligence.signals().stream()
                .mapToInt(PolymarketIntelligence.MarketSignal::relevanceScore)
                .max()
                .orElse(0);

        return intelligence.averageProbabilityChange().abs().compareTo(minChange) >= 0
                || intelligence.importance() >= properties.polymarketMinImportance()
                || maxRelevance >= properties.polymarketMinImportance()
                || materiality >= properties.polymarketDeepMaterialityScore();
    }

    private int materialityScore(PolymarketIntelligence intelligence) {
        int maxRelevance = intelligence.signals().stream()
                .mapToInt(PolymarketIntelligence.MarketSignal::relevanceScore)
                .max()
                .orElse(0);

        int probabilityScore = intelligence.averageProbabilityChange()
                .abs()
                .multiply(BigDecimal.valueOf(2000))
                .intValue();

        int breadthScore = Math.min(40, intelligence.marketCount() * 8);

        return Math.min(
                100,
                Math.max(
                        Math.max(intelligence.importance(), maxRelevance),
                        Math.max(probabilityScore, breadthScore)
                )
        );
    }

    private String fingerprint(PolymarketIntelligence intelligence) {
        String topMarket = intelligence.signals().stream()
                .findFirst()
                .map(signal -> signal.marketId() + ":" + signal.oneDayProbabilityChange())
                .orElse("");

        return intelligence.bias()
                + "|" + intelligence.topEvent()
                + "|" + intelligence.averageProbabilityChange()
                + "|" + intelligence.marketCount()
                + "|" + topMarket;
    }

    @Scheduled(initialDelay = 45000, fixedDelay = 300000)
    public void analyzeTrackedSymbols() {
        if (!properties.enabled() || !properties.polymarketEnabled()) {
            return;
        }

        Flux.fromIterable(List.of("BTCUSDT", "ETHUSDT", "SOLUSDT"))
                .concatMap(symbol -> {
                    PolymarketIntelligence intelligence = polymarketService.latestFresh(symbol);
                    return intelligence == null
                            ? Mono.empty()
                            : analyze(intelligence, false);
                })
                .subscribe();
    }

    private AiPolymarketIntelligence skipped(
            PolymarketIntelligence intelligence,
            int materiality,
            String reason
    ) {
        return new AiPolymarketIntelligence(
                intelligence.symbol(),
                "SKIPPED",
                intelligence.bias(),
                intelligence.importance(),
                intelligence.confidence(),
                "NONE",
                "Deterministic Polymarket signal tersedia tanpa pemanggilan AI.",
                intelligence.topEvent().isBlank() ? List.of() : List.of(intelligence.topEvent()),
                List.of(),
                List.of(),
                materiality,
                intelligence.bias(),
                intelligence.marketCount(),
                "deterministic",
                reason,
                Instant.now()
        );
    }

    private AiPolymarketIntelligence unavailable(
            String symbol,
            String reason,
            int materiality,
            String deterministicBias,
            int marketCount
    ) {
        return new AiPolymarketIntelligence(
                symbol,
                "UNAVAILABLE",
                deterministicBias,
                0,
                0,
                "NONE",
                reason,
                List.of(),
                List.of(),
                List.of(),
                materiality,
                deterministicBias,
                marketCount,
                "none",
                reason,
                Instant.now()
        );
    }

    private String normalizeSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.toUpperCase(Locale.ROOT);
        if ("BTC".equals(normalized)) return "BTCUSDT";
        if ("ETH".equals(normalized)) return "ETHUSDT";
        if ("SOL".equals(normalized)) return "SOLUSDT";
        return normalized;
    }
}
