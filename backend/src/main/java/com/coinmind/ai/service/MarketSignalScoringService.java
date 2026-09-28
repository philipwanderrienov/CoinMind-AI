package com.coinmind.ai.service;

import com.coinmind.ai.model.MarketContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class MarketSignalScoringService {

    public MarketContext.SignalContext score(
            MarketContext.TechnicalContext technical,
            MarketContext.MicrostructureContext microstructure,
            MarketContext.NewsContext news,
            BigDecimal minimumTriggerScore
    ) {
        BigDecimal trend = clamp(technical.trendScore());
        BigDecimal momentum = clamp(technical.momentumScore());
        BigDecimal orderFlow = orderFlowScore(microstructure.buySellRatio());
        BigDecimal newsScore = clamp(news.averageSentiment().multiply(BigDecimal.valueOf(100)));

        BigDecimal score = trend.multiply(BigDecimal.valueOf(0.40))
                .add(momentum.multiply(BigDecimal.valueOf(0.25)))
                .add(orderFlow.multiply(BigDecimal.valueOf(0.20)))
                .add(newsScore.multiply(BigDecimal.valueOf(0.15)))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal strength = score.abs().setScale(2, RoundingMode.HALF_UP);
        String bias = score.compareTo(BigDecimal.valueOf(15)) > 0
                ? "BULLISH"
                : score.compareTo(BigDecimal.valueOf(-15)) < 0
                ? "BEARISH"
                : "NEUTRAL";

        String risk = technical.volatilityScore().compareTo(BigDecimal.valueOf(70)) >= 0
                ? "HIGH"
                : technical.volatilityScore().compareTo(BigDecimal.valueOf(35)) >= 0
                ? "MEDIUM"
                : "LOW";

        List<String> reasons = new ArrayList<>();
        reasons.add("Trend " + signed(trend));
        reasons.add("Momentum " + signed(momentum));
        reasons.add("Order flow " + signed(orderFlow));

        if (news.articleCount() > 0) {
            reasons.add("News " + signed(newsScore));
        }

        return new MarketContext.SignalContext(
                score,
                bias,
                strength,
                strength.compareTo(minimumTriggerScore) >= 0,
                risk,
                List.copyOf(reasons)
        );
    }

    private BigDecimal orderFlowScore(BigDecimal ratio) {
        if (ratio == null || ratio.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        return clamp(
                ratio.subtract(BigDecimal.ONE)
                        .multiply(BigDecimal.valueOf(100))
        );
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }

        return value.max(BigDecimal.valueOf(-100))
                .min(BigDecimal.valueOf(100));
    }

    private String signed(BigDecimal value) {
        String prefix = value.signum() > 0 ? "+" : "";
        return prefix + value.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
