import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Subscription, retry, timer } from 'rxjs';
import {
  AiAnalysisResult,
  Candlestick,
  ConnectionState,
  MarketContext,
  MarketMicrostructure,
  OrderBookSnapshot,
  TechnicalIndicators,
  TickerSnapshot
} from './core/models/market.models';
import { MarketApiService } from './core/services/market-api.service';
import { MarketChartComponent } from './features/market-chart/market-chart.component';
import { formatCompact, formatPrice } from './shared/price-format';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, MarketChartComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, OnDestroy {
  private readonly marketApi = inject(MarketApiService);
  private readonly subscriptions = new Subscription();

  readonly symbols = ['BTCUSDT', 'ETHUSDT', 'SOLUSDT'];
  readonly intervals = ['1m', '5m', '15m', '1h', '4h', '1d'];

  readonly selectedSymbol = signal('BTCUSDT');
  readonly selectedInterval = signal('1m');
  readonly connectionState = signal<ConnectionState>('connecting');
  readonly tickers = signal<Record<string, TickerSnapshot>>({});
  readonly candles = signal<Candlestick[]>([]);
  readonly lastMarketEventAt = signal<Date | null>(null);
  readonly historyLoading = signal(false);
  readonly orderBooks = signal<Record<string, OrderBookSnapshot>>({});
  readonly microstructure = signal<MarketMicrostructure | null>(null);
  readonly indicators = signal<TechnicalIndicators | null>(null);
  readonly marketContext = signal<MarketContext | null>(null);
  readonly aiAnalysis = signal<AiAnalysisResult | null>(null);

  readonly selectedTicker = computed(() => this.tickers()[this.selectedSymbol()] ?? null);

  readonly selectedCandles = computed(() =>
    this.candles()
      .filter(candle =>
        candle.symbol === this.selectedSymbol() &&
        candle.interval === this.selectedInterval()
      )
      .sort((a, b) => new Date(a.openTime).getTime() - new Date(b.openTime).getTime())
  );

  readonly selectedOrderBook = computed(() =>
    this.orderBooks()[this.selectedSymbol()] ?? null
  );

  readonly currentCandle = computed(() => {
    const values = this.selectedCandles();
    return values.length ? values[values.length - 1] : null;
  });

  readonly dailyChange = computed(() => {
    const ticker = this.selectedTicker();
    if (!ticker || ticker.openPrice === 0) {
      return null;
    }
    return ((ticker.closePrice - ticker.openPrice) / ticker.openPrice) * 100;
  });

  ngOnInit(): void {
    this.loadSnapshots();
    this.loadHistory();
    this.loadMicrostructure();
    this.loadIndicators();
    this.loadMarketContext();
    this.loadIndicators();
    this.loadMarketContext();
    this.connectRealtime();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  selectSymbol(symbol: string): void {
    this.selectedSymbol.set(symbol);
    this.loadHistory();
    this.loadMicrostructure();
  }

  selectInterval(interval: string): void {
    this.selectedInterval.set(interval);
    this.loadHistory();
    this.loadIndicators();
    this.loadMarketContext();
  }

  formatPrice(value: number | null | undefined): string {
    return formatPrice(value);
  }

  formatCompact(value: number | null | undefined): string {
    return formatCompact(value);
  }

  symbolLabel(symbol: string): string {
    return symbol.replace('USDT', '');
  }

  changeClass(value: number | null): string {
    if (value == null || value === 0) {
      return 'neutral';
    }
    return value > 0 ? 'positive' : 'negative';
  }

  private loadSnapshots(): void {
    this.subscriptions.add(
      this.marketApi.getTickers().subscribe({
        next: tickers => {
          this.tickers.set(
            Object.fromEntries(tickers.map(ticker => [ticker.symbol, ticker]))
          );
        }
      })
    );
  }

  private loadHistory(): void {
    const symbol = this.selectedSymbol();
    const interval = this.selectedInterval();

    this.historyLoading.set(true);

    this.subscriptions.add(
      this.marketApi.getHistoricalCandles(symbol, interval, 500).subscribe({
        next: history => {
          this.mergeSeries(history);
          this.historyLoading.set(false);
        },
        error: () => {
          this.historyLoading.set(false);
        }
      })
    );
  }

  private loadMarketContext(): void {
    this.subscriptions.add(
      this.marketApi.getMarketContext(
        this.selectedSymbol(),
        this.selectedInterval()
      ).subscribe({
        next: context => this.marketContext.set(context),
        error: () => this.marketContext.set(null)
      })
    );

    this.subscriptions.add(
      this.marketApi.getAiAnalysis(
        this.selectedSymbol(),
        this.selectedInterval()
      ).subscribe({
        next: result => this.aiAnalysis.set(result),
        error: () => this.aiAnalysis.set(null)
      })
    );
  }

  private loadIndicators(): void {
    this.subscriptions.add(
      this.marketApi.getIndicators(
        this.selectedSymbol(),
        this.selectedInterval()
      ).subscribe({
        next: indicators => this.indicators.set(indicators),
        error: () => this.indicators.set(null)
      })
    );
  }

  private loadMicrostructure(): void {
    this.subscriptions.add(
      this.marketApi.getMicrostructure(this.selectedSymbol()).subscribe({
        next: summary => this.microstructure.set(summary),
        error: () => this.microstructure.set(null)
      })
    );
  }

  private connectRealtime(): void {
    const retryDelay = () => timer(3000);

    this.subscriptions.add(
      this.marketApi.tickerStream()
        .pipe(retry({ delay: retryDelay }))
        .subscribe({
          next: ticker => {
            this.connectionState.set('live');
            this.lastMarketEventAt.set(new Date());
            this.tickers.update(current => ({
              ...current,
              [ticker.symbol]: ticker
            }));
          },
          error: () => this.connectionState.set('offline')
        })
    );

    this.subscriptions.add(
      this.marketApi.orderBookStream()
        .pipe(retry({ delay: retryDelay }))
        .subscribe({
          next: orderBook => {
            this.orderBooks.update(current => ({
              ...current,
              [orderBook.symbol]: orderBook
            }));

            if (orderBook.symbol === this.selectedSymbol()) {
              this.marketApi.getMicrostructure(orderBook.symbol).subscribe({
                next: summary => this.microstructure.set(summary)
              });
            }
          }
        })
    );

    this.subscriptions.add(
      this.marketApi.candleStream()
        .pipe(retry({ delay: retryDelay }))
        .subscribe({
          next: candle => {
            this.connectionState.set('live');
            this.lastMarketEventAt.set(new Date());
            this.upsertCandle(candle);

            if (
              candle.symbol === this.selectedSymbol() &&
              candle.interval === this.selectedInterval() &&
              candle.closed
            ) {
              this.loadIndicators();
              this.loadMarketContext();
            }
          },
          error: () => this.connectionState.set('offline')
        })
    );
  }

  private mergeSeries(history: Candlestick[]): void {
    this.candles.update(current => {
      const merged = new Map<string, Candlestick>();

      history.forEach(candle => {
        merged.set(
          `${candle.symbol}:${candle.interval}:${candle.openTime}`,
          candle
        );
      });

      // Realtime data wins when the REST bootstrap overlaps the currently-forming candle.
      current.forEach(candle => {
        merged.set(
          `${candle.symbol}:${candle.interval}:${candle.openTime}`,
          candle
        );
      });

      return [...merged.values()]
        .sort((a, b) => new Date(a.openTime).getTime() - new Date(b.openTime).getTime())
        .slice(-3000);
    });
  }

  private upsertCandle(candle: Candlestick): void {
    this.candles.update(current => {
      const withoutSameCandle = current.filter(item =>
        !(
          item.symbol === candle.symbol &&
          item.interval === candle.interval &&
          item.openTime === candle.openTime
        )
      );

      return [...withoutSameCandle, candle]
        .sort((a, b) => new Date(a.openTime).getTime() - new Date(b.openTime).getTime())
        .slice(-3000);
    });
  }
}
