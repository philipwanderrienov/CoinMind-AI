package com.coinmind.trade.futures;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * OFFLINE synthetic contract fixture for testing adapters and UI states.
 * Never exposes live signals, never calls CoinAPI and never places orders.
 * Fixture format is CoinMind-normalized, NOT a claim of exact CoinAPI wire compatibility.
 */
@RestController
@RequestMapping("/api/v1/dev/futures-fixtures")
public class CoinApiFixtureController {
    private static final Set<String> SYMBOLS = Set.of("BTCUSDT", "ETHUSDT", "SOLUSDT");
    private static final Set<String> SCENARIOS = Set.of("NORMAL", "STALE", "MISSING_OI", "DISCONNECTED");
    private final boolean enabled;

    public CoinApiFixtureController(
            @org.springframework.beans.factory.annotation.Value(
                "${coinmind.futures.fixtures-enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @GetMapping("/{symbol}")
    public Fixture get(@PathVariable String symbol,
            @RequestParam(defaultValue = "NORMAL") String scenario) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fixture endpoint disabled");
        if (!SYMBOLS.contains(symbol) || !SCENARIOS.contains(scenario)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported fixture request");
        }
        BigDecimal baseline = switch (symbol) {
            case "BTCUSDT" -> new BigDecimal("80000");
            case "ETHUSDT" -> new BigDecimal("3000");
            default -> new BigDecimal("150");
        };
        Instant observed = "STALE".equals(scenario)
                ? Instant.parse("2020-01-01T00:00:00Z") : Instant.now();
        return new Fixture(symbol, "COINAPI", "SYNTHETIC_FIXTURE", scenario,
                "DISCONNECTED".equals(scenario) ? "UNAVAILABLE"
                    : "STALE".equals(scenario) ? "STALE" : "MISSING_OI".equals(scenario)
                    ? "INCOMPLETE" : "SYNTHETIC",
                "DISCONNECTED".equals(scenario) ? null : baseline,
                "DISCONNECTED".equals(scenario) ? null : baseline.subtract(BigDecimal.ONE),
                "MISSING_OI".equals(scenario) || "DISCONNECTED".equals(scenario)
                    ? null : new BigDecimal("1200"),
                "DISCONNECTED".equals(scenario) ? null : new BigDecimal("0.0001"),
                observed, false, "Not real market data; never eligible for live trading signals");
    }

    public record Fixture(String symbol, String upstreamProvider, String provenance,
            String scenario, String status, BigDecimal markPrice, BigDecimal indexPrice,
            BigDecimal openInterest, BigDecimal fundingRate, Instant observedAt,
            boolean liveSignalEligible, String warning) {}
}
