package com.coinmind.news.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

@Service
public class NewsSentimentService {

    private static final Set<String> POSITIVE = Set.of(
            "surge", "rally", "gain", "gains", "bullish", "breakout",
            "adoption", "approval", "approved", "growth", "record",
            "partnership", "launch", "upgrade", "inflow", "inflows",
            "recovery", "rebound", "optimism"
    );

    private static final Set<String> NEGATIVE = Set.of(
            "drop", "fall", "falls", "crash", "bearish", "selloff",
            "hack", "hacked", "exploit", "lawsuit", "ban", "banned",
            "outflow", "outflows", "liquidation", "liquidations",
            "fraud", "decline", "risk", "warning"
    );

    public BigDecimal score(String title, String summary) {
        String text = ((title == null ? "" : title) + " " +
                (summary == null ? "" : summary))
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ");

        int positive = 0;
        int negative = 0;

        for (String token : text.split("\\s+")) {
            if (POSITIVE.contains(token)) {
                positive++;
            }
            if (NEGATIVE.contains(token)) {
                negative++;
            }
        }

        int total = positive + negative;
        if (total == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(positive - negative)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }
}
