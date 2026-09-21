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


export interface TechnicalIndicators {
  symbol: string;
  interval: string;
  lastPrice: number;
  ema20: number;
  ema50: number;
  ema200: number;
  rsi14: number;
  macd: number;
  macdSignal: number;
  macdHistogram: number;
  atr14: number;
  bollingerMiddle: number;
  bollingerUpper: number;
  bollingerLower: number;
  volumeRatio: number;
  trendScore: number;
  momentumScore: number;
  volatilityScore: number;
  calculatedAt: string;
}


export interface MarketContext {
  symbol: string;
  interval: string;
  generatedAt: string;
  price: {
    lastPrice: number;
    open24h: number;
    high24h: number;
    low24h: number;
    quoteVolume24h: number;
  };
  technical: {
    ema20: number;
    ema50: number;
    ema200: number;
    rsi14: number;
    macd: number;
    macdSignal: number;
    macdHistogram: number;
    atr14: number;
    bollingerMiddle: number;
    bollingerUpper: number;
    bollingerLower: number;
    volumeRatio: number;
    trendScore: number;
    momentumScore: number;
    volatilityScore: number;
  };
  microstructure: {
    bestBid: number;
    bestAsk: number;
    spread: number;
    spreadBps: number;
    midPrice: number;
    buyVolume: number;
    sellVolume: number;
    buySellRatio: number;
  };
}

export interface AiAnalysisResult {
  symbol: string;
  interval: string;
  marketBias: string;
  confidence: number;
  summary: string;
  supportingFactors: string[];
  riskFactors: string[];
  model: string;
  analyzedAt: string;
}
