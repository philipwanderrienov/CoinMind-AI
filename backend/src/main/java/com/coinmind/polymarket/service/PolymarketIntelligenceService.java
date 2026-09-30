package com.coinmind.polymarket.service;

import com.coinmind.polymarket.model.PolymarketIntelligence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PolymarketIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(PolymarketIntelligenceService.class);
    private static final Duration FRESHNESS = Duration.ofMinutes(10);
    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Map<String, PolymarketIntelligence> latestBySymbol = new ConcurrentHashMap<>();

    public PolymarketIntelligenceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl("https://gamma-api.polymarket.com")
                .build();
    }

    public Mono<PolymarketIntelligence> refresh(String symbol) {
        String normalized = normalizeSymbol(symbol);

        return Flux.fromIterable(searchQueries(normalized))
                .concatMap(this::searchEvents)
                .flatMapIterable(events -> events)
                .distinct(event -> event.path("id").asText(""))
                .take(12)
                .concatMap(this::fetchEvent)
                .flatMapIterable(this::extractSignals)
                .filter(signal -> signal.relevanceScore() >= 40)
                .sort(Comparator.comparingInt(PolymarketIntelligence.MarketSignal::relevanceScore).reversed())
                .take(8)
                .collectList()
                .map(signals -> summarize(normalized, signals))
                .doOnNext(result -> latestBySymbol.put(normalized, result))
                .onErrorResume(error -> {
                    log.warn("Polymarket intelligence refresh failed. symbol={}", normalized, error);
                    PolymarketIntelligence cached = latestFresh(normalized);
                    return cached == null
                            ? Mono.just(empty(normalized))
                            : Mono.just(cached);
                });
    }

    public PolymarketIntelligence latestFresh(String symbol) {
        PolymarketIntelligence intelligence = latestBySymbol.get(normalizeSymbol(symbol));

        if (intelligence == null || intelligence.updatedAt() == null) {
            return null;
        }

        return intelligence.updatedAt().plus(FRESHNESS).isAfter(Instant.now())
                ? intelligence
                : null;
    }

    @Scheduled(initialDelay = 15000, fixedDelay = 300000)
    public void refreshTrackedSymbols() {
        Flux.fromIterable(List.of("BTCUSDT", "ETHUSDT", "SOLUSDT"))
                .concatMap(this::refresh)
                .subscribe(
                        ignored -> { },
                        error -> log.warn("Scheduled Polymarket refresh failed", error)
                );
    }

    private Mono<List<JsonNode>> searchEvents(String query) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/public-search")
                        .queryParam("q", query)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        List<JsonNode> events = new ArrayList<>();
                        root.path("events").forEach(events::add);
                        return events;
                    } catch (Exception ex) {
                        throw new IllegalStateException("Unable to parse Polymarket search response", ex);
                    }
                });
    }

    private Mono<JsonNode> fetchEvent(JsonNode event) {
        String id = event.path("id").asText("");
        if (id.isBlank()) {
            return Mono.empty();
        }

        return webClient.get()
                .uri("/events/{id}", id)
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        return objectMapper.readTree(body);
                    } catch (Exception ex) {
                        throw new IllegalStateException("Unable to parse Polymarket event", ex);
                    }
                });
    }

    private List<PolymarketIntelligence.MarketSignal> extractSignals(JsonNode event) {
        List<PolymarketIntelligence.MarketSignal> signals = new ArrayList<>();
        String eventId = event.path("id").asText("");

        for (JsonNode market : event.path("markets")) {
            boolean active = market.path("active").asBoolean(false);
            boolean closed = market.path("closed").asBoolean(false);

            if (!active || closed) {
                continue;
            }

            String question = market.path("question").asText("");
            String marketId = market.path("id").asText("");

            BigDecimal yesProbability = yesProbability(market.path("outcomePrices").asText(""));
            BigDecimal oneDayChange = decimal(market.path("oneDayPriceChange"));
            BigDecimal liquidity = decimal(market.path("liquidity"));
            BigDecimal volume24h = decimal(market.path("volume24hr"));

            int direction = directionalSign(question);
            if (direction == 0) {
                continue;
            }

            int relevance = relevance(question, liquidity, volume24h, oneDayChange);
            String bias = direction > 0 ? "BULLISH" : "BEARISH";

            signals.add(new PolymarketIntelligence.MarketSignal(
                    eventId,
                    marketId,
                    question,
                    yesProbability,
                    oneDayChange,
                    liquidity,
                    volume24h,
                    bias,
                    relevance
            ));
        }

        return signals;
    }

    private PolymarketIntelligence summarize(
            String symbol,
            List<PolymarketIntelligence.MarketSignal> signals
    ) {
        if (signals.isEmpty()) {
            return empty(symbol);
        }

        BigDecimal weightedSignedChange = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        int importance = 0;

        for (PolymarketIntelligence.MarketSignal signal : signals) {
            BigDecimal qualityWeight = BigDecimal.valueOf(Math.max(1, signal.relevanceScore()));
            int sign = "BULLISH".equals(signal.directionalBias()) ? 1 : -1;

            weightedSignedChange = weightedSignedChange.add(
                    signal.oneDayProbabilityChange()
                            .multiply(BigDecimal.valueOf(sign), MC)
                            .multiply(qualityWeight, MC),
                    MC
            );
            totalWeight = totalWeight.add(qualityWeight, MC);
            importance = Math.max(importance, signal.relevanceScore());
        }

        BigDecimal averageChange = totalWeight.signum() == 0
                ? BigDecimal.ZERO
                : weightedSignedChange.divide(totalWeight, 6, RoundingMode.HALF_UP);

        String bias = averageChange.compareTo(BigDecimal.valueOf(0.015)) > 0
                ? "BULLISH"
                : averageChange.compareTo(BigDecimal.valueOf(-0.015)) < 0
                ? "BEARISH"
                : "NEUTRAL";

        int confidence = Math.min(
                90,
                35
                        + Math.min(25, signals.size() * 5)
                        + Math.min(30, averageChange.abs()
                                .multiply(BigDecimal.valueOf(200))
                                .intValue())
        );

        PolymarketIntelligence.MarketSignal top = signals.stream()
                .max(Comparator.comparingInt(PolymarketIntelligence.MarketSignal::relevanceScore))
                .orElse(signals.get(0));

        return new PolymarketIntelligence(
                symbol,
                bias,
                confidence,
                importance,
                signals.size(),
                averageChange,
                top.question(),
                List.copyOf(signals),
                Instant.now()
        );
    }

    private int relevance(
            String question,
            BigDecimal liquidity,
            BigDecimal volume24h,
            BigDecimal oneDayChange
    ) {
        int score = 35;

        String normalized = question.toLowerCase(Locale.ROOT);
        if (normalized.contains("bitcoin") || normalized.contains("ethereum") || normalized.contains("solana")) {
            score += 25;
        }

        if (liquidity.compareTo(BigDecimal.valueOf(10_000)) >= 0) {
            score += 15;
        } else if (liquidity.compareTo(BigDecimal.valueOf(2_500)) >= 0) {
            score += 8;
        }

        if (volume24h.compareTo(BigDecimal.valueOf(5_000)) >= 0) {
            score += 15;
        } else if (volume24h.compareTo(BigDecimal.valueOf(1_000)) >= 0) {
            score += 8;
        }

        if (oneDayChange.abs().compareTo(BigDecimal.valueOf(0.05)) >= 0) {
            score += 10;
        } else if (oneDayChange.abs().compareTo(BigDecimal.valueOf(0.02)) >= 0) {
            score += 5;
        }

        return Math.min(100, score);
    }

    private int directionalSign(String question) {
        String text = question.toLowerCase(Locale.ROOT);

        if (containsAny(text, List.of(
                "fall below", "drop below", "below $", "under $", "crash",
                "fall to", "drop to", "decline to", "lower than"
        ))) {
            return -1;
        }

        if (containsAny(text, List.of(
                "reach $", "hit $", "above $", "over $", "rise to",
                "exceed $", "higher than", "all-time high", "all time high"
        ))) {
            return 1;
        }

        return 0;
    }

    private BigDecimal yesProbability(String encodedPrices) {
        if (encodedPrices == null || encodedPrices.isBlank()) {
            return BigDecimal.ZERO;
        }

        try {
            JsonNode prices = objectMapper.readTree(encodedPrices);
            if (prices.isArray() && !prices.isEmpty()) {
                return decimal(prices.get(0));
            }
        } catch (Exception ignored) {
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(node.asText("0"));
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private List<String> searchQueries(String symbol) {
        return switch (symbol) {
            case "BTCUSDT" -> List.of("bitcoin");
            case "ETHUSDT" -> List.of("ethereum");
            case "SOLUSDT" -> List.of("solana");
            default -> List.of(symbol.toLowerCase(Locale.ROOT));
        };
    }

    private String normalizeSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.toUpperCase(Locale.ROOT);

        if (normalized.equals("BTC")) return "BTCUSDT";
        if (normalized.equals("ETH")) return "ETHUSDT";
        if (normalized.equals("SOL")) return "SOLUSDT";

        return normalized;
    }

    private boolean containsAny(String value, List<String> terms) {
        return terms.stream().anyMatch(value::contains);
    }

    private PolymarketIntelligence empty(String symbol) {
        return new PolymarketIntelligence(
                symbol,
                "NEUTRAL",
                0,
                0,
                0,
                BigDecimal.ZERO,
                "",
                List.of(),
                Instant.now()
        );
    }
}
