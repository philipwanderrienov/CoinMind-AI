package com.coinmind.news.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class NewsSentimentService {

    private static final Map<String, BigDecimal> PHRASES = new LinkedHashMap<>();
    private static final Set<String> POSITIVE = Set.of(
            "surge", "surges", "rally", "rallies", "gain", "gains", "bullish", "breakout",
            "adoption", "approval", "approved", "growth", "record", "partnership", "launch",
            "upgrade", "inflow", "inflows", "recovery", "rebound", "optimism", "beats",
            "strong", "support", "expansion", "accumulation", "buying", "institutional"
    );
    private static final Set<String> NEGATIVE = Set.of(
            "drop", "drops", "fall", "falls", "crash", "bearish", "selloff", "hack", "hacked",
            "exploit", "lawsuit", "ban", "banned", "outflow", "outflows", "liquidation",
            "liquidations", "fraud", "decline", "risk", "warning", "breach", "breached",
            "stolen", "theft", "attack", "attacks", "probe", "investigation", "shutdown",
            "loss", "losses", "default", "rejection", "rejected"
    );

    static {
        PHRASES.put("all time high", BigDecimal.valueOf(0.9));
        PHRASES.put("record high", BigDecimal.valueOf(0.8));
        PHRASES.put("spot etf approval", BigDecimal.valueOf(0.9));
        PHRASES.put("institutional inflows", BigDecimal.valueOf(0.7));
        PHRASES.put("mass adoption", BigDecimal.valueOf(0.7));
        PHRASES.put("security breach", BigDecimal.valueOf(-0.9));
        PHRASES.put("private keys", BigDecimal.valueOf(-0.6));
        PHRASES.put("exchange hack", BigDecimal.valueOf(-1.0));
        PHRASES.put("regulatory crackdown", BigDecimal.valueOf(-0.8));
        PHRASES.put("illegal gambling", BigDecimal.valueOf(-0.7));
        PHRASES.put("liquidation cascade", BigDecimal.valueOf(-0.9));
    }

    public BigDecimal score(String title, String summary) {
        String normalized = normalize(title, summary);

        BigDecimal weighted = BigDecimal.ZERO;
        int matches = 0;

        for (var entry : PHRASES.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                weighted = weighted.add(entry.getValue());
                matches++;
            }
        }

        for (String token : normalized.split("\\s+")) {
            if (POSITIVE.contains(token)) {
                weighted = weighted.add(BigDecimal.valueOf(0.35));
                matches++;
            }
            if (NEGATIVE.contains(token)) {
                weighted = weighted.subtract(BigDecimal.valueOf(0.35));
                matches++;
            }
        }

        if (matches == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal normalizedScore = weighted
                .divide(BigDecimal.valueOf(Math.max(1, matches)), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(2.5));

        if (normalizedScore.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        }
        if (normalizedScore.compareTo(BigDecimal.ONE.negate()) < 0) {
            return BigDecimal.ONE.negate().setScale(4, RoundingMode.HALF_UP);
        }

        return normalizedScore.setScale(4, RoundingMode.HALF_UP);
    }

    private String normalize(String title, String summary) {
        return ((title == null ? "" : title) + " " + (summary == null ? "" : summary))
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9$ ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
