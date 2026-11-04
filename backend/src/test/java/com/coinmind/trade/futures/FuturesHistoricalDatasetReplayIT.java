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
        assertEquals(result.executed(), result.trades().size());
        for (var trade : result.trades()) {
            assertNotNull(trade.side());
            assertNotNull(trade.entryReason());
            assertNotNull(trade.exitReason());
            System.out.println("COINMIND HISTORICAL TRADE"
                    + " decisionUtc=" + trade.decisionAt()
                    + " executionWindowOpenUtc=" + trade.executionWindowOpen()
                    + " settledUtc=" + trade.settledAt()
                    + " side=" + trade.side()
                    + " entryReason=" + trade.entryReason()
                    + " exitReason=" + trade.exitReason()
                    + " entryFill=" + trade.entryFill()
                    + " exitFill=" + trade.exitFill()
                    + " grossPnl=" + trade.grossPnl()
                    + " fees=" + trade.tradingFees()
                    + " netPnl=" + trade.netPnl());
        }
        for (var group : FuturesBacktestDiagnostics.bySide(result))
            System.out.println("COINMIND HISTORICAL GROUP side=" + group.category()
                    + " count=" + group.count() + " wins=" + group.wins()
                    + " losses=" + group.losses() + " gross=" + group.grossPnl()
                    + " fees=" + group.fees() + " net=" + group.netPnl()
                    + " averageNet=" + group.averageNetPnl());
        for (var group : FuturesBacktestDiagnostics.byExit(result))
            System.out.println("COINMIND HISTORICAL GROUP exit=" + group.category()
                    + " count=" + group.count() + " wins=" + group.wins()
                    + " losses=" + group.losses() + " gross=" + group.grossPnl()
                    + " fees=" + group.fees() + " net=" + group.netPnl()
                    + " averageNet=" + group.averageNetPnl());
        // Fixed research grid; parameter search is descriptive, not production selection.
        for (int horizon : new int[]{2,3,6,12}) {
            var scenario = new FuturesHistoricalWalkForwardRunner.Input(input.provenance(),
                    input.m5(), input.m15(), input.h1(), input.h4(), horizon,
                    input.stopPercent(), input.targetPercent(), input.quantity(),
                    input.feePercentPerSide(), input.slippagePercentPerSide());
            for (var direction : FuturesHistoricalWalkForwardRunner.ExperimentalDirection.values()) {
                var alternative = FuturesHistoricalWalkForwardRunner.runWithDynamicEquityExperimental(
                        scenario, new FuturesHistoricalWalkForwardRunner.PaperSizing(
                                new BigDecimal("1000"), BigDecimal.ONE, new BigDecimal("3")),
                        new BigDecimal("2"), direction);
                var summary = alternative.performance();
                assertEquals(alternative.executed(), alternative.trades().size());
                assertEquals(alternative.executed(), alternative.equityCurve().size());
                if (direction == FuturesHistoricalWalkForwardRunner.ExperimentalDirection.LONG_ONLY)
                    assertTrue(alternative.trades().stream().allMatch(t -> "LONG".equals(t.side())));
                if (horizon == 2 && direction == FuturesHistoricalWalkForwardRunner.ExperimentalDirection.BOTH)
                    assertEquals(result, alternative, "Baseline must remain identical");
                if (horizon == 2 && direction == FuturesHistoricalWalkForwardRunner.ExperimentalDirection.BOTH
                        || horizon == 12) {
                    var observations = FuturesMarketRegimeDiagnostics.observe(scenario, alternative);
                    var regimeGroups = FuturesMarketRegimeDiagnostics.byVolatility(observations);
                    assertEquals(alternative.executed(), observations.size());
                    BigDecimal observationNet = BigDecimal.ZERO;
                    BigDecimal observationFees = BigDecimal.ZERO;
                    for (var observation : observations) {
                        observationNet = observationNet.add(observation.netPnl());
                        observationFees = observationFees.add(observation.tradingFees());
                        System.out.println("COINMIND ENTRY DIAGNOSTIC month=" + month
                                + " direction=" + direction + " bars=" + horizon
                                + " decisionUtc=" + observation.decisionAt()
                                + " side=" + observation.side()
                                + " volatility=" + observation.volatilityBand()
                                + " rangePercent=" + observation.lastThreeFiveMinuteRangePercent()
                                + " momentum5m=" + observation.fiveMinuteMomentum()
                                + " momentum15m=" + observation.fifteenMinuteMomentum()
                                + " momentum1h=" + observation.oneHourMomentum()
                                + " momentum4h=" + observation.fourHourMomentum()
                                + " strength=" + observation.entryStrength()
                                + " aligned=" + observation.alignedTimeframes()
                                + " decelerating=" + observation.fiveMinuteMomentumDecelerating()
                                + " gross=" + observation.grossPnl()
                                + " fees=" + observation.tradingFees()
                                + " net=" + observation.netPnl()
                                + " DISCLAIMER=descriptive_no_trade_gate");
                    }
                    assertEquals(0, alternative.finalEquity().subtract(alternative.initialEquity())
                            .compareTo(observationNet), "Entry diagnostic net must reconcile");
                    assertTrue(observationFees.signum() >= 0);
                    for (var group : regimeGroups) {
                        System.out.println("COINMIND REGIME DIAGNOSTIC month=" + month
                                + " direction=" + direction + " bars=" + horizon
                                + " volatility=" + group.volatilityBand()
                                + " count=" + group.count() + " wins=" + group.wins()
                                + " losses=" + group.losses() + " netPnl=" + group.netPnl()
                                + " averageNet=" + group.averageNetPnl()
                                + " DISCLAIMER=descriptive_not_predictive");
                    }
                }
                System.out.println("COINMIND EXPERIMENTAL SCENARIO month=" + month
                        + " direction=" + direction + " bars=" + horizon
                        + " executed=" + alternative.executed()
                        + " wins=" + summary.wins() + " losses=" + summary.losses()
                        + " winRate=" + summary.winRatePercent()
                        + " profitFactor=" + summary.profitFactor()
                        + " netPnl=" + alternative.finalEquity().subtract(alternative.initialEquity())
                        + " maxDrawdownQuote=" + alternative.maxDrawdownQuote()
                        + " DISCLAIMER=in_sample_exploratory_only");
            }
        }
        // Optional checksum-gated offline volume research. Legacy OHLC datasets
        // produce identical historical output and never require a volume sidecar.
        if (manifest.has("volume_5m")) {
            var meta = manifest.path("volume_5m");
            assertEquals("BINANCE_UM_5M_VOLUME_V1", meta.path("schema").asText());
            assertEquals("volume-5m.csv", meta.path("file").asText());
            assertEquals("https://data.binance.vision/data/futures/um/monthly/klines/BTCUSDT/5m/"
                    + "BTCUSDT-5m-" + month + ".zip", meta.path("source_url").asText());
            Path volumeFile = dir.resolve("volume-5m.csv").toRealPath();
            assertTrue(volumeFile.startsWith(dir) && Files.isRegularFile(volumeFile));
            assertTrue(Files.size(volumeFile) <= 24_000_000);
            String volumeCsv = Files.readString(volumeFile, StandardCharsets.UTF_8);
            assertEquals(meta.path("sha256").asText(), FuturesOfflineVerifiedDataset.sha256(volumeCsv));
            var volumeBars = FuturesOfflineVolumeCsv.parse(volumeCsv, input.m5());
            assertEquals(meta.path("candles").asInt(), volumeBars.size());
            var volumeGate = new FuturesOfflineVolumeConfirmation(volumeBars, input.m5());
            for (var candidate : List.of(
                    new String[]{"BOTH", "2"}, new String[]{"BOTH", "12"},
                    new String[]{"LONG_ONLY", "12"})) {
                var direction = FuturesHistoricalWalkForwardRunner.ExperimentalDirection
                        .valueOf(candidate[0]);
                int bars = Integer.parseInt(candidate[1]);
                var scenario = new FuturesHistoricalWalkForwardRunner.Input(input.provenance(),
                        input.m5(), input.m15(), input.h1(), input.h4(), bars,
                        input.stopPercent(), input.targetPercent(), input.quantity(),
                        input.feePercentPerSide(), input.slippagePercentPerSide());
                var sizing = new FuturesHistoricalWalkForwardRunner.PaperSizing(
                        new BigDecimal("1000"), BigDecimal.ONE, new BigDecimal("3"));
                var baseline = FuturesHistoricalWalkForwardRunner.runWithDynamicEquityExperimental(
                        scenario, sizing, new BigDecimal("2"), direction);
                var gated = FuturesHistoricalWalkForwardRunner.runWithDynamicEquityVolumeResearch(
                        scenario, sizing, new BigDecimal("2"), direction, volumeGate);
                assertFalse(gated.liveSignalEligible());
                assertEquals("OFFLINE_ONLY", gated.mode());
                assertEquals(gated.executed(), gated.trades().size());
                assertEquals(gated.executed(), gated.equityCurve().size());
                for (var trade : gated.trades()) {
                    int signalIndex = (int) (Duration.between(
                            input.m5().getFirst().time(), trade.decisionAt()).toMinutes() / 5) - 1;
                    assertTrue(volumeGate.permits(signalIndex, trade.decisionAt(),
                            trade.side(), input.m5()), "Every executed gated trade must qualify");
                }
                BigDecimal total = gated.trades().stream().map(
                        FuturesHistoricalWalkForwardRunner.TradeDetail::netPnl)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                assertEquals(0, gated.finalEquity().subtract(gated.initialEquity())
                        .compareTo(total));
                System.out.println("COINMIND VOLUME REPLAY month=" + month
                        + " direction=" + direction + " bars=" + bars
                        + " baselineTrades=" + baseline.executed()
                        + " baselineNet=" + baseline.finalEquity().subtract(baseline.initialEquity())
                        + " gatedTrades=" + gated.executed()
                        + " gatedNet=" + gated.finalEquity().subtract(gated.initialEquity())
                        + " gatedDrawdown=" + gated.maxDrawdownQuote()
                        + " gatedFees=" + gated.trades().stream()
                                .map(FuturesHistoricalWalkForwardRunner.TradeDetail::tradingFees)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                        + " DISCLAIMER=retrospective_replayed_not_prospective");
            }
        }
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
