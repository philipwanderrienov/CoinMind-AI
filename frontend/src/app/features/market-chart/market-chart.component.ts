import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import {
  CandlestickData,
  CandlestickSeries,
  ColorType,
  createChart,
  IChartApi,
  ISeriesApi,
  Time
} from 'lightweight-charts';
import { Candlestick } from '../../core/models/market.models';

@Component({
  selector: 'app-market-chart',
  standalone: true,
  template: '<div #chart class="chart-container"></div>',
  styles: [`
    :host {
      display: block;
      width: 100%;
      height: 100%;
      min-height: 420px;
    }

    .chart-container {
      width: 100%;
      height: 100%;
      min-height: 420px;
    }
  `]
})
export class MarketChartComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input({ required: true }) candles: Candlestick[] = [];
  @Input({ required: true }) seriesKey = '';

  @ViewChild('chart', { static: true })
  private chartElement!: ElementRef<HTMLDivElement>;

  private chart?: IChartApi;
  private series?: ISeriesApi<'Candlestick'>;
  private resizeObserver?: ResizeObserver;
  private appliedSeriesKey = '';
  private appliedCount = 0;
  private appliedFirstTime?: number;
  private appliedLastTime?: number;

  ngAfterViewInit(): void {
    this.chart = createChart(this.chartElement.nativeElement, {
      width: this.chartElement.nativeElement.clientWidth,
      height: this.chartElement.nativeElement.clientHeight,
      layout: {
        background: { type: ColorType.Solid, color: '#0b1220' },
        textColor: '#8d9bb3'
      },
      grid: {
        vertLines: { color: '#172033' },
        horzLines: { color: '#172033' }
      },
      rightPriceScale: {
        borderColor: '#22304a'
      },
      timeScale: {
        borderColor: '#22304a',
        timeVisible: true,
        secondsVisible: false
      }
    });

    this.series = this.chart.addSeries(CandlestickSeries, {
      upColor: '#21d4a7',
      downColor: '#ff5d7a',
      borderVisible: false,
      wickUpColor: '#21d4a7',
      wickDownColor: '#ff5d7a',
      priceLineVisible: true
    });

    this.resizeObserver = new ResizeObserver(entries => {
      const entry = entries[0];
      if (!entry || !this.chart) {
        return;
      }

      this.chart.applyOptions({
        width: entry.contentRect.width,
        height: entry.contentRect.height
      });
    });

    this.resizeObserver.observe(this.chartElement.nativeElement);
    this.resetSeries(true);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.series) {
      return;
    }

    if (changes['seriesKey'] && !changes['seriesKey'].firstChange) {
      this.resetSeries(true);
      return;
    }

    if (changes['candles']) {
      this.applyIncrementalUpdate();
    }
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.chart?.remove();
  }

  private resetSeries(fitContent: boolean): void {
    if (!this.series) {
      return;
    }

    const data = this.sortedChartData();

    this.series.setData(data);
    this.captureAppliedState(data);

    if (fitContent || data.length <= 2) {
      this.chart?.timeScale().fitContent();
    }
  }

  private applyIncrementalUpdate(): void {
    if (!this.series) {
      return;
    }

    const data = this.sortedChartData();

    if (!data.length) {
      this.resetSeries(false);
      return;
    }

    const firstTime = Number(data[0].time);
    const lastTime = Number(data[data.length - 1].time);

    const requiresFullReload =
      this.appliedSeriesKey !== this.seriesKey ||
      this.appliedCount === 0 ||
      this.appliedFirstTime !== firstTime ||
      data.length < this.appliedCount ||
      data.length > this.appliedCount + 1 ||
      (this.appliedLastTime != null && lastTime < this.appliedLastTime);

    if (requiresFullReload) {
      this.resetSeries(this.appliedSeriesKey !== this.seriesKey);
      return;
    }

    this.series.update(data[data.length - 1]);
    this.captureAppliedState(data);
  }

  private sortedChartData(): CandlestickData<Time>[] {
    return this.candles
      .map(candle => this.toChartData(candle))
      .sort((a, b) => Number(a.time) - Number(b.time));
  }

  private captureAppliedState(data: CandlestickData<Time>[]): void {
    this.appliedSeriesKey = this.seriesKey;
    this.appliedCount = data.length;
    this.appliedFirstTime = data.length ? Number(data[0].time) : undefined;
    this.appliedLastTime = data.length ? Number(data[data.length - 1].time) : undefined;
  }

  private toChartData(candle: Candlestick): CandlestickData<Time> {
    return {
      time: Math.floor(new Date(candle.openTime).getTime() / 1000) as Time,
      open: candle.open,
      high: candle.high,
      low: candle.low,
      close: candle.close
    };
  }
}
