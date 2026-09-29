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

export type ConnectionState = 'connecting' | 'live' | 'stale' | 'offline';


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
  news: {
    articleCount: number;
    averageSentiment: number;
    positiveCount: number;
    neutralCount: number;
    negativeCount: number;
    topArticles: Array<{
      title: string;
      sentimentScore: number;
      relevanceScore: number;
    }>;
  };
  signal: {
    score: number;
    bias: 'BULLISH' | 'BEARISH' | 'NEUTRAL';
    strength: number;
    triggerEligible: boolean;
    riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
    reasons: string[];
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


export interface NewsArticle {
  id: string;
  title: string;
  source: string;
  url: string;
  summary: string;
  publishedAt: string;
  symbols: string[];
  sentimentScore: number;
  relevanceScore: number;
}

export interface NewsSentimentSummary {
  symbol: string;
  articleCount: number;
  averageSentiment: number;
  positiveCount: number;
  neutralCount: number;
  negativeCount: number;
  recentArticles: NewsArticle[];
  calculatedAt: string;
}


export interface MarketFeedStreamHealth {
  status: 'UP' | 'STALE' | 'STARTING';
  lastEventAt: string | null;
  ageSeconds: number | null;
}

export interface MarketFeedHealth {
  status: 'UP' | 'DEGRADED' | 'STARTING';
  checkedAt: string;
  streams: {
    ticker: MarketFeedStreamHealth;
    kline: MarketFeedStreamHealth;
    orderBook: MarketFeedStreamHealth;
    trade: MarketFeedStreamHealth;
  };
}


export interface AiUsageDaily {
  date: string;
  calls: number;
  inputTokens: number;
  outputTokens: number;
  estimatedCostUsd: number;
}

export interface AiUsageSummary {
  todayCalls: number;
  todayInputTokens: number;
  todayCachedInputTokens: number;
  todayOutputTokens: number;
  todayReasoningTokens: number;
  todayTotalTokens: number;
  todayEstimatedCostUsd: number;
  monthCalls: number;
  monthInputTokens: number;
  monthOutputTokens: number;
  monthEstimatedCostUsd: number;
  daily: AiUsageDaily[];
}

export interface PushPublicKeyResponse {
  enabled: boolean;
  publicKey: string;
}


export interface BillingStatus {
  enabled: boolean;
  provider: string;
  nextPaymentDate: string;
  daysUntilPayment: number;
  reminderDaysBefore: number;
  pushConfigured: boolean;
}


export interface AiEngineStatus {
  state: 'DISABLED' | 'MISSING_API_KEY' | 'MOCK' | 'CONFIGURED_UNVERIFIED' | 'READY' | 'AUTH_ERROR' | 'RATE_LIMITED' | 'ERROR';
  provider: string;
  model: string;
  enabled: boolean;
  apiKeyConfigured: boolean;
  message: string;
  lastHttpStatus: number | null;
  lastCheckedAt: string | null;
}


export interface AiGuardrailStatus {
  minimumAutomaticSignalScore: number;
  automaticCallsThisHour: number;
  automaticCallsToday: number;
  maxAutomaticCallsPerHour: number;
  maxAutomaticCallsPerDay: number;
}


export interface AiAnalysisEvaluation {
  horizon: string;
  exitPrice: number;
  returnPct: number;
  directionCorrect: boolean;
  evaluatedAt: string;
}

export interface AiAnalysisHistoryItem {
  id: string;
  symbol: string;
  interval: string;
  triggerType: string;
  marketBias: string;
  confidence: number;
  summary: string;
  model: string;
  entryPrice: number | null;
  signalScore: number | null;
  analyzedAt: string;
  evaluations: AiAnalysisEvaluation[];
}


export interface AiEvaluationSummary {
  symbol: string;
  interval: string;
  totalAnalyses: number;
  totalEvaluations: number;
  horizons: Array<{
    horizon: string;
    evaluated: number;
    correct: number;
    accuracyPct: number;
    averageReturnPct: number;
  }>;
  biases: Array<{
    marketBias: string;
    evaluated: number;
    correct: number;
    hitRatePct: number;
  }>;
  models: Array<{
    model: string;
    evaluated: number;
    correct: number;
    accuracyPct: number;
    averageReturnPct: number;
  }>;
}


export interface AiDecisionReview {
  symbol: string;
  engineAction: string;
  verdict: 'CONFIRM' | 'WATCH' | 'WAIT' | 'UNAVAILABLE';
  confidence: number;
  summary: string;
  confirmations: string[];
  concerns: string[];
  model: string;
  reviewedAt: string;
}

export interface TradeSetup {
  symbol: string;
  interval: string;
  action: 'WAIT' | 'WATCH_BUY' | 'WATCH_SELL' | 'BUY' | 'SELL';
  side: 'NONE' | 'LONG' | 'SHORT';
  confidence: number;
  signalScore: number;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  alignmentScore: number;
  alignmentLabel: 'STRONG' | 'GOOD' | 'MIXED' | 'WEAK';
  marketRegime: 'TRENDING_BULLISH' | 'TRENDING_BEARISH' | 'SIDEWAYS' | 'HIGH_VOLATILITY' | 'MIXED';
  timeframes: Array<{
    interval: string;
    weight: number;
    signalScore: number;
    trendScore: number;
    momentumScore: number;
    volatilityScore: number;
    bias: 'BULLISH' | 'BEARISH' | 'NEUTRAL';
  }>;
  marketPrice: number;
  entryLow: number | null;
  entryHigh: number | null;
  invalidationPrice: number | null;
  target1: number | null;
  target2: number | null;
  riskReward1: number | null;
  riskReward2: number | null;
  reasons: string[];
  warnings: string[];
  generatedAt: string;
}
