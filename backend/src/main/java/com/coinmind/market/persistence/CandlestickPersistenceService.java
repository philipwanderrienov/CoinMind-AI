package com.coinmind.market.persistence;

import com.coinmind.market.model.Candlestick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
        prefix = "coinmind.persistence",
        name = "enabled",
        havingValue = "true"
)
public class CandlestickPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(CandlestickPersistenceService.class);

    private final CandlestickRepository repository;

    public CandlestickPersistenceService(CandlestickRepository repository) {
        this.repository = repository;
    }

    public Mono<Void> persistIfClosed(Candlestick candle) {
        if (!candle.closed()) {
            return Mono.empty();
        }

        return repository.upsert(candle)
                .doOnSuccess(ignored -> log.debug(
                        "Closed candle persisted. symbol={}, interval={}, openTime={}",
                        candle.symbol(),
                        candle.interval(),
                        candle.openTime()
                ))
                .doOnError(error -> log.error(
                        "Failed to persist closed candle. symbol={}, interval={}, openTime={}",
                        candle.symbol(),
                        candle.interval(),
                        candle.openTime(),
                        error
                ));
    }
}
