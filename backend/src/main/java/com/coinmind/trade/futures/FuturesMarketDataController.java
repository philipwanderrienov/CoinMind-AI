package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import io.netty.channel.ChannelOption;
import tools.jackson.databind.JsonNode;

/** Public, read-only USD-M derivatives indicators. No authenticated/order APIs. */
@RestController
@RequestMapping("/api/v1/futures/market-data")
public class FuturesMarketDataController {
    private static final Set<String> SYMBOLS = Set.of("BTCUSDT", "ETHUSDT", "SOLUSDT");
    private final WebClient client;
    private final boolean enabled;

    public FuturesMarketDataController(
            @Value("${coinmind.futures.market-data-enabled:false}") boolean enabled,
            @Value("${coinmind.futures.base-url:https://fapi.binance.com}") String url) {
        this.enabled = enabled;
        this.client = WebClient.builder().baseUrl(url)
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 4000)
                                .responseTimeout(Duration.ofSeconds(7)))).build();
    }

    @GetMapping("/{symbol}")
    public Mono<Snapshot> snapshot(@PathVariable String symbol) {
        if (!SYMBOLS.contains(symbol)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported futures symbol"));
        }
        if (!enabled) {
            return Mono.just(new Snapshot(symbol, "DISABLED", null, null, null, null, null,
                    "Futures feed disabled; no spot fallback", Instant.now()));
        }
        Mono<JsonNode> premium = client.get().uri(b -> b.path("/fapi/v1/premiumIndex")
                .queryParam("symbol", symbol).build()).retrieve().bodyToMono(JsonNode.class);
        Mono<JsonNode> oi = client.get().uri(b -> b.path("/fapi/v1/openInterest")
                .queryParam("symbol", symbol).build()).retrieve().bodyToMono(JsonNode.class);
        return Mono.zip(premium, oi).timeout(Duration.ofSeconds(9))
                .map(t -> {
                    JsonNode p = t.getT1();
                    JsonNode o = t.getT2();
                    BigDecimal mark = positive(p.path("markPrice").asText(""));
                    BigDecimal index = positive(p.path("indexPrice").asText(""));
                    BigDecimal openInterest = positive(o.path("openInterest").asText(""));
                    BigDecimal funding = numeric(p.path("lastFundingRate").asText(""));
                    long published = p.path("time").asLong(0);
                    long oiTime = o.path("time").asLong(0);
                    boolean fresh = published > 0 && oiTime > 0
                            && Math.abs(System.currentTimeMillis() - published) < 120_000
                            && Math.abs(System.currentTimeMillis() - oiTime) < 120_000;
                    return new Snapshot(symbol, fresh && mark != null && index != null
                            && openInterest != null && funding != null ? "UP" : "STALE_OR_INVALID",
                            mark, index, openInterest, funding,
                            p.path("nextFundingTime").asLong(0),
                            "Public Binance USD-M market data; no liquidation model or trading signal",
                            Instant.now());
                })
                .onErrorResume(error -> Mono.just(new Snapshot(symbol, "UNAVAILABLE", null, null,
                        null, null, null, error.getClass().getSimpleName(), Instant.now())));
    }

    private static BigDecimal positive(String s) {
        BigDecimal value = numeric(s);
        return value != null && value.signum() > 0 ? value : null;
    }

    private static BigDecimal numeric(String s) {
        try { return new BigDecimal(s); }
        catch (NumberFormatException e) { return null; }
    }

    public record Snapshot(String symbol, String status, BigDecimal markPrice,
            BigDecimal indexPrice, BigDecimal openInterest, BigDecimal lastFundingRate,
            Long nextFundingTime, String details, Instant checkedAt) {}
}
