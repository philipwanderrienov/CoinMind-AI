import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Candlestick,
  AiAnalysisResult,
  MarketContext,
  MarketMicrostructure,
  NewsArticle,
  NewsSentimentSummary,
  OrderBookSnapshot,
  TechnicalIndicators,
  TickerSnapshot
} from '../models/market.models';

@Injectable({ providedIn: 'root' })
export class MarketApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/market';

  getBackendHealth(): Observable<{ status: string }> {
    return this.http.get<{ status: string }>('/actuator/health');
  }

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

  getNews(symbol: string, limit = 10): Observable<NewsArticle[]> {
    return this.http.get<NewsArticle[]>(
      `/api/v1/news/${symbol}?limit=${limit}`
    );
  }

  getNewsSentiment(symbol: string): Observable<NewsSentimentSummary> {
    return this.http.get<NewsSentimentSummary>(
      `/api/v1/news/${symbol}/sentiment?hours=6&limit=20`
    );
  }

  getMarketContext(symbol: string, interval: string): Observable<MarketContext> {
    return this.http.get<MarketContext>(
      `/api/v1/ai/context/${symbol}/${interval}`
    );
  }

  getAiAnalysis(symbol: string, interval: string): Observable<AiAnalysisResult> {
    return this.http.get<AiAnalysisResult>(
      `/api/v1/ai/analysis/${symbol}/${interval}`
    );
  }

  getIndicators(symbol: string, interval: string): Observable<TechnicalIndicators> {
    return this.http.get<TechnicalIndicators>(
      `/api/v1/indicators/${symbol}/${interval}`
    );
  }

  getOrderBook(symbol: string): Observable<OrderBookSnapshot> {
    return this.http.get<OrderBookSnapshot>(
      `${this.baseUrl}/microstructure/order-books/${symbol}`
    );
  }

  getMicrostructure(symbol: string): Observable<MarketMicrostructure> {
    return this.http.get<MarketMicrostructure>(
      `${this.baseUrl}/microstructure/${symbol}/summary`
    );
  }

  orderBookStream(): Observable<OrderBookSnapshot> {
    return this.eventSource<OrderBookSnapshot>(
      `${this.baseUrl}/microstructure/order-books/stream`
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
