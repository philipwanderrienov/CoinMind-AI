export interface TickerSnapshot {
  symbol: string;
  closePrice: number;
  openPrice: number;
  highPrice: number;
  lowPrice: number;
  baseVolume: number;
  quoteVolume: number;
  eventTime: string;
}

export interface Candlestick {
  symbol: string;
  interval: string;
  openTime: string;
  closeTime: string;
  open: number;
  high: number;
  low: number;
  close: number;
  baseVolume: number;
  quoteVolume: number;
  tradeCount: number;
  closed: boolean;
  eventTime: string;
}

export type ConnectionState = 'connecting' | 'live' | 'offline';


export interface OrderBookSnapshot {
  symbol: string;
  bestBidPrice: number;
  bestBidQuantity: number;
  bestAskPrice: number;
  bestAskQuantity: number;
  spread: number;
  midPrice: number;
  eventTime: string;
}

export interface MarketMicrostructure {
  symbol: string;
  spread: number;
  spreadBps: number;
  midPrice: number;
  buyVolume: number;
  sellVolume: number;
  buySellRatio: number;
  updatedAt: string;
}
