# Gemini free-tier-first news research (OFFLINE_ONLY)

This is an **opt-in research tool**, not production AI switching or permission
to execute trades. It does NOT replace production NewsService, send orders,
enable Futures live trading, or route requests to a paid provider.

## Key and billing safety

1. In Google AI Studio, confirm that the **project associated with this API key**
   is in the Free tier and check its **actual** requests/minute, tokens/minute,
   and requests/day quotas. Quotas are project-specific and change; this code
   cannot prove the key is unbilled.
2. Do not paste your API key into GitHub, this chat, the frontend, or any log.
   Read it invisibly in your own shell and export only to that shell's environment.
3. Google states Free tier content **may be used to improve its products**.
   This pipeline sends ONLY PUBLIC source, headline, and article identifier.
   Never send user credentials, private portfolios, bank data, or API keys.
4. The code selects **gemini-2.5-flash-lite** and never falls back to a paid model.
   It makes at most 3 batches per invocation (5 headlines/batch, default 1);
   it does not retry on HTTP 429/403/503. This is a request guard, NOT a
   provider-enforced cost cap. The operator must monitor quotas and billing.
5. Each item is scored once and cached by newsId + model in local CSV. A repeated
   run does not incur a second inference call for the same stored score.
   Results are *analyst annotations* only, NOT historical price predictions.

## Start with a NEW headline archive

Existing `/home/phive/coinmind-news-first-seen.csv` already contains 40
valid publication-time and actual-capture-time metadata rows, but no headlines.
**Never pretend those article titles were available then.** Create a NEW
headline archive when you fetch articles again now:

```bash
cd /home/phive/Documents/CoinMind-AI
git pull --ff-only origin main
python3 -m unittest discover -s scripts/offline -p 'test_*.py'

python3 scripts/offline/gemini_news_research.py capture \
  --feed-url 'https://www.coindesk.com/arc/outboundfeeds/rss?outputType=xml' \
  --source CoinDesk \
  --archive /home/phive/coinmind-news-headlines-v1.csv

python3 scripts/offline/gemini_news_research.py capture \
  --feed-url 'https://www.federalreserve.gov/feeds/press_monetary.xml' \
  --source 'Federal Reserve' \
  --archive /home/phive/coinmind-news-headlines-v1.csv
```

The new archive is **separate** from the existing 40 metadata records; first
capture means the time the headline itself was actually observed in this
new tool. The RSS title is captured verbatim at observation time.
No old headline or sentiment can be reconstructed as of an earlier timestamp.

## Explicitly budget and score

First check what will be scored, without any Gemini network request:

```bash
python3 scripts/offline/gemini_news_research.py score \
  --archive /home/phive/coinmind-news-headlines-v1.csv \
  --output /home/phive/coinmind-gemini-news-scores-v1.csv
```

Then if and only if the key's Google AI Studio project is verified Free,
set the key privately (shell history will not contain its value):

```bash
read -rsp 'Gemini API key: ' GEMINI_API_KEY; echo
export GEMINI_API_KEY

python3 scripts/offline/gemini_news_research.py score \
  --archive /home/phive/coinmind-news-headlines-v1.csv \
  --output /home/phive/coinmind-gemini-news-scores-v1.csv \
  --max-batches 1 --execute

unset GEMINI_API_KEY
```

Expect the first executed request to analyze at most 5 headlines, one API
request. When rerun, cached articles are skipped and new titles can be scored
incrementally. Scores contain BTC relevance 0..1, bullish/bearish/neutral/
uncertain label, qualitative impact and concise explanation. Scoring is based
ONLY on the headline: no article body, model retrieval, Google Search grounding,
technical price forecast or real-time execution.

The output's `scoredUtc` is the moment the Gemini inference completed, which
MUST be considered the *earliest availability* of its classification for a
point-in-time backtest (not the article `publishedUtc` or headline
`firstSeenUtc`). Before inference actually occurs, model-derived labels are
UNAVAILABLE. Treat model guesses as potentially incorrect; future research
must evaluate accuracy before using any results as a strategy gate.

## Follow-on integration plan

When real headline capture + scoring proves stable: a separate research-only
join should filter on both `firstSeenUtc` and `scoredUtc` <= decisionUTC,
validate title identity, and run a chronological replay against the frozen
Technical+Volume baseline. Keep Manifold Markets only for prediction markets,
and do not change production AI provider or production trading on this step.
