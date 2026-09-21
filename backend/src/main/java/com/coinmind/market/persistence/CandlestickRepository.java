package com.coinmind.market.persistence;

import com.coinmind.market.model.Candlestick;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@ConditionalOnProperty(
        prefix = "coinmind.persistence",
        name = "enabled",
        havingValue = "true"
)
public class CandlestickRepository {

    private final DatabaseClient databaseClient;

    public CandlestickRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Mono<Void> upsert(Candlestick candle) {
        return databaseClient.sql("""
                INSERT INTO market_candles (
                    symbol,
                    interval,
                    open_time,
                    close_time,
                    open_price,
                    high_price,
                    low_price,
                    close_price,
                    base_volume,
                    quote_volume,
                    trade_count,
                    closed,
                    event_time
                ) VALUES (
                    :symbol,
                    :interval,
                    :openTime,
                    :closeTime,
                    :open,
                    :high,
                    :low,
                    :close,
                    :baseVolume,
                    :quoteVolume,
                    :tradeCount,
                    :closed,
                    :eventTime
                )
                ON CONFLICT (symbol, interval, open_time)
                DO UPDATE SET
                    close_time = EXCLUDED.close_time,
                    open_price = EXCLUDED.open_price,
                    high_price = EXCLUDED.high_price,
                    low_price = EXCLUDED.low_price,
                    close_price = EXCLUDED.close_price,
                    base_volume = EXCLUDED.base_volume,
                    quote_volume = EXCLUDED.quote_volume,
                    trade_count = EXCLUDED.trade_count,
                    closed = EXCLUDED.closed,
                    event_time = EXCLUDED.event_time
                """)
                .bind("symbol", candle.symbol())
                .bind("interval", candle.interval())
                .bind("openTime", candle.openTime())
                .bind("closeTime", candle.closeTime())
                .bind("open", candle.open())
                .bind("high", candle.high())
                .bind("low", candle.low())
                .bind("close", candle.close())
                .bind("baseVolume", candle.baseVolume())
                .bind("quoteVolume", candle.quoteVolume())
                .bind("tradeCount", candle.tradeCount())
                .bind("closed", candle.closed())
                .bind("eventTime", candle.eventTime())
                .then();
    }
}
