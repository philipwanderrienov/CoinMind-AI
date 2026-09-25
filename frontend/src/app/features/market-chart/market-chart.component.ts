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
  LineData,
  LineSeries,
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
  @Input() showEma = false;
  @Input() showRsi = false;
  @Input() showMacd = false;

  @ViewChild('chart', { static: true })
  private chartElement!: ElementRef<HTMLDivElement>;

  private chart?: IChartApi;
  private series?: ISeriesApi<'Candlestick'>;

  private ema20Series?: ISeriesApi<'Line'>;
  private ema50Series?: ISeriesApi<'Line'>;
  private ema200Series?: ISeriesApi<'Line'>;
  private rsiSeries?: ISeriesApi<'Line'>;
  private macdSeries?: ISeriesApi<'Line'>;
  private macdSignalSeries?: ISeriesApi<'Line'>;

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
        textColor: '#8d9bb3',
        panes: {
          separatorColor: '#22304a',
          separatorHoverColor: '#6f59db',
          enableResize: true
        }
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

      const width = Math.floor(entry.contentRect.width);
      const height = Math.floor(entry.contentRect.height);

      if (width <= 0 || height <= 0) {
        return;
      }

      this.chart.applyOptions({ width, height });
    });

    this.resizeObserver.observe(this.chartElement.nativeElement);
    this.resetSeries(true);
    this.rebuildIndicatorSeries();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.series) {
      return;
    }

    if (changes['seriesKey'] && !changes['seriesKey'].firstChange) {
      this.resetSeriesForSelection();
      return;
    }

    if (
      changes['showEma'] ||
      changes['showRsi'] ||
      changes['showMacd']
    ) {
      this.rebuildIndicatorSeries();
      return;
    }

    if (changes['candles']) {
      this.applyIncrementalUpdate();
      this.renderIndicators();
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
      this.fitChartToData();
    }
  }

  private resetSeriesForSelection(): void {
    if (!this.series || !this.chart) {
      return;
    }

    this.series.setData([]);
    this.appliedSeriesKey = '';
    this.appliedCount = 0;
    this.appliedFirstTime = undefined;
    this.appliedLastTime = undefined;

    this.chart.priceScale('right').applyOptions({
      autoScale: true
    });

    const data = this.sortedChartData();
    this.series.setData(data);
    this.captureAppliedState(data);

    this.rebuildIndicatorSeries();
    this.fitChartToData();
  }

  private fitChartToData(): void {
    requestAnimationFrame(() => {
      if (!this.chart) {
        return;
      }

      this.chart.priceScale('right').applyOptions({
        autoScale: true
      });
      this.chart.timeScale().fitContent();
    });
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

  private rebuildIndicatorSeries(): void {
    if (!this.chart) {
      return;
    }

    this.removeIndicatorSeries();

    if (this.showEma) {
      this.ema20Series = this.chart.addSeries(LineSeries, {
        color: '#f2b84b',
        lineWidth: 2,
        priceLineVisible: false,
        lastValueVisible: false
      }, 0);

      this.ema50Series = this.chart.addSeries(LineSeries, {
        color: '#5aa9ff',
        lineWidth: 2,
        priceLineVisible: false,
        lastValueVisible: false
      }, 0);

      this.ema200Series = this.chart.addSeries(LineSeries, {
        color: '#b38cff',
        lineWidth: 2,
        priceLineVisible: false,
        lastValueVisible: false
      }, 0);
    }

    let paneIndex = 1;

    if (this.showRsi) {
      this.rsiSeries = this.chart.addSeries(LineSeries, {
        color: '#21d4a7',
        lineWidth: 2,
        priceLineVisible: false,
        lastValueVisible: true,
        priceFormat: {
          type: 'price',
          precision: 1,
          minMove: 0.1
        }
      }, paneIndex);

      paneIndex++;
    }

    if (this.showMacd) {
      this.macdSeries = this.chart.addSeries(LineSeries, {
        color: '#5aa9ff',
        lineWidth: 2,
        priceLineVisible: false,
        lastValueVisible: false
      }, paneIndex);

      this.macdSignalSeries = this.chart.addSeries(LineSeries, {
        color: '#f2b84b',
        lineWidth: 2,
        priceLineVisible: false,
        lastValueVisible: false
      }, paneIndex);
    }

    this.renderIndicators();
    this.resizeIndicatorPanes();
  }

  private removeIndicatorSeries(): void {
    if (!this.chart) {
      return;
    }

    [
      this.ema20Series,
      this.ema50Series,
      this.ema200Series,
      this.rsiSeries,
      this.macdSeries,
      this.macdSignalSeries
    ].forEach(series => {
      if (series) {
        this.chart?.removeSeries(series);
      }
    });

    this.ema20Series = undefined;
    this.ema50Series = undefined;
    this.ema200Series = undefined;
    this.rsiSeries = undefined;
    this.macdSeries = undefined;
    this.macdSignalSeries = undefined;
  }

  private resizeIndicatorPanes(): void {
    requestAnimationFrame(() => {
      if (!this.chart) {
        return;
      }

      const panes = this.chart.panes();

      if (panes[0]) {
        panes[0].setHeight(
          this.showRsi || this.showMacd ? 280 : 420
        );
      }

      for (let index = 1; index < panes.length; index++) {
        panes[index].setHeight(120);
      }
    });
  }

  private renderIndicators(): void {
    const candles = [...this.candles]
      .sort((a, b) =>
        new Date(a.openTime).getTime() - new Date(b.openTime).getTime()
      );

    if (!candles.length) {
      return;
    }

    const closes = candles.map(candle => candle.close);
    const times = candles.map(candle =>
      Math.floor(new Date(candle.openTime).getTime() / 1000) as Time
    );

    if (this.showEma) {
      this.ema20Series?.setData(this.toLineData(times, this.ema(closes, 20)));
      this.ema50Series?.setData(this.toLineData(times, this.ema(closes, 50)));
      this.ema200Series?.setData(this.toLineData(times, this.ema(closes, 200)));
    }

    if (this.showRsi) {
      this.rsiSeries?.setData(this.toLineData(times, this.rsi(closes, 14)));
    }

    if (this.showMacd) {
      const macd = this.macd(closes);
      this.macdSeries?.setData(this.toLineData(times, macd.macd));
      this.macdSignalSeries?.setData(this.toLineData(times, macd.signal));
    }
  }

  private ema(values: number[], period: number): Array<number | null> {
    const result: Array<number | null> = Array(values.length).fill(null);

    if (values.length < period) {
      return result;
    }

    let seed = 0;
    for (let i = 0; i < period; i++) {
      seed += values[i];
    }

    let previous = seed / period;
    result[period - 1] = previous;

    const multiplier = 2 / (period + 1);

    for (let i = period; i < values.length; i++) {
      previous = (values[i] - previous) * multiplier + previous;
      result[i] = previous;
    }

    return result;
  }

  private rsi(values: number[], period: number): Array<number | null> {
    const result: Array<number | null> = Array(values.length).fill(null);

    if (values.length <= period) {
      return result;
    }

    let gains = 0;
    let losses = 0;

    for (let i = 1; i <= period; i++) {
      const change = values[i] - values[i - 1];

      if (change >= 0) {
        gains += change;
      } else {
        losses += Math.abs(change);
      }
    }

    let averageGain = gains / period;
    let averageLoss = losses / period;
    result[period] = this.rsiValue(averageGain, averageLoss);

    for (let i = period + 1; i < values.length; i++) {
      const change = values[i] - values[i - 1];
      const gain = Math.max(change, 0);
      const loss = Math.max(-change, 0);

      averageGain = ((averageGain * (period - 1)) + gain) / period;
      averageLoss = ((averageLoss * (period - 1)) + loss) / period;

      result[i] = this.rsiValue(averageGain, averageLoss);
    }

    return result;
  }

  private rsiValue(averageGain: number, averageLoss: number): number {
    if (averageLoss === 0) {
      return 100;
    }

    const rs = averageGain / averageLoss;
    return 100 - (100 / (1 + rs));
  }

  private macd(values: number[]): {
    macd: Array<number | null>;
    signal: Array<number | null>;
  } {
    const ema12 = this.ema(values, 12);
    const ema26 = this.ema(values, 26);
    const macd: Array<number | null> = values.map((_, index) => {
      const fast = ema12[index];
      const slow = ema26[index];

      return fast == null || slow == null ? null : fast - slow;
    });

    const signal: Array<number | null> = Array(values.length).fill(null);
    const validMacd = macd
      .map((value, index) => ({ value, index }))
      .filter(item => item.value != null) as Array<{ value: number; index: number }>;

    const signalValues = this.ema(
      validMacd.map(item => item.value),
      9
    );

    signalValues.forEach((value, signalIndex) => {
      if (value != null) {
        signal[validMacd[signalIndex].index] = value;
      }
    });

    return { macd, signal };
  }

  private toLineData(
    times: Time[],
    values: Array<number | null>
  ): LineData<Time>[] {
    const data: LineData<Time>[] = [];

    values.forEach((value, index) => {
      if (value == null || !Number.isFinite(value)) {
        return;
      }

      data.push({
        time: times[index],
        value
      });
    });

    return data;
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
