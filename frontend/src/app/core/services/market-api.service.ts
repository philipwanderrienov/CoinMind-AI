import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Candlestick, TickerSnapshot } from '../models/market.models';

@Injectable({ providedIn: 'root' })
export class MarketApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/market';

  getTickers(): Observable<TickerSnapshot[]> {
    return this.http.get<TickerSnapshot[]>(`${this.baseUrl}/tickers`);
  }

  getCandles(symbol: string): Observable<Candlestick[]> {
    return this.http.get<Candlestick[]>(`${this.baseUrl}/candles/${symbol}`);
  }

  getCandle(symbol: string, interval: string): Observable<Candlestick> {
    return this.http.get<Candlestick>(`${this.baseUrl}/candles/${symbol}/${interval}`);
  }

  getHistoricalCandles(
    symbol: string,
    interval: string,
    limit = 500
  ): Observable<Candlestick[]> {
    return this.http.get<Candlestick[]>(
      `${this.baseUrl}/history/${symbol}/${interval}?limit=${limit}`
    );
  }

  tickerStream(): Observable<TickerSnapshot> {
    return this.eventSource<TickerSnapshot>(`${this.baseUrl}/tickers/stream`);
  }

  candleStream(): Observable<Candlestick> {
    return this.eventSource<Candlestick>(`${this.baseUrl}/candles/stream`);
  }

  private eventSource<T>(url: string): Observable<T> {
    return new Observable<T>(subscriber => {
      const source = new EventSource(url);

      source.onmessage = event => {
        try {
          subscriber.next(JSON.parse(event.data) as T);
        } catch (error) {
          subscriber.error(error);
        }
      };

      source.onerror = () => {
        subscriber.error(new Error(`Realtime stream disconnected: ${url}`));
        source.close();
      };

      return () => source.close();
    });
  }
}
