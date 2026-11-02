package com.coinmind.trade.futures;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Integrity-gated ingestion of operator-supplied CSV content.
 * SHA-256 proves byte consistency against a vetted manifest, NOT exchange authenticity.
 * No network calls, remote paths, disk access, or live-trading API.
 */
public final class FuturesOfflineVerifiedDataset {
    private static final int MAX_CSV_CHARS = 24_000_000;
    private FuturesOfflineVerifiedDataset() {}

    public record Manifest(String source, String market, String symbol, String provenance,
                           List<String> sha256) {
        public Manifest {
            if (source == null || source.isBlank() || source.length() > 128
                    || market == null || !List.of("SPOT", "USD_M_FUTURES").contains(market)
                    || symbol == null || !symbol.matches("[A-Z0-9]{3,24}")
                    || !"HISTORICAL_REPLAY".equals(provenance)
                    || sha256 == null || sha256.size() != 4
                    || sha256.stream().anyMatch(s -> s == null || !s.matches("[0-9a-f]{64}")))
                throw new IllegalArgumentException("Complete verified historical manifest required");
            sha256 = List.copyOf(sha256);
        }
    }

    /** The manifest's source and market must be checked against original provider metadata externally. */
    public static FuturesHistoricalWalkForwardRunner.Input read(
            Manifest manifest, List<String> csv,
            int horizonBars, BigDecimal stopPercent, BigDecimal targetPercent,
            BigDecimal quantity, BigDecimal feePercentPerSide,
            BigDecimal slippagePercentPerSide) throws IOException {
        if (manifest == null || csv == null || csv.size() != 4)
            throw new IllegalArgumentException("Four vetted historical CSV streams required");
        for (int i = 0; i < 4; i++) {
            var data = csv.get(i);
            if (data == null || data.isEmpty() || data.length() > MAX_CSV_CHARS
                    || !sha256(data).equals(manifest.sha256().get(i)))
                throw new IllegalArgumentException("CSV integrity mismatch at timeframe index " + i);
        }
        return FuturesOfflineCsvBundle.read(manifest.provenance(),
                new StringReader(csv.get(0)), new StringReader(csv.get(1)),
                new StringReader(csv.get(2)), new StringReader(csv.get(3)),
                horizonBars, stopPercent, targetPercent, quantity,
                feePercentPerSide, slippagePercentPerSide);
    }

    public static String sha256(String csv) {
        if (csv == null || csv.length() > MAX_CSV_CHARS)
            throw new IllegalArgumentException("Bounded CSV required");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(csv.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
