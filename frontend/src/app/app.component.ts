import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Subscription, catchError, fromEvent, map, of, retry, switchMap, timer } from 'rxjs';
import {
  AiAnalysisResult,
  AiAnalysisHistoryItem,
  AiEngineStatus,
  AiEvaluationSummary,
  AiGuardrailStatus,
  AiUsageSummary,
  BillingStatus,
  Candlestick,
  ConnectionState,
  MarketContext,
  MarketMicrostructure,
  MarketFeedHealth,
  NewsArticle,
  NewsSentimentSummary,
  OrderBookSnapshot,
  TechnicalIndicators,
  TickerSnapshot,
  TradeSetup
} from './core/models/market.models';
import { MarketApiService } from './core/services/market-api.service';
import { PushDeviceStatus, PushNotificationService } from './core/services/push-notification.service';
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
  readonly Math = Math;
  private readonly marketApi = inject(MarketApiService);
  private readonly pushNotifications = inject(PushNotificationService);
  private readonly subscriptions = new Subscription();
  private realtimeSubscriptions = new Subscription();
  private readonly realtimeStartedAt = Date.now();
  private lastRealtimeReconnectAt = 0;
  private readonly staleThresholdMs = 20_000;
  private readonly reconnectCooldownMs = 15_000;

  readonly symbols = ['BTCUSDT', 'ETHUSDT', 'SOLUSDT'];
  readonly intervals = ['1m', '5m', '15m', '1h', '4h', '1d'];
  readonly decisionIntervals = ['1m', '15m', '1h', '4h', '1d'];
  readonly decisionCandles = signal<Record<string, Candlestick | null>>({});
  readonly timeframeIndicators = signal<Record<string, TechnicalIndicators | null>>({});

  readonly selectedSymbol = signal('BTCUSDT');
  readonly selectedInterval = signal('1m');
  readonly connectionState = signal<ConnectionState>('connecting');
  readonly backendState = signal<'checking' | 'up' | 'down'>('checking');
  readonly backendLatencyMs = signal<number | null>(null);
  readonly backendLastCheckedAt = signal<Date | null>(null);
  readonly backendError = signal<string | null>(null);
  readonly marketFeedHealth = signal<MarketFeedHealth | null>(null);
  readonly tickers = signal<Record<string, TickerSnapshot>>({});
  readonly candles = signal<Candlestick[]>([]);
  readonly lastMarketEventAt = signal<Date | null>(null);
  readonly marketStreamAgeSeconds = signal<number | null>(null);
  readonly marketReconnectCount = signal(0);
  readonly historyLoading = signal(false);
  readonly orderBooks = signal<Record<string, OrderBookSnapshot>>({});
  readonly microstructure = signal<MarketMicrostructure | null>(null);
  readonly indicators = signal<TechnicalIndicators | null>(null);
  readonly tradeSetup = signal<TradeSetup | null>(null);
  readonly marketContext = signal<MarketContext | null>(null);
  readonly aiAnalysis = signal<AiAnalysisResult | null>(null);
  readonly aiHistory = signal<AiAnalysisHistoryItem[]>([]);
  readonly aiEvaluationSummary = signal<AiEvaluationSummary | null>(null);
  readonly aiStatus = signal<AiEngineStatus | null>(null);
  readonly aiGuardrails = signal<AiGuardrailStatus | null>(null);
  readonly news = signal<NewsArticle[]>([]);
  readonly newsSentiment = signal<NewsSentimentSummary | null>(null);
  readonly aiUsage = signal<AiUsageSummary | null>(null);
  readonly billingStatus = signal<BillingStatus | null>(null);
  readonly aiRunning = signal(false);
  readonly pushStatus = signal<PushDeviceStatus | 'checking' | 'enabling' | 'error'>('checking');
  readonly showNotificationOnboarding = signal(false);
  readonly showEma = signal(false);
  readonly showRsi = signal(false);
  readonly showMacd = signal(false);

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

  decisionCandle(interval: string): Candlestick | null {
    return this.decisionCandles()[interval] ?? null;
  }

  candleChangePercent(candle: Candlestick | null): number | null {
    if (!candle || candle.open === 0) {
      return null;
    }

    return ((candle.close - candle.open) / candle.open) * 100;
  }

  timeframeRole(interval: string): string {
    switch (interval) {
      case '1m': return 'Execution';
      case '15m': return 'Short-term';
      case '1h': return 'Decision';
      case '4h': return 'Trend';
      case '1d': return 'Context';
      default: return '';
    }
  }

  timeframeIndicator(interval: string): TechnicalIndicators | null {
    return this.timeframeIndicators()[interval] ?? null;
  }

  technicalDirection(score: number | null | undefined): string {
    if (score == null) {
      return 'Waiting';
    }
    if (score >= 25) {
      return 'Bullish';
    }
    if (score <= -25) {
      return 'Bearish';
    }
    return 'Neutral';
  }

  momentumStrength(score: number | null | undefined): string {
    if (score == null) {
      return 'Waiting';
    }

    const strength = Math.abs(score);
    if (strength >= 60) {
      return 'Strong';
    }
    if (strength >= 30) {
      return 'Moderate';
    }
    return 'Weak';
  }

  readonly marketUiStatus = computed(() => {
    const state = this.connectionState();

    if (state === 'live') {
      return 'LIVE';
    }

    if (state === 'stale') {
      return this.marketReconnectCount() > 0 ? 'RECONNECTING' : 'DEGRADED';
    }

    if (state === 'offline') {
      return 'OFFLINE';
    }

    return this.marketReconnectCount() > 0 ? 'RECONNECTING' : 'DEGRADED';
  });

  readonly dailyChange = computed(() => {
    const ticker = this.selectedTicker();
    if (!ticker || ticker.openPrice === 0) {
      return null;
    }
    return ((ticker.closePrice - ticker.openPrice) / ticker.openPrice) * 100;
  });

  ngOnInit(): void {
    this.startBackendHealthCheck();
    this.startMarketFeedHealthCheck();
    this.loadSnapshots();
    this.loadHistory();
    this.loadDecisionCandles();
    this.loadTimeframeIndicators();
    this.loadMicrostructure();
    this.loadIndicators();
    this.loadMarketContext();
    this.loadTradeSetup();
    this.loadLatestAiAnalysis();
    this.loadAiHistory();
    this.loadAiEvaluationSummary();
    this.startAiStatusMonitoring();
    this.loadAiGuardrails();
    this.loadNews();
    this.startAiUsageMonitoring();
    this.loadBillingStatus();
    this.initializePushNotifications();
    this.startNotificationPermissionMonitoring();
    this.connectRealtime();
    this.startRealtimeWatchdog();
  }

  ngOnDestroy(): void {
    this.realtimeSubscriptions.unsubscribe();
    this.subscriptions.unsubscribe();
  }

  selectSymbol(symbol: string): void {
    this.selectedSymbol.set(symbol);
    this.loadHistory();
    this.loadDecisionCandles();
    this.loadTimeframeIndicators();
    this.loadMicrostructure();
    this.loadIndicators();
    this.loadMarketContext();
    this.loadTradeSetup();
    this.loadLatestAiAnalysis();
    this.loadAiHistory();
    this.loadAiEvaluationSummary();
    this.loadNews();
  }

  selectInterval(interval: string): void {
    this.selectedInterval.set(interval);
    this.loadHistory();
    this.loadIndicators();
    this.loadMarketContext();
    this.loadTradeSetup();
    this.loadLatestAiAnalysis();
    this.loadAiHistory();
    this.loadAiEvaluationSummary();
  }

  toggleEma(): void {
    this.showEma.update(value => !value);
  }

  toggleRsi(): void {
    this.showRsi.update(value => !value);
  }

  toggleMacd(): void {
    this.showMacd.update(value => !value);
  }

  runAiAnalysis(): void {
    const state = this.aiStatus()?.state;

    if (
      this.aiRunning()
      || (state !== 'READY' && state !== 'CONFIGURED_UNVERIFIED' && state !== 'MOCK')
    ) {
      return;
    }

    this.aiRunning.set(true);

    this.subscriptions.add(
      this.marketApi.triggerAiAnalysis(
        this.selectedSymbol(),
        this.selectedInterval()
      ).subscribe({
        next: result => {
          this.aiAnalysis.set(result);
          this.aiRunning.set(false);
          this.loadAiUsage();
          this.loadAiStatus();
          this.loadAiGuardrails();
          this.loadAiHistory();
          this.loadAiEvaluationSummary();
        },
        error: () => {
          this.aiRunning.set(false);
          this.loadAiStatus();
        }
      })
    );
  }

  async enablePushNotifications(): Promise<void> {
    this.pushStatus.set('enabling');

    try {
      const status = await this.pushNotifications.requestPermission();
      this.pushStatus.set(status);
      this.showNotificationOnboarding.set(false);
    } catch {
      this.pushStatus.set('error');
    }
  }

  dismissNotificationOnboarding(): void {
    this.showNotificationOnboarding.set(false);
    localStorage.setItem(
      'coinmind-notification-onboarding-dismissed-at',
      Date.now().toString()
    );
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

  decisionPrice(setup: TradeSetup): number {
    return this.selectedTicker()?.closePrice ?? setup.marketPrice;
  }

  entryAreaLabel(setup: TradeSetup): string {
    return setup.side === 'SHORT' ? 'SELL AREA' : 'BUY AREA';
  }

  entryStatus(setup: TradeSetup): string {
    if (
      setup.action === 'WAIT' ||
      setup.entryLow == null ||
      setup.entryHigh == null
    ) {
      return 'NO ENTRY YET';
    }

    const price = this.decisionPrice(setup);

    if (price >= setup.entryLow && price <= setup.entryHigh) {
      return 'IN ENTRY AREA';
    }

    if (setup.side === 'LONG') {
      return price > setup.entryHigh
        ? 'WAIT FOR PULLBACK'
        : 'WAIT FOR CONFIRMATION';
    }

    return price < setup.entryLow
      ? 'WAIT FOR REBOUND'
      : 'WAIT FOR CONFIRMATION';
  }

  entryStatusClass(setup: TradeSetup): string {
    const status = this.entryStatus(setup);

    if (status === 'IN ENTRY AREA') {
      return 'ready';
    }

    if (status === 'NO ENTRY YET') {
      return 'neutral';
    }

    return 'waiting';
  }

  private async initializePushNotifications(
    allowOnboarding = true
  ): Promise<void> {
    try {
      const status = await this.pushNotifications.refreshFromDeviceSettings();
      this.pushStatus.set(status);

      const dismissedAt = Number(
        localStorage.getItem('coinmind-notification-onboarding-dismissed-at') ?? 0
      );
      const onboardingCooldownMs = 7 * 24 * 60 * 60 * 1000;
      const canShowOnboarding =
        !dismissedAt || Date.now() - dismissedAt >= onboardingCooldownMs;

      this.showNotificationOnboarding.set(
        allowOnboarding &&
        status === 'permission-required' &&
        canShowOnboarding
      );
    } catch {
      this.pushStatus.set('error');
      this.showNotificationOnboarding.set(false);
    }
  }

  private startNotificationPermissionMonitoring(): void {
    this.subscriptions.add(
      fromEvent(window, 'focus').subscribe(() => {
        void this.initializePushNotifications(false);
      })
    );

    this.subscriptions.add(
      fromEvent(document, 'visibilitychange').subscribe(() => {
        if (document.visibilityState === 'visible') {
          void this.initializePushNotifications(false);
        }
      })
    );
  }

  private startMarketFeedHealthCheck(): void {
    this.subscriptions.add(
      timer(0, 5000)
        .pipe(
          switchMap(() => this.marketApi.getMarketFeedHealth().pipe(
            catchError(() => of(null))
          ))
        )
        .subscribe(health => {
          this.marketFeedHealth.set(health);

          if (!health) {
            return;
          }

          if (health.status === 'DEGRADED' && this.connectionState() === 'live') {
            this.connectionState.set('stale');
          }
        })
    );
  }

  private startBackendHealthCheck(): void {
    this.subscriptions.add(
      timer(0, 5000)
        .pipe(
          switchMap(() => {
            const startedAt = performance.now();

            return this.marketApi.getBackendHealth().pipe(
              map(response => ({
                ok: response.status?.toUpperCase() === 'UP',
                latency: Math.round(performance.now() - startedAt),
                error: null as string | null
              })),
              catchError(error => of({
                ok: false,
                latency: Math.round(performance.now() - startedAt),
                error: this.describeBackendError(error)
              }))
            );
          })
        )
        .subscribe(result => {
          this.backendState.set(result.ok ? 'up' : 'down');
          this.backendLatencyMs.set(result.latency);
          this.backendLastCheckedAt.set(new Date());
          this.backendError.set(result.error);
        })
    );
  }

  private describeBackendError(error: any): string {
    if (error?.status === 0) {
      return 'Backend unreachable / connection refused';
    }

    if (error?.status) {
      return `HTTP ${error.status} ${error.statusText ?? ''}`.trim();
    }

    return error?.message ?? 'Unknown backend error';
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

  private loadDecisionCandles(): void {
    const symbol = this.selectedSymbol();

    this.decisionIntervals.forEach(interval => {
      this.subscriptions.add(
        this.marketApi.getHistoricalCandles(symbol, interval, 2).subscribe({
          next: history => {
            const latest = history.length ? history[history.length - 1] : null;
            this.decisionCandles.update(current => ({
              ...current,
              [interval]: latest
            }));
          },
          error: () => {
            this.decisionCandles.update(current => ({
              ...current,
              [interval]: null
            }));
          }
        })
      );
    });
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

  private loadNews(): void {
    this.subscriptions.add(
      this.marketApi.getNews(this.selectedSymbol(), 8).subscribe({
        next: articles => this.news.set(articles),
        error: () => this.news.set([])
      })
    );

    this.subscriptions.add(
      this.marketApi.getNewsSentiment(this.selectedSymbol()).subscribe({
        next: sentiment => this.newsSentiment.set(sentiment),
        error: () => this.newsSentiment.set(null)
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

  }

  private loadLatestAiAnalysis(): void {
    this.subscriptions.add(
      this.marketApi.getLatestAiAnalysis(
        this.selectedSymbol(),
        this.selectedInterval()
      ).subscribe({
        next: result => this.aiAnalysis.set(result),
        error: () => this.aiAnalysis.set(null)
      })
    );
  }

  private loadAiEvaluationSummary(): void {
    this.marketApi.getAiEvaluationSummary(
      this.selectedSymbol(),
      this.selectedInterval()
    ).subscribe({
      next: summary => this.aiEvaluationSummary.set(summary),
      error: () => this.aiEvaluationSummary.set(null)
    });
  }

  private loadAiHistory(): void {
    this.marketApi.getAiHistory(
      this.selectedSymbol(),
      this.selectedInterval(),
      12
    ).subscribe({
      next: history => this.aiHistory.set(history),
      error: () => this.aiHistory.set([])
    });
  }

  private startAiStatusMonitoring(): void {
    this.subscriptions.add(
      timer(0, 30_000).subscribe(() => {
        this.loadAiStatus();
        this.loadAiGuardrails();
      })
    );
  }

  private loadAiStatus(): void {
    this.marketApi.getAiStatus().subscribe({
      next: status => this.aiStatus.set(status),
      error: () => this.aiStatus.set(null)
    });
  }

  private loadAiGuardrails(): void {
    this.marketApi.getAiGuardrails().subscribe({
      next: status => this.aiGuardrails.set(status),
      error: () => this.aiGuardrails.set(null)
    });
  }

  private startAiUsageMonitoring(): void {
    this.subscriptions.add(
      timer(0, 60_000).subscribe(() => this.loadAiUsage())
    );
  }

  private loadAiUsage(): void {
    this.marketApi.getAiUsageSummary(14).subscribe({
      next: summary => this.aiUsage.set(summary)
    });
  }

  private loadBillingStatus(): void {
    this.marketApi.getBillingStatus().subscribe({
      next: status => this.billingStatus.set(status)
    });
  }

  private loadTradeSetup(): void {
    this.marketApi.getTradeSetup(
      this.selectedSymbol(),
      this.selectedInterval()
    ).subscribe({
      next: setup => this.tradeSetup.set(setup),
      error: () => this.tradeSetup.set(null)
    });
  }

  private loadTimeframeIndicators(): void {
    const symbol = this.selectedSymbol();

    this.decisionIntervals.forEach(interval => {
      this.subscriptions.add(
        this.marketApi.getIndicators(symbol, interval).subscribe({
          next: indicators => {
            this.timeframeIndicators.update(current => ({
              ...current,
              [interval]: indicators
            }));
          },
          error: () => {
            this.timeframeIndicators.update(current => ({
              ...current,
              [interval]: null
            }));
          }
        })
      );
    });
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

  private startRealtimeWatchdog(): void {
    this.subscriptions.add(
      timer(5000, 5000).subscribe(() => {
        const now = Date.now();
        const lastEvent = this.lastMarketEventAt();
        const referenceTime = lastEvent?.getTime() ?? this.realtimeStartedAt;
        const ageMs = now - referenceTime;

        this.marketStreamAgeSeconds.set(Math.floor(ageMs / 1000));

        if (this.backendState() === 'down') {
          this.connectionState.set('offline');
          return;
        }

        if (ageMs <= this.staleThresholdMs) {
          return;
        }

        this.connectionState.set('stale');

        if (
          this.backendState() === 'up' &&
          now - this.lastRealtimeReconnectAt >= this.reconnectCooldownMs
        ) {
          this.marketReconnectCount.update(count => count + 1);
          this.connectRealtime(true);
        }
      })
    );
  }

  private connectRealtime(forceReconnect = false): void {
    if (forceReconnect) {
      this.realtimeSubscriptions.unsubscribe();
      this.realtimeSubscriptions = new Subscription();
    }

    this.lastRealtimeReconnectAt = Date.now();
    this.connectionState.set('connecting');

    const retryDelay = () => timer(3000);

    this.realtimeSubscriptions.add(
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

    this.realtimeSubscriptions.add(
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

    this.realtimeSubscriptions.add(
      this.marketApi.candleStream()
        .pipe(retry({ delay: retryDelay }))
        .subscribe({
          next: candle => {
            this.connectionState.set('live');
            this.lastMarketEventAt.set(new Date());
            this.upsertCandle(candle);

            if (
              candle.symbol === this.selectedSymbol() &&
              this.decisionIntervals.includes(candle.interval)
            ) {
              this.decisionCandles.update(current => ({
                ...current,
                [candle.interval]: candle
              }));
            }

            if (
              candle.symbol === this.selectedSymbol() &&
              candle.interval === this.selectedInterval() &&
              candle.closed
            ) {
              this.loadIndicators();
              if (this.decisionIntervals.includes(candle.interval)) {
                this.marketApi.getIndicators(candle.symbol, candle.interval).subscribe({
                  next: indicators => {
                    this.timeframeIndicators.update(current => ({
                      ...current,
                      [candle.interval]: indicators
                    }));
                  }
                });
              }
              this.loadMarketContext();
              this.loadTradeSetup();

              if (['15m', '1h', '4h', '1d'].includes(candle.interval)) {
                setTimeout(() => this.loadLatestAiAnalysis(), 2500);
              }
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
