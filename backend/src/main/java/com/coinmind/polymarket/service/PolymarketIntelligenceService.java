package com.coinmind.polymarket.service;

import com.coinmind.polymarket.model.PolymarketIntelligence;
import io.netty.channel.ChannelOption;
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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PolymarketIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(PolymarketIntelligenceService.class);
    private static final Duration FRESHNESS = Duration.ofMinutes(10);
    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);

    private final WebClient gammaClient;
    private final WebClient clobClient;
    private final WebClient manifoldClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final Duration failureCooldown;
    private final int gammaEventLimit;
    private final int clobPageLimit;

    private final Map<String, PolymarketIntelligence> latestBySymbol = new ConcurrentHashMap<>();
    private final Map<String, Instant> unavailableUntilBySymbol = new ConcurrentHashMap<>();

    public PolymarketIntelligenceService(
            ObjectMapper objectMapper,
            @Value("${coinmind.polymarket.base-url:https://gamma-api.polymarket.com}") String gammaBaseUrl,
            @Value("${coinmind.polymarket.clob-base-url:https://clob.polymarket.com}") String clobBaseUrl,
            @Value("${coinmind.polymarket.manifold-base-url:https://api.manifold.markets}") String manifoldBaseUrl,
            @Value("${coinmind.polymarket.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${coinmind.polymarket.response-timeout-ms:8000}") int responseTimeoutMs,
            @Value("${coinmind.polymarket.failure-cooldown-ms:120000}") long failureCooldownMs,
            @Value("${coinmind.polymarket.gamma-event-limit:100}") int gammaEventLimit,
            @Value("${coinmind.polymarket.clob-page-limit:3}") int clobPageLimit
    ) {
        this.objectMapper = objectMapper;
        this.requestTimeout = Duration.ofMillis(Math.max(1000, responseTimeoutMs));
        this.failureCooldown = Duration.ofMillis(Math.max(1000, failureCooldownMs));
        this.gammaEventLimit = Math.max(20, Math.min(500, gammaEventLimit));
        this.clobPageLimit = Math.max(1, Math.min(10, clobPageLimit));

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.max(1000, connectTimeoutMs))
                .responseTimeout(this.requestTimeout);

        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

        this.gammaClient = WebClient.builder()
                .baseUrl(gammaBaseUrl)
                .clientConnector(connector)
                .build();

        this.clobClient = WebClient.builder()
                .baseUrl(clobBaseUrl)
                .clientConnector(connector)
                .build();

        this.manifoldClient = WebClient.builder()
                .baseUrl(manifoldBaseUrl)
                .clientConnector(connector)
                .build();
    }

    public Mono<PolymarketIntelligence> refresh(String symbol) {
        String normalized = normalizeSymbol(symbol);

        if (isCoolingDown(normalized)) {
            return Mono.just(fallback(normalized));
        }

        return loadGammaSignals(normalized)
                .flatMap(signals -> {
                    if (!signals.isEmpty()) {
                        log.debug("Prediction source selected. symbol={} source=POLYMARKET_GAMMA signals={}",
                                normalized, signals.size());
                        return Mono.just(new SourcedSignals("POLYMARKET_GAMMA", signals));
                    }

                    return loadFallbackSignals(normalized);
                })
                .onErrorResume(gammaError -> {
                    log.warn(
                            "Polymarket Gamma unavailable. symbol={} reason={} trying=fallback-sources",
                            normalized,
                            failureReason(gammaError)
                    );
                    return loadFallbackSignals(normalized);
                })
                .map(result -> summarize(normalized, result.source(), result.signals()))
                .timeout(requestTimeout.multipliedBy(2))
                .doOnNext(result -> {
                    latestBySymbol.put(normalized, result);
                    unavailableUntilBySymbol.remove(normalized);
                })
                .onErrorResume(error -> {
                    unavailableUntilBySymbol.put(normalized, Instant.now().plus(failureCooldown));
                    log.warn(
                            "Polymarket all sources unavailable. symbol={} reason={} fallback={}",
                            normalized,
                            failureReason(error),
                            latestBySymbol.containsKey(normalized) ? "cached" : "neutral"
                    );
                    return Mono.just(fallback(normalized));
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
                        error -> log.warn(
                                "Scheduled Polymarket refresh stopped unexpectedly. reason={}",
                                failureReason(error)
                        )
                );
    }

    private Mono<List<PolymarketIntelligence.MarketSignal>> loadGammaSignals(String symbol) {
        return gammaClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/events")
                        .queryParam("active", true)
                        .queryParam("closed", false)
                        .queryParam("order", "volume_24hr")
                        .queryParam("ascending", false)
                        .queryParam("limit", gammaEventLimit)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(requestTimeout)
                .map(this::parseJson)
                .map(root -> {
                    List<PolymarketIntelligence.MarketSignal> signals = new ArrayList<>();
                    JsonNode events = root.isArray() ? root : root.path("events");

                    if (!events.isArray()) {
                        return signals;
                    }

                    for (JsonNode event : events) {
                        if (!eventMatchesSymbol(event, symbol)) {
                            continue;
                        }
                        signals.addAll(extractGammaSignals(event, symbol));
                    }

                    return rankSignals(signals);
                });
    }

    private Mono<SourcedSignals> loadFallbackSignals(String symbol) {
        return loadClobSignals(symbol)
                .flatMap(signals -> {
                    if (!signals.isEmpty()) {
                        log.info(
                                "Prediction fallback source used. symbol={} source=POLYMARKET_CLOB signals={}",
                                symbol,
                                signals.size()
                        );
                        return Mono.just(new SourcedSignals("POLYMARKET_CLOB", signals));
                    }
                    return loadManifoldSignals(symbol)
                            .map(manifoldSignals -> new SourcedSignals("MANIFOLD", manifoldSignals));
                })
                .onErrorResume(clobError -> {
                    log.warn(
                            "Polymarket CLOB unavailable. symbol={} reason={} trying=manifold",
                            symbol,
                            failureReason(clobError)
                    );
                    return loadManifoldSignals(symbol)
                            .map(manifoldSignals -> new SourcedSignals("MANIFOLD", manifoldSignals));
                })
                .doOnNext(result -> {
                    if ("MANIFOLD".equals(result.source())) {
                        log.info(
                                "Prediction fallback source used. symbol={} source=MANIFOLD signals={}",
                                symbol,
                                result.signals().size()
                        );
                    }
                });
    }

    private Mono<List<PolymarketIntelligence.MarketSignal>> loadManifoldSignals(String symbol) {
        String term = switch (symbol) {
            case "BTCUSDT" -> "bitcoin";
            case "ETHUSDT" -> "ethereum";
            case "SOLUSDT" -> "solana";
            default -> symbol.toLowerCase(Locale.ROOT);
        };

        return manifoldClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v0/search-markets")
                        .queryParam("term", term)
                        .queryParam("sort", "24-hour-vol")
                        .queryParam("filter", "closing-month")
                        .queryParam("contractType", "BINARY")
                        .queryParam("limit", 50)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(requestTimeout)
                .map(this::parseJson)
                .map(root -> {
                    List<PolymarketIntelligence.MarketSignal> signals = new ArrayList<>();
                    if (!root.isArray()) {
                        return signals;
                    }

                    for (JsonNode market : root) {
                        String question = market.path("question").asText("");
                        if (!textMatchesSymbol(question, symbol) || !manifoldHorizonEligible(market)) {
                            continue;
                        }

                        int direction = directionalSign(question);
                        if (direction == 0) {
                            continue;
                        }

                        BigDecimal probability = decimal(market.path("probability"));
                        BigDecimal liquidity = decimal(market.path("totalLiquidity"));
                        BigDecimal volume24h = decimal(market.path("volume24Hours"));
                        int relevance = Math.min(
                                78,
                                relevance(
                                        question,
                                        symbol,
                                        liquidity,
                                        volume24h,
                                        BigDecimal.ZERO
                                )
                        );

                        signals.add(new PolymarketIntelligence.MarketSignal(
                                "manifold",
                                market.path("id").asText(""),
                                question,
                                probability,
                                BigDecimal.ZERO,
                                liquidity,
                                volume24h,
                                direction > 0 ? "BULLISH" : "BEARISH",
                                relevance
                        ));
                    }

                    return rankSignals(signals);
                });
    }

    private Mono<List<PolymarketIntelligence.MarketSignal>> loadClobSignals(String symbol) {
        return loadClobPage(symbol, "MA==", 0, new ArrayList<>())
                .map(this::rankSignals);
    }

    private Mono<List<PolymarketIntelligence.MarketSignal>> loadClobPage(
            String symbol,
            String cursor,
            int page,
            List<PolymarketIntelligence.MarketSignal> collected
    ) {
        if (page >= clobPageLimit || collected.size() >= 16) {
            return Mono.just(collected);
        }

        return clobClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/simplified-markets")
                        .queryParam("next_cursor", cursor)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(requestTimeout)
                .map(this::parseJson)
                .flatMap(root -> {
                    JsonNode data = root.path("data");

                    if (data.isArray()) {
                        for (JsonNode market : data) {
                            PolymarketIntelligence.MarketSignal signal = extractClobSignal(market, symbol);
                            if (signal != null) {
                                collected.add(signal);
                            }
                        }
                    }

                    String nextCursor = root.path("next_cursor").asText("");
                    if (nextCursor.isBlank()
                            || "LTE=".equals(nextCursor)
                            || nextCursor.equals(cursor)
                            || collected.size() >= 16) {
                        return Mono.just(collected);
                    }

                    return loadClobPage(symbol, nextCursor, page + 1, collected);
                });
    }

    private List<PolymarketIntelligence.MarketSignal> extractGammaSignals(
            JsonNode event,
            String symbol
    ) {
        List<PolymarketIntelligence.MarketSignal> signals = new ArrayList<>();
        String eventId = event.path("id").asText("");

        for (JsonNode market : event.path("markets")) {
            boolean active = market.path("active").asBoolean(true);
            boolean closed = market.path("closed").asBoolean(false);

            if (!active || closed) {
                continue;
            }

            String question = market.path("question").asText("");
            if (!textMatchesSymbol(question, symbol)) {
                continue;
            }

            String marketId = firstText(market, "id", "conditionId", "condition_id");
            BigDecimal yesProbability = yesProbability(market.path("outcomePrices"));
            BigDecimal oneDayChange = firstDecimal(
                    market,
                    "oneDayPriceChange",
                    "oneDayPriceChangeNum",
                    "one_day_price_change"
            );
            BigDecimal liquidity = firstDecimal(
                    market,
                    "liquidityNum",
                    "liquidity",
                    "liquidity_num"
            );
            BigDecimal volume24h = firstDecimal(
                    market,
                    "volume24hr",
                    "volume24h",
                    "volume24hrNum",
                    "volume_24hr"
            );

            int direction = directionalSign(question);
            if (direction == 0) {
                continue;
            }

            int relevance = relevance(question, symbol, liquidity, volume24h, oneDayChange);
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

    private PolymarketIntelligence.MarketSignal extractClobSignal(
            JsonNode market,
            String symbol
    ) {
        String question = firstText(
                market,
                "question",
                "market",
                "title",
                "description"
        );

        if (question.isBlank() || !textMatchesSymbol(question, symbol)) {
            return null;
        }

        int direction = directionalSign(question);
        if (direction == 0) {
            return null;
        }

        String marketId = firstText(
                market,
                "condition_id",
                "conditionId",
                "market_id",
                "id"
        );

        BigDecimal yesProbability = clobYesProbability(market);
        BigDecimal liquidity = firstDecimal(
                market,
                "liquidity",
                "liquidity_num",
                "liquidityNum"
        );
        BigDecimal volume24h = firstDecimal(
                market,
                "volume_24hr",
                "volume24hr",
                "volume24h"
        );

        int relevance = Math.min(
                80,
                relevance(
                        question,
                        symbol,
                        liquidity,
                        volume24h,
                        BigDecimal.ZERO
                )
        );

        return new PolymarketIntelligence.MarketSignal(
                "clob",
                marketId,
                question,
                yesProbability,
                BigDecimal.ZERO,
                liquidity,
                volume24h,
                direction > 0 ? "BULLISH" : "BEARISH",
                relevance
        );
    }

    private List<PolymarketIntelligence.MarketSignal> rankSignals(
            List<PolymarketIntelligence.MarketSignal> signals
    ) {
        return signals.stream()
                .filter(signal -> signal.relevanceScore() >= 40)
                .sorted(Comparator.comparingInt(
                        PolymarketIntelligence.MarketSignal::relevanceScore
                ).reversed())
                .limit(8)
                .toList();
    }

    private PolymarketIntelligence summarize(
            String symbol,
            String source,
            List<PolymarketIntelligence.MarketSignal> signals
    ) {
        if (signals.isEmpty()) {
            return empty(symbol, source);
        }

        BigDecimal weightedSignedEvidence = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        int importance = 0;
        boolean probabilityOnlyFallback = "POLYMARKET_CLOB".equals(source)
                || "MANIFOLD".equals(source);

        for (PolymarketIntelligence.MarketSignal signal : signals) {
            BigDecimal qualityWeight = BigDecimal.valueOf(Math.max(1, signal.relevanceScore()));
            int sign = "BULLISH".equals(signal.directionalBias()) ? 1 : -1;

            BigDecimal evidence = signal.oneDayProbabilityChange();

            if ("POLYMARKET_CLOB".equals(source)
                    && evidence.signum() == 0
                    && signal.yesProbability().signum() > 0) {
                evidence = signal.yesProbability()
                        .subtract(BigDecimal.valueOf(0.5), MC)
                        .multiply(BigDecimal.valueOf(0.06), MC);
            }

            if ("MANIFOLD".equals(source)) {
                evidence = BigDecimal.ZERO;
            }

            weightedSignedEvidence = weightedSignedEvidence.add(
                    evidence
                            .multiply(BigDecimal.valueOf(sign), MC)
                            .multiply(qualityWeight, MC),
                    MC
            );
            totalWeight = totalWeight.add(qualityWeight, MC);
            importance = Math.max(importance, signal.relevanceScore());
        }

        BigDecimal averageChange = totalWeight.signum() == 0
                ? BigDecimal.ZERO
                : weightedSignedEvidence.divide(totalWeight, 6, RoundingMode.HALF_UP);

        String bias = "MANIFOLD".equals(source)
                ? "NEUTRAL"
                : averageChange.compareTo(BigDecimal.valueOf(0.015)) > 0
                ? "BULLISH"
                : averageChange.compareTo(BigDecimal.valueOf(-0.015)) < 0
                ? "BEARISH"
                : "NEUTRAL";

        int confidence = Math.min(
                "MANIFOLD".equals(source) ? 45 : probabilityOnlyFallback ? 60 : 90,
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
                source,
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
            String symbol,
            BigDecimal liquidity,
            BigDecimal volume24h,
            BigDecimal oneDayChange
    ) {
        int score = 30;

        if (textMatchesSymbol(question, symbol)) {
            score += 30;
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

    private boolean manifoldHorizonEligible(JsonNode market) {
        long closeTime = market.path("closeTime").asLong(0L);
        if (closeTime <= 0L) {
            return false;
        }

        Instant closeAt = Instant.ofEpochMilli(closeTime);
        Instant now = Instant.now();

        return closeAt.isAfter(now)
                && closeAt.isBefore(now.plus(Duration.ofDays(35)));
    }

    private boolean eventMatchesSymbol(JsonNode event, String symbol) {
        return textMatchesSymbol(
                event.path("title").asText("")
                        + " "
                        + event.path("description").asText("")
                        + " "
                        + event.path("slug").asText(""),
                symbol
        );
    }

    private boolean textMatchesSymbol(String text, String symbol) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);

        return switch (symbol) {
            case "BTCUSDT" -> normalized.contains("bitcoin") || normalized.contains("btc");
            case "ETHUSDT" -> normalized.contains("ethereum") || normalized.contains("ether")
                    || normalized.contains("eth");
            case "SOLUSDT" -> normalized.contains("solana") || normalized.contains("sol");
            default -> normalized.contains(symbol.toLowerCase(Locale.ROOT));
        };
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

    private BigDecimal yesProbability(JsonNode outcomePrices) {
        if (outcomePrices == null || outcomePrices.isMissingNode() || outcomePrices.isNull()) {
            return BigDecimal.ZERO;
        }

        try {
            if (outcomePrices.isArray() && !outcomePrices.isEmpty()) {
                return decimal(outcomePrices.get(0));
            }

            if (outcomePrices.isTextual()) {
                JsonNode prices = objectMapper.readTree(outcomePrices.asText(""));
                if (prices.isArray() && !prices.isEmpty()) {
                    return decimal(prices.get(0));
                }
            }
        } catch (Exception ignored) {
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal clobYesProbability(JsonNode market) {
        BigDecimal direct = firstDecimal(
                market,
                "yes_price",
                "yesPrice",
                "price"
        );

        if (direct.signum() > 0 && direct.compareTo(BigDecimal.ONE) <= 0) {
            return direct;
        }

        JsonNode tokens = market.path("tokens");
        if (tokens.isArray()) {
            for (JsonNode token : tokens) {
                if ("YES".equalsIgnoreCase(token.path("outcome").asText(""))) {
                    BigDecimal price = firstDecimal(token, "price", "last_price", "midpoint");
                    if (price.signum() > 0 && price.compareTo(BigDecimal.ONE) <= 0) {
                        return price;
                    }
                }
            }
        }

        return BigDecimal.ZERO;
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to parse Polymarket response", ex);
        }
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText("").trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private BigDecimal firstDecimal(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                BigDecimal parsed = decimal(value);
                if (parsed.signum() != 0) {
                    return parsed;
                }
            }
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

    private boolean isCoolingDown(String symbol) {
        Instant unavailableUntil = unavailableUntilBySymbol.get(symbol);
        if (unavailableUntil == null) {
            return false;
        }

        if (unavailableUntil.isAfter(Instant.now())) {
            return true;
        }

        unavailableUntilBySymbol.remove(symbol);
        return false;
    }

    private PolymarketIntelligence fallback(String symbol) {
        PolymarketIntelligence cached = latestBySymbol.get(symbol);
        return cached == null ? empty(symbol, "NONE") : cached;
    }

    private String failureReason(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }

        String message = cause.getMessage();
        return cause.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private PolymarketIntelligence empty(String symbol, String source) {
        return new PolymarketIntelligence(
                symbol,
                source,
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
    private record SourcedSignals(
            String source,
            List<PolymarketIntelligence.MarketSignal> signals
    ) {
    }

}
