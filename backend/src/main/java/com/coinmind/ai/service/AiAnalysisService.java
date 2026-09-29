package com.coinmind.ai.service;

import com.coinmind.ai.model.AiAnalysisResult;
import com.coinmind.ai.model.AiDecisionReview;
import com.coinmind.ai.persistence.AiAnalysisRepository;
import com.coinmind.ai.provider.AiProvider;
import com.coinmind.trade.service.TradeSetupService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AiAnalysisService {

    private final MarketContextBuilder contextBuilder;
    private final AiProvider aiProvider;
    private final ObjectProvider<AiAnalysisRepository> repositoryProvider;
    private final TradeSetupService tradeSetupService;

    public AiAnalysisService(
            MarketContextBuilder contextBuilder,
            AiProvider aiProvider,
            ObjectProvider<AiAnalysisRepository> repositoryProvider,
            TradeSetupService tradeSetupService
    ) {
        this.contextBuilder = contextBuilder;
        this.aiProvider = aiProvider;
        this.repositoryProvider = repositoryProvider;
        this.tradeSetupService = tradeSetupService;
    }

    public Mono<AiDecisionReview> reviewDecision(String symbol) {
        return Mono.fromSupplier(() -> tradeSetupService.build(symbol, "1h"))
                .flatMap(setup ->
                        Mono.fromSupplier(() -> contextBuilder.build(symbol, "1h"))
                                .flatMap(context -> aiProvider.reviewDecision(setup, context))
                );
    }

    public Mono<AiAnalysisResult> analyze(String symbol, String interval) {
        return analyze(symbol, interval, "MANUAL");
    }

    public Mono<AiAnalysisResult> latest(String symbol, String interval) {
        AiAnalysisRepository repository = repositoryProvider.getIfAvailable();

        if (repository == null) {
            return Mono.empty();
        }

        return repository.findLatest(symbol, interval);
    }

    public Mono<AiAnalysisResult> analyze(
            String symbol,
            String interval,
            String triggerType
    ) {
        return Mono.fromSupplier(() -> contextBuilder.build(symbol, interval))
                .flatMap(context -> analyze(context, triggerType));
    }

    public Mono<AiAnalysisResult> analyze(
            com.coinmind.ai.model.MarketContext context,
            String triggerType
    ) {
        return aiProvider.analyze(context, triggerType)
                .doOnNext(result ->
                        repositoryProvider.ifAvailable(repository ->
                                repository.insert(
                                        result,
                                        triggerType,
                                        context.price().lastPrice(),
                                        context.signal().score()
                                ).subscribe()
                        )
                );
    }
}
