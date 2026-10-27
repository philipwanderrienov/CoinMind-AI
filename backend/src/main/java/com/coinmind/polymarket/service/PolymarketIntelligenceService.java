package com.coinmind.polymarket.service;

import com.coinmind.polymarket.model.PolymarketIntelligence;
import com.coinmind.polymarket.model.PredictionSourceHealth;
import io.netty.channel.ChannelOption;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Legacy type name retained temporarily for downstream compatibility.
 * Manifold is the ONLY network source; no Gamma or CLOB calls exist.
 * Probabilities are contextual and NEVER translated into trade direction.
 */
@Service
public class PolymarketIntelligenceService {
    private static final Logger log = LoggerFactory.getLogger(PolymarketIntelligenceService.class);
    private static final Duration FRESHNESS = Duration.ofMinutes(10);
    private final WebClient manifoldClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final Duration failureCooldown;
    private final Map<String, PolymarketIntelligence> latest = new ConcurrentHashMap<>();
    private final Map<String, Instant> cooldown = new ConcurrentHashMap<>();
    private volatile PredictionSourceHealth.SourceStatus health =
            new PredictionSourceHealth.SourceStatus("MANIFOLD", "UNKNOWN", "Not checked yet", null, 0);

    public PolymarketIntelligenceService(
            ObjectMapper objectMapper,
            @Value("${coinmind.manifold.base-url:https://api.manifold.markets}") String baseUrl,
            @Value("${coinmind.manifold.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${coinmind.manifold.response-timeout-ms:8000}") int responseTimeoutMs,
            @Value("${coinmind.manifold.failure-cooldown-ms:120000}") long failureCooldownMs) {
        this.objectMapper = objectMapper;
        this.requestTimeout = Duration.ofMillis(Math.max(1000, responseTimeoutMs));
        this.failureCooldown = Duration.ofMillis(Math.max(1000, failureCooldownMs));
        HttpClient client = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.max(1000, connectTimeoutMs))
                .responseTimeout(this.requestTimeout);
        this.manifoldClient = WebClient.builder().baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(client)).build();
    }

    public Mono<PolymarketIntelligence> refresh(String symbol) {
        String normalized = normalize(symbol);
        Instant now = Instant.now();
        if (cooldown.getOrDefault(normalized, Instant.EPOCH).isAfter(now)) {
            return Mono.just(fallback(normalized));
        }
        String term = switch (normalized) {
            case "BTCUSDT" -> "bitcoin";
            case "ETHUSDT" -> "ethereum";
            case "SOLUSDT" -> "solana";
            default -> "";
        };
        if (term.isEmpty()) return Mono.just(fallback(normalized));
        return manifoldClient.get()
                .uri(builder -> builder.path("/v0/search-markets")
                        .queryParam("term", term).queryParam("filter", "open")
                        .queryParam("sort", "24-hour-vol")
                        .queryParam("contractType", "BINARY")
                        .queryParam("limit", 100).build())
                .retrieve().bodyToMono(String.class)
                .timeout(requestTimeout)
                .map(body -> parse(normalized, body))
                .doOnNext(result -> {
                    latest.put(normalized, result);
                    cooldown.remove(normalized);
                    health = new PredictionSourceHealth.SourceStatus(
                            "MANIFOLD", result.marketCount() > 0 ? "UP" : "EMPTY",
                            "Public search responded", Instant.now(), result.marketCount());
                })
                .onErrorResume(error -> {
                    cooldown.put(normalized, Instant.now().plus(failureCooldown));
                    health = new PredictionSourceHealth.SourceStatus(
                            "MANIFOLD", "UNAVAILABLE", error.getClass().getSimpleName(),
                            Instant.now(), 0);
                    log.warn("Manifold unavailable for {}: {}", normalized, error.toString());
                    return Mono.just(fallback(normalized));
                });
    }

    private PolymarketIntelligence parse(String symbol, String body) {
        JsonNode root = objectMapper.readTree(body);
        List<PolymarketIntelligence.MarketSignal> signals = new ArrayList<>();
        long now = Instant.now().toEpochMilli();
        long cutoff = Instant.now().plus(Duration.ofDays(35)).toEpochMilli();
        if (root.isArray()) for (JsonNode market : root) {
            if (!"BINARY".equals(market.path("outcomeType").asText("")) ||
                    market.path("isResolved").asBoolean(false)) continue;
            JsonNode end = market.path("closeTime");
            if (end.isMissingNode() || end.isNull() || !end.canConvertToLong()) continue;
            long close = end.asLong();
            if (close <= now || close > cutoff) continue;
            String question = market.path("question").asText("");
            String q = question.toLowerCase(Locale.ROOT);
            boolean related = switch (symbol) {
                case "BTCUSDT" -> q.contains("bitcoin") || q.matches(".*\\bbtc\\b.*");
                case "ETHUSDT" -> q.contains("ethereum") || q.matches(".*\\beth\\b.*");
                case "SOLUSDT" -> q.contains("solana") || q.matches(".*\\bsol\\b.*");
                default -> false;
            };
            if (!related || !market.path("probability").isNumber()) continue;
            BigDecimal probability = market.path("probability").decimalValue();
            if (probability.compareTo(BigDecimal.ZERO) < 0 || probability.compareTo(BigDecimal.ONE) > 0) continue;
            BigDecimal volume = decimal(market.path("volume24Hours"));
            BigDecimal liquidity = decimal(market.path("totalLiquidity"));
            int relevance = 35 + (volume.signum() > 0 ? 20 : 0) + (liquidity.signum() > 0 ? 10 : 0);
            signals.add(new PolymarketIntelligence.MarketSignal(
                    "manifold", market.path("id").asText(""), question, probability,
                    BigDecimal.ZERO, liquidity, volume, "NEUTRAL", relevance));
        }
        signals.sort(Comparator.comparing(PolymarketIntelligence.MarketSignal::volume24h).reversed());
        List<PolymarketIntelligence.MarketSignal> top = signals.stream().limit(8).toList();
        return new PolymarketIntelligence(symbol, "MANIFOLD", "NEUTRAL",
                top.isEmpty() ? 0 : 35, top.isEmpty() ? 0 : 40, top.size(),
                BigDecimal.ZERO, top.isEmpty() ? "" : top.get(0).question(),
                top, Instant.now());
    }

    private BigDecimal decimal(JsonNode node) {
        return node.isNumber() ? node.decimalValue() : BigDecimal.ZERO;
    }

    private PolymarketIntelligence fallback(String symbol) {
        PolymarketIntelligence cached = latestFresh(symbol);
        if (cached != null) return cached;
        return new PolymarketIntelligence(symbol, "NONE", "NEUTRAL", 0, 0, 0,
                BigDecimal.ZERO, "", List.of(), Instant.now());
    }

    private String normalize(String symbol) {
        String s = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        return switch(s) {
            case "BTC" -> "BTCUSDT";
            case "ETH" -> "ETHUSDT";
            case "SOL" -> "SOLUSDT";
            default -> s;
        };
    }

    public PolymarketIntelligence latestFresh(String symbol) {
        PolymarketIntelligence found = latest.get(normalize(symbol));
        return found != null && found.updatedAt().plus(FRESHNESS).isAfter(Instant.now()) ? found : null;
    }

    public PredictionSourceHealth sourceHealth() {
        return new PredictionSourceHealth(List.of(health), Instant.now());
    }

    @Scheduled(initialDelay = 15000, fixedDelay = 300000)
    public void refreshTrackedSymbols() {
        Flux.fromIterable(List.of("BTCUSDT", "ETHUSDT", "SOLUSDT")).concatMap(this::refresh)
                .subscribe(ignored -> {}, error -> log.warn("Manifold refresh error", error));
    }
}
