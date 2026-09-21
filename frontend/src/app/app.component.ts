import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Subscription, retry, timer } from 'rxjs';
import { Candlestick, ConnectionState, TickerSnapshot } from './core/models/market.models';
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

  readonly selectedTicker = computed(() => this.tickers()[this.selectedSymbol()] ?? null);

  readonly selectedCandles = computed(() =>
    this.candles()
      .filter(candle =>
        candle.symbol === this.selectedSymbol() &&
        candle.interval === this.selectedInterval()
      )
      .sort((a, b) => new Date(a.openTime).getTime() - new Date(b.openTime).getTime())
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
    this.connectRealtime();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  selectSymbol(symbol: string): void {
    this.selectedSymbol.set(symbol);
    this.seedCurrentCandle();
  }

  selectInterval(interval: string): void {
    this.selectedInterval.set(interval);
    this.seedCurrentCandle();
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

    this.seedCurrentCandle();
  }

  private seedCurrentCandle(): void {
    this.subscriptions.add(
      this.marketApi.getCandle(this.selectedSymbol(), this.selectedInterval()).subscribe({
        next: candle => this.upsertCandle(candle),
        error: () => {
          // It is valid for no candle to be available while the backend is still connecting.
        }
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
      this.marketApi.candleStream()
        .pipe(retry({ delay: retryDelay }))
        .subscribe({
          next: candle => {
            this.connectionState.set('live');
            this.lastMarketEventAt.set(new Date());
            this.upsertCandle(candle);
          },
          error: () => this.connectionState.set('offline')
        })
    );
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

      const next = [...withoutSameCandle, candle];

      // Keep the browser session bounded while historical persistence is not yet implemented.
      return next.slice(-1500);
    });
  }
}
