package com.coinmind.trade.service;

import com.coinmind.ai.model.MarketContext;
import com.coinmind.ai.service.MarketContextBuilder;
import com.coinmind.trade.model.TradeSetup;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class TradeSetupService {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal STRONG_THRESHOLD = BigDecimal.valueOf(60);
    private static final BigDecimal WATCH_THRESHOLD = BigDecimal.valueOf(45);

    private final MarketContextBuilder contextBuilder;

    public TradeSetupService(MarketContextBuilder contextBuilder) {
        this.contextBuilder = contextBuilder;
    }

    public TradeSetup build(String symbol, String interval) {
        return build(contextBuilder.build(symbol, interval));
    }

    public TradeSetup build(MarketContext context) {
        BigDecimal score = context.signal().score();
        BigDecimal strength = score.abs();
        BigDecimal price = context.price().lastPrice();
        BigDecimal atr = safeAtr(context.technical().atr14(), price);

        String side = score.signum() > 0
                ? "LONG"
                : score.signum() < 0
                ? "SHORT"
                : "NONE";

        String action = action(context, strength, side);
        int confidence = confidence(context, strength);

        if ("WAIT".equals(action)) {
            return new TradeSetup(
                    context.symbol(),
                    context.interval(),
                    action,
                    "NONE",
                    confidence,
                    score,
                    context.signal().riskLevel(),
                    scale(price),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    reasons(context),
                    waitWarnings(context),
                    Instant.now()
            );
        }

        boolean longSide = "LONG".equals(side);
        BigDecimal entryLow;
        BigDecimal entryHigh;
        BigDecimal entryReference;
        BigDecimal riskDistance;

        if (longSide) {
            entryLow = price.subtract(atr.multiply(BigDecimal.valueOf(0.30), MC), MC);
            entryHigh = price.add(atr.multiply(BigDecimal.valueOf(0.10), MC), MC);
        } else {
            entryLow = price.subtract(atr.multiply(BigDecimal.valueOf(0.10), MC), MC);
            entryHigh = price.add(atr.multiply(BigDecimal.valueOf(0.30), MC), MC);
        }

        entryReference = entryLow.add(entryHigh, MC)
                .divide(BigDecimal.valueOf(2), MC);

        BigDecimal minimumRisk = price.multiply(BigDecimal.valueOf(0.0075), MC);
        riskDistance = atr.multiply(BigDecimal.valueOf(1.25), MC)
                .max(minimumRisk);

        BigDecimal invalidation;
        BigDecimal target1;
        BigDecimal target2;

        if (longSide) {
            invalidation = entryReference.subtract(riskDistance, MC);
            target1 = entryReference.add(riskDistance.multiply(BigDecimal.valueOf(1.5), MC), MC);
            target2 = entryReference.add(riskDistance.multiply(BigDecimal.valueOf(2.5), MC), MC);
        } else {
            invalidation = entryReference.add(riskDistance, MC);
            target1 = entryReference.subtract(riskDistance.multiply(BigDecimal.valueOf(1.5), MC), MC);
            target2 = entryReference.subtract(riskDistance.multiply(BigDecimal.valueOf(2.5), MC), MC);
        }

        return new TradeSetup(
                context.symbol(),
                context.interval(),
                action,
                side,
                confidence,
                score,
                context.signal().riskLevel(),
                scale(price),
                scale(entryLow),
                scale(entryHigh),
                scale(invalidation),
                scale(target1),
                scale(target2),
                BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(2.5),
                reasons(context),
                setupWarnings(context, action),
                Instant.now()
        );
    }

    private String action(
            MarketContext context,
            BigDecimal strength,
            String side
    ) {
        if ("NONE".equals(side) || strength.compareTo(WATCH_THRESHOLD) < 0) {
            return "WAIT";
        }

        boolean extremeRsi = context.technical().rsi14().compareTo(BigDecimal.valueOf(75)) > 0
                || context.technical().rsi14().compareTo(BigDecimal.valueOf(25)) < 0;

        boolean highRisk = "HIGH".equals(context.signal().riskLevel());

        if (strength.compareTo(STRONG_THRESHOLD) >= 0 && !extremeRsi && !highRisk) {
            return "LONG".equals(side) ? "BUY" : "SELL";
        }

        return "LONG".equals(side) ? "WATCH_BUY" : "WATCH_SELL";
    }

    private int confidence(MarketContext context, BigDecimal strength) {
        int value = 40 + strength.divide(BigDecimal.valueOf(2), MC).intValue();

        if (context.technical().volumeRatio().compareTo(BigDecimal.valueOf(1.2)) >= 0) {
            value += 5;
        }

        if (context.news().articleCount() > 0
                && context.news().averageSentiment().signum() == context.signal().score().signum()) {
            value += 5;
        }

        if ("HIGH".equals(context.signal().riskLevel())) {
            value -= 10;
        }

        return Math.max(25, Math.min(95, value));
    }

    private List<String> reasons(MarketContext context) {
        List<String> reasons = new ArrayList<>(context.signal().reasons());

        if (context.technical().volumeRatio().compareTo(BigDecimal.valueOf(1.2)) >= 0) {
            reasons.add("Volume is above its recent baseline");
        }

        if (context.microstructure().buySellRatio().compareTo(BigDecimal.ONE) > 0) {
            reasons.add("Realtime order flow favors buyers");
        } else if (context.microstructure().buySellRatio().compareTo(BigDecimal.ONE) < 0) {
            reasons.add("Realtime order flow favors sellers");
        }

        return reasons.stream().limit(6).toList();
    }

    private List<String> setupWarnings(MarketContext context, String action) {
        List<String> warnings = new ArrayList<>();

        if (action.startsWith("WATCH")) {
            warnings.add("Signal is not strong enough for an active setup yet");
        }

        if ("HIGH".equals(context.signal().riskLevel())) {
            warnings.add("Volatility is elevated");
        }

        if (context.technical().rsi14().compareTo(BigDecimal.valueOf(70)) > 0) {
            warnings.add("RSI is elevated; avoid chasing price");
        } else if (context.technical().rsi14().compareTo(BigDecimal.valueOf(30)) < 0) {
            warnings.add("RSI is depressed; reversal risk is elevated");
        }

        warnings.add("Levels are deterministic decision-support outputs, not execution instructions");
        return List.copyOf(warnings);
    }

    private List<String> waitWarnings(MarketContext context) {
        List<String> warnings = new ArrayList<>();
        warnings.add("No directional setup currently meets the minimum signal threshold");

        if ("HIGH".equals(context.signal().riskLevel())) {
            warnings.add("Volatility is elevated");
        }

        return List.copyOf(warnings);
    }

    private BigDecimal safeAtr(BigDecimal atr, BigDecimal price) {
        BigDecimal fallback = price.multiply(BigDecimal.valueOf(0.005), MC);

        if (atr == null || atr.signum() <= 0) {
            return fallback;
        }

        return atr.max(fallback);
    }

    private BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return null;
        }

        return value.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
    }
}
