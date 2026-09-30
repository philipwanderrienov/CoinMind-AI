package com.coinmind.trade.service;

import com.coinmind.ai.model.AiNewsIntelligence;
import com.coinmind.ai.model.MarketContext;
import com.coinmind.ai.service.AiNewsIntelligenceService;
import com.coinmind.ai.service.MarketContextBuilder;
import com.coinmind.market.model.MarketActivityProfile;
import com.coinmind.market.service.MarketActivityProfileService;
import com.coinmind.trade.model.TradeSetup;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import reactor.core.publisher.Mono;

@Service
public class TradeSetupService {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal STRONG_THRESHOLD = BigDecimal.valueOf(60);
    private static final BigDecimal WATCH_THRESHOLD = BigDecimal.valueOf(45);

    private static final Map<String, Integer> TIMEFRAME_WEIGHTS = Map.of(
            "1m", 5,
            "15m", 15,
            "1h", 35,
            "4h", 35,
            "1d", 10
    );

    private static final List<String> DECISION_INTERVALS =
            List.of("1m", "15m", "1h", "4h", "1d");

    private final MarketContextBuilder contextBuilder;
    private final MarketActivityProfileService activityProfileService;
    private final AiNewsIntelligenceService aiNewsIntelligenceService;

    public TradeSetupService(
            MarketContextBuilder contextBuilder,
            MarketActivityProfileService activityProfileService,
            AiNewsIntelligenceService aiNewsIntelligenceService
    ) {
        this.contextBuilder = contextBuilder;
        this.activityProfileService = activityProfileService;
        this.aiNewsIntelligenceService = aiNewsIntelligenceService;
    }

    public Mono<TradeSetup> build(String symbol, String interval) {
        return activityProfileService.build(symbol)
                .map(activity -> build(
                        symbol,
                        interval,
                        activity,
                        aiNewsIntelligenceService.latestFresh(symbol)
                ));
    }

    private TradeSetup build(
            String symbol,
            String interval,
            MarketActivityProfile activity,
            AiNewsIntelligence newsIntelligence
    ) {
        Map<String, MarketContext> contexts = new LinkedHashMap<>();

        for (String decisionInterval : DECISION_INTERVALS) {
            contexts.put(
                    decisionInterval,
                    contextBuilder.build(symbol, decisionInterval)
            );
        }

        MarketContext anchor = contexts.get("1h");

        BigDecimal weightedScore = weightedSignalScore(contexts);
        String side = weightedScore.signum() > 0
                ? "LONG"
                : weightedScore.signum() < 0
                ? "SHORT"
                : "NONE";

        int alignmentScore = alignmentScore(contexts, weightedScore.signum());
        String alignmentLabel = alignmentLabel(alignmentScore);
        String regime = marketRegime(contexts);

        return buildDecision(
                anchor,
                interval,
                weightedScore,
                side,
                alignmentScore,
                alignmentLabel,
                regime,
                activity.currentActivity(),
                activity.currentScore(),
                newsIntelligence,
                timeframeSignals(contexts)
        );
    }

    public TradeSetup build(MarketContext context) {
        BigDecimal score = context.signal().score();
        int alignment = Math.min(100, score.abs().intValue());

        return buildDecision(
                context,
                context.interval(),
                score,
                score.signum() > 0 ? "LONG" : score.signum() < 0 ? "SHORT" : "NONE",
                alignment,
                alignmentLabel(alignment),
                singleContextRegime(context),
                "UNKNOWN",
                BigDecimal.ZERO,
                null,
                List.of(toTimeframeSignal(context, 100))
        );
    }

    private TradeSetup buildDecision(
            MarketContext context,
            String requestedInterval,
            BigDecimal score,
            String side,
            int alignmentScore,
            String alignmentLabel,
            String regime,
            String marketActivity,
            BigDecimal marketActivityScore,
            AiNewsIntelligence newsIntelligence,
            List<TradeSetup.TimeframeSignal> timeframes
    ) {
        BigDecimal strength = score.abs();
        BigDecimal price = context.price().lastPrice();
        BigDecimal atr = safeAtr(context.technical().atr14(), price);

        String action = action(
                context,
                strength,
                side,
                alignmentScore,
                regime,
                marketActivity,
                newsIntelligence
        );
        int confidence = confidence(
                context,
                strength,
                alignmentScore,
                regime,
                marketActivity,
                newsIntelligence,
                side
        );

        List<String> reasons = reasons(
                context,
                alignmentScore,
                alignmentLabel,
                regime,
                marketActivity,
                marketActivityScore,
                newsIntelligence,
                side,
                timeframes
        );
        List<String> warnings = "WAIT".equals(action)
                ? waitWarnings(context, alignmentScore, regime, marketActivity, newsIntelligence, side)
                : setupWarnings(context, action, alignmentScore, regime, marketActivity, newsIntelligence, side);

        if ("WAIT".equals(action)) {
            return new TradeSetup(
                    context.symbol(),
                    requestedInterval,
                    action,
                    "NONE",
                    confidence,
                    scale(score),
                    context.signal().riskLevel(),
                    alignmentScore,
                    alignmentLabel,
                    regime,
                    marketActivity,
                    scale(marketActivityScore),
                    timeframes,
                    scale(price),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    reasons,
                    warnings,
                    Instant.now()
            );
        }

        boolean longSide = "LONG".equals(side);
        BigDecimal entryLow;
        BigDecimal entryHigh;

        if (longSide) {
            entryLow = price.subtract(atr.multiply(BigDecimal.valueOf(0.30), MC), MC);
            entryHigh = price.add(atr.multiply(BigDecimal.valueOf(0.10), MC), MC);
        } else {
            entryLow = price.subtract(atr.multiply(BigDecimal.valueOf(0.10), MC), MC);
            entryHigh = price.add(atr.multiply(BigDecimal.valueOf(0.30), MC), MC);
        }

        BigDecimal entryReference = entryLow.add(entryHigh, MC)
                .divide(BigDecimal.valueOf(2), MC);

        BigDecimal minimumRisk = price.multiply(BigDecimal.valueOf(0.0075), MC);
        BigDecimal riskDistance = atr.multiply(BigDecimal.valueOf(1.25), MC)
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
                requestedInterval,
                action,
                side,
                confidence,
                scale(score),
                context.signal().riskLevel(),
                alignmentScore,
                alignmentLabel,
                regime,
                marketActivity,
                scale(marketActivityScore),
                timeframes,
                scale(price),
                scale(entryLow),
                scale(entryHigh),
                scale(invalidation),
                scale(target1),
                scale(target2),
                BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(2.5),
                reasons,
                warnings,
                Instant.now()
        );
    }

    private BigDecimal weightedSignalScore(Map<String, MarketContext> contexts) {
        BigDecimal total = BigDecimal.ZERO;

        for (String interval : DECISION_INTERVALS) {
            MarketContext context = contexts.get(interval);
            int weight = TIMEFRAME_WEIGHTS.get(interval);

            total = total.add(
                    context.signal().score()
                            .multiply(BigDecimal.valueOf(weight), MC)
                            .divide(BigDecimal.valueOf(100), MC),
                    MC
            );
        }

        return total;
    }

    private int alignmentScore(
            Map<String, MarketContext> contexts,
            int finalDirection
    ) {
        if (finalDirection == 0) {
            return 0;
        }

        int alignedWeight = 0;
        int opposingWeight = 0;

        for (String interval : DECISION_INTERVALS) {
            int sign = contexts.get(interval).signal().score().signum();
            int weight = TIMEFRAME_WEIGHTS.get(interval);

            if (sign == finalDirection) {
                alignedWeight += weight;
            } else if (sign == -finalDirection) {
                opposingWeight += weight;
            }
        }

        return Math.max(0, Math.min(100, alignedWeight - (opposingWeight / 2)));
    }

    private String alignmentLabel(int alignmentScore) {
        if (alignmentScore >= 80) return "STRONG";
        if (alignmentScore >= 60) return "GOOD";
        if (alignmentScore >= 40) return "MIXED";
        return "WEAK";
    }

    private String marketRegime(Map<String, MarketContext> contexts) {
        MarketContext h1 = contexts.get("1h");
        MarketContext h4 = contexts.get("4h");

        BigDecimal volatility = h1.technical().volatilityScore()
                .multiply(BigDecimal.valueOf(0.5), MC)
                .add(
                        h4.technical().volatilityScore()
                                .multiply(BigDecimal.valueOf(0.5), MC),
                        MC
                );

        if (volatility.compareTo(BigDecimal.valueOf(70)) >= 0) {
            return "HIGH_VOLATILITY";
        }

        BigDecimal h1Trend = h1.technical().trendScore();
        BigDecimal h4Trend = h4.technical().trendScore();

        if (h1Trend.compareTo(BigDecimal.valueOf(20)) > 0
                && h4Trend.compareTo(BigDecimal.valueOf(20)) > 0) {
            return "TRENDING_BULLISH";
        }

        if (h1Trend.compareTo(BigDecimal.valueOf(-20)) < 0
                && h4Trend.compareTo(BigDecimal.valueOf(-20)) < 0) {
            return "TRENDING_BEARISH";
        }

        if (h1Trend.abs().compareTo(BigDecimal.valueOf(25)) < 0
                && h4Trend.abs().compareTo(BigDecimal.valueOf(25)) < 0) {
            return "SIDEWAYS";
        }

        return "MIXED";
    }

    private String singleContextRegime(MarketContext context) {
        if (context.technical().volatilityScore().compareTo(BigDecimal.valueOf(70)) >= 0) {
            return "HIGH_VOLATILITY";
        }
        if (context.technical().trendScore().compareTo(BigDecimal.valueOf(20)) > 0) {
            return "TRENDING_BULLISH";
        }
        if (context.technical().trendScore().compareTo(BigDecimal.valueOf(-20)) < 0) {
            return "TRENDING_BEARISH";
        }
        return "SIDEWAYS";
    }

    private List<TradeSetup.TimeframeSignal> timeframeSignals(
            Map<String, MarketContext> contexts
    ) {
        return DECISION_INTERVALS.stream()
                .map(interval -> toTimeframeSignal(
                        contexts.get(interval),
                        TIMEFRAME_WEIGHTS.get(interval)
                ))
                .toList();
    }

    private TradeSetup.TimeframeSignal toTimeframeSignal(
            MarketContext context,
            int weight
    ) {
        return new TradeSetup.TimeframeSignal(
                context.interval(),
                weight,
                scale(context.signal().score()),
                scale(context.technical().trendScore()),
                scale(context.technical().momentumScore()),
                scale(context.technical().volatilityScore()),
                context.signal().bias()
        );
    }

    private String action(
            MarketContext context,
            BigDecimal strength,
            String side,
            int alignmentScore,
            String regime,
            String marketActivity,
            AiNewsIntelligence newsIntelligence
    ) {
        if ("NONE".equals(side)
                || strength.compareTo(WATCH_THRESHOLD) < 0
                || alignmentScore < 45) {
            return "WAIT";
        }

        boolean extremeRsi = context.technical().rsi14().compareTo(BigDecimal.valueOf(75)) > 0
                || context.technical().rsi14().compareTo(BigDecimal.valueOf(25)) < 0;
        boolean highRisk = "HIGH".equals(context.signal().riskLevel())
                || "HIGH_VOLATILITY".equals(regime);

        if (strength.compareTo(STRONG_THRESHOLD) >= 0
                && alignmentScore >= 70
                && !extremeRsi
                && !highRisk) {
            if ("LOW".equals(marketActivity)
                    || strongNewsContradiction(newsIntelligence, side)) {
                return "LONG".equals(side) ? "WATCH_BUY" : "WATCH_SELL";
            }
            return "LONG".equals(side) ? "BUY" : "SELL";
        }

        return "LONG".equals(side) ? "WATCH_BUY" : "WATCH_SELL";
    }

    private int confidence(
            MarketContext context,
            BigDecimal strength,
            int alignmentScore,
            String regime,
            String marketActivity,
            AiNewsIntelligence newsIntelligence,
            String side
    ) {
        int value = 30
                + strength.divide(BigDecimal.valueOf(2), MC).intValue()
                + (alignmentScore / 4);

        if (context.technical().volumeRatio().compareTo(BigDecimal.valueOf(1.2)) >= 0) {
            value += 5;
        }

        if (context.news().articleCount() > 0
                && context.news().averageSentiment().signum() == context.signal().score().signum()) {
            value += 5;
        }

        if ("HIGH".equals(context.signal().riskLevel())
                || "HIGH_VOLATILITY".equals(regime)) {
            value -= 10;
        }

        if ("SIDEWAYS".equals(regime)) {
            value -= 5;
        }

        if ("VERY_HIGH".equals(marketActivity) || "HIGH".equals(marketActivity)) {
            value += 5;
        } else if ("LOW".equals(marketActivity)) {
            value -= 8;
        }

        int newsAdjustment = newsConfidenceAdjustment(newsIntelligence, side);
        value += newsAdjustment;

        return Math.max(25, Math.min(95, value));
    }

    private List<String> reasons(
            MarketContext context,
            int alignmentScore,
            String alignmentLabel,
            String regime,
            String marketActivity,
            BigDecimal marketActivityScore,
            AiNewsIntelligence newsIntelligence,
            String side,
            List<TradeSetup.TimeframeSignal> timeframes
    ) {
        List<String> reasons = new ArrayList<>();
        reasons.add("Timeframe alignment " + alignmentLabel + " (" + alignmentScore + "%)");
        reasons.add("Market regime: " + regime.replace('_', ' ').toLowerCase());
        reasons.add(
                "Current market activity: "
                        + marketActivity.toLowerCase().replace('_', ' ')
                        + " (" + scale(marketActivityScore) + "/100)"
        );

        timeframes.stream()
                .filter(item -> "1h".equals(item.interval()) || "4h".equals(item.interval()))
                .forEach(item -> reasons.add(
                        item.interval() + " bias " + item.bias().toLowerCase()
                ));

        if (context.technical().volumeRatio().compareTo(BigDecimal.valueOf(1.2)) >= 0) {
            reasons.add("Volume is above its recent baseline");
        }

        if (newsIntelligence != null) {
            String relation = newsSupportsSide(newsIntelligence, side)
                    ? "supports"
                    : newsOpposesSide(newsIntelligence, side)
                    ? "opposes"
                    : "is neutral to";
            reasons.add(
                    "AI news intelligence " + relation + " the setup: "
                            + newsIntelligence.direction().toLowerCase()
                            + ", importance " + newsIntelligence.importance()
                            + "%, " + newsIntelligence.horizon().toLowerCase()
            );
        }

        return reasons.stream().limit(7).toList();
    }

    private List<String> setupWarnings(
            MarketContext context,
            String action,
            int alignmentScore,
            String regime,
            String marketActivity,
            AiNewsIntelligence newsIntelligence,
            String side
    ) {
        List<String> warnings = new ArrayList<>();

        if (action.startsWith("WATCH")) {
            warnings.add("Setup is forming but has not reached active-entry quality");
        }

        if (alignmentScore < 70) {
            warnings.add("Higher timeframes are not fully aligned");
        }

        if ("HIGH_VOLATILITY".equals(regime)) {
            warnings.add("Volatility is elevated; entry timing requires extra caution");
        } else if ("SIDEWAYS".equals(regime)) {
            warnings.add("Market is sideways; breakout confirmation is more important");
        }

        if ("LOW".equals(marketActivity)) {
            warnings.add("Current trading hour has low relative activity; wait for stronger participation before active entry");
        }

        if (strongNewsContradiction(newsIntelligence, side)) {
            warnings.add("High-importance AI news intelligence conflicts with the technical setup; active entry is downgraded to watch");
        }

        if (context.technical().rsi14().compareTo(BigDecimal.valueOf(70)) > 0) {
            warnings.add("RSI is elevated; avoid chasing price");
        } else if (context.technical().rsi14().compareTo(BigDecimal.valueOf(30)) < 0) {
            warnings.add("RSI is depressed; reversal risk is elevated");
        }

        return List.copyOf(warnings);
    }

    private List<String> waitWarnings(
            MarketContext context,
            int alignmentScore,
            String regime,
            String marketActivity,
            AiNewsIntelligence newsIntelligence,
            String side
    ) {
        List<String> warnings = new ArrayList<>();
        warnings.add("No multi-timeframe setup currently meets the minimum threshold");

        if (alignmentScore < 45) {
            warnings.add("Timeframes are conflicting or weakly aligned");
        }

        if ("HIGH_VOLATILITY".equals(regime)) {
            warnings.add("Volatility is elevated");
        }

        if ("LOW".equals(marketActivity)) {
            warnings.add("Current trading hour has low relative activity");
        }

        if (newsOpposesSide(newsIntelligence, side)) {
            warnings.add("AI news intelligence currently conflicts with the weighted market direction");
        }

        return List.copyOf(warnings);
    }

    private boolean strongNewsContradiction(
            AiNewsIntelligence intelligence,
            String side
    ) {
        return intelligence != null
                && intelligence.importance() >= 70
                && intelligence.confidence() >= 65
                && newsOpposesSide(intelligence, side);
    }

    private int newsConfidenceAdjustment(
            AiNewsIntelligence intelligence,
            String side
    ) {
        if (intelligence == null || intelligence.importance() < 40) {
            return 0;
        }

        if (newsSupportsSide(intelligence, side)) {
            return intelligence.importance() >= 70
                    && intelligence.confidence() >= 65
                    ? 4
                    : 2;
        }

        if (newsOpposesSide(intelligence, side)) {
            return intelligence.importance() >= 70
                    && intelligence.confidence() >= 65
                    ? -6
                    : -3;
        }

        return 0;
    }

    private boolean newsSupportsSide(
            AiNewsIntelligence intelligence,
            String side
    ) {
        if (intelligence == null) {
            return false;
        }

        return ("LONG".equals(side) && "BULLISH".equals(intelligence.direction()))
                || ("SHORT".equals(side) && "BEARISH".equals(intelligence.direction()));
    }

    private boolean newsOpposesSide(
            AiNewsIntelligence intelligence,
            String side
    ) {
        if (intelligence == null) {
            return false;
        }

        return ("LONG".equals(side) && "BEARISH".equals(intelligence.direction()))
                || ("SHORT".equals(side) && "BULLISH".equals(intelligence.direction()));
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
