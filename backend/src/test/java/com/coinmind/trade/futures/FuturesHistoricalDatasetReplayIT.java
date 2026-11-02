package com.coinmind.trade.futures;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Explicit operator-only historical replay from vetted local files.
 * Never writes database records, routes orders, or opens network connections.
 */
class FuturesHistoricalDatasetReplayIT {
    @Test
    @EnabledIfEnvironmentVariable(named="COINMIND_HISTORICAL_DATASET_DIR", matches=".+")
    void runChecksumVerifiedLocalHistoricalDataset() throws Exception {
        Path dir = Path.of(System.getenv("COINMIND_HISTORICAL_DATASET_DIR")).toRealPath();
        JsonNode manifest = new ObjectMapper().readTree(
                Files.readString(dir.resolve("manifest.json"), StandardCharsets.UTF_8));
        assertEquals("USD_M_FUTURES", manifest.path("market").asText());
        assertEquals("BTCUSDT", manifest.path("symbol").asText());
        assertEquals("HISTORICAL_REPLAY", manifest.path("provenance").asText());
        String month = manifest.path("month_utc").asText();
        assertTrue(month.matches("20[0-9]{2}-(0[1-9]|1[0-2])"), "Month required");
        String[] frames = {"5m", "15m", "1h", "4h"};
        JsonNode files = manifest.path("files");
        assertTrue(files.isArray());
        assertEquals(4, files.size());
        var csv = new ArrayList<String>();
        var hashes = new ArrayList<String>();
        for (int i = 0; i < 4; i++) {
            JsonNode entry = files.get(i);
            assertEquals(frames[i], entry.path("timeframe").asText(), "Wrong timeframe order");
            String filename = frames[i] + ".csv";
            assertEquals(filename, entry.path("file").asText(), "Unexpected file path");
            assertEquals("https://data.binance.vision/data/futures/um/monthly/klines/BTCUSDT/"
                    + frames[i] + "/BTCUSDT-" + frames[i] + "-" + month + ".zip",
                    entry.path("source_url").asText(), "Unexpected source metadata");
            Path file = dir.resolve(filename).toRealPath();
            assertTrue(file.startsWith(dir) && Files.isRegularFile(file), "Unsafe file path");
            assertTrue(Files.size(file) <= 24_000_000, "CSV too large");
            String data = Files.readString(file, StandardCharsets.UTF_8);
            csv.add(data);
            String expected = entry.path("sha256").asText();
            assertEquals(expected, FuturesOfflineVerifiedDataset.sha256(data), "Hash mismatch");
            hashes.add(expected);
            assertEquals(entry.path("candles").asInt(),
                    data.lines().count() - 1, "Manifest candle count mismatch");
        }
        var verified = new FuturesOfflineVerifiedDataset.Manifest(
                manifest.path("source").asText(), "USD_M_FUTURES", "BTCUSDT",
                "HISTORICAL_REPLAY", List.copyOf(hashes));
        var input = FuturesOfflineVerifiedDataset.read(verified, List.copyOf(csv),
                2, new BigDecimal("1"), new BigDecimal("2"), BigDecimal.ONE,
                new BigDecimal("0.05"), new BigDecimal("0.05"));
        Instant first = input.m5().getFirst().time();
        Instant lastExclusive = input.m5().getLast().time().plus(Duration.ofMinutes(5));
        assertEquals(month + "-01T00:00:00Z",
                first.toString(), "Historical month starts late");
        assertEquals(java.time.YearMonth.parse(month).plusMonths(1).atDay(1)
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), lastExclusive,
                "Historical month incomplete");
        var result = FuturesHistoricalWalkForwardRunner.runWithDynamicEquity(
                input, new FuturesHistoricalWalkForwardRunner.PaperSizing(
                        new BigDecimal("1000"), BigDecimal.ONE, new BigDecimal("3")),
                new BigDecimal("2"));
        assertEquals("OFFLINE_ONLY", result.mode());
        assertFalse(result.liveSignalEligible());
        assertEquals(result.executed(), result.equityCurve().size());
        BigDecimal reconciled = result.initialEquity();
        for (var point : result.equityCurve()) {
            reconciled = reconciled.add(point.netPnlUsdt());
            assertEquals(0, reconciled.compareTo(point.equityUsdt()));
        }
        assertEquals(0, reconciled.compareTo(result.finalEquity()));
        var stats = result.performance();
        System.out.println("COINMIND HISTORICAL OFFLINE REPORT month=" + month
                + " market=USD_M_FUTURES symbol=BTCUSDT"
                + " executed=" + result.executed()
                + " waits=" + result.waits()
                + " riskBlocked=" + result.riskBlocked()
                + " initialEquity=" + result.initialEquity()
                + " finalEquity=" + result.finalEquity()
                + " netPnl=" + result.finalEquity().subtract(result.initialEquity())
                + " maxDrawdownQuote=" + result.maxDrawdownQuote()
                + " wins=" + stats.wins() + " losses=" + stats.losses()
                + " winRatePercent=" + stats.winRatePercent()
                + " profitFactor=" + stats.profitFactor()
                + " DISCLAIMER=experimental_no_funding_liquidation_or_real_fills");
    }
}
