# CoinMind-AI: Actual news capture and post-freeze volume validation

## Scope and safety

This is an **offline research** workflow. News is not inserted into live signals,
the production database, Manifold Markets or real order execution. Advisor-only
and live Futures BLOCKED remain unchanged.

An article's `publishedUtc` is what the publisher reports. `firstSeenUtc` is
the UTC time the OFFLINE collector fetched the feed successfully on this server.
**Do not backdate firstSeenUtc** to a web page publication timestamp or a date
retrospectively discovered in 2026-06..2026-09. News published during June to
September 2026 but discovered in November cannot have affected those decisions.

## Capture an authentic feed (on phive-server)

The collector is opt-in; it makes **one network fetch per invocation**. It is
NOT a daemon or automatically scheduled. It supports RSS 2.0 and Atom, HTTPS
only on explicitly allowed publisher domains. Publisher feeds may change schema
or require operator review; failures must not be replaced with fabricated data.

Example Federal Reserve *monetary policy* RSS feed (official):

```bash
cd /home/phive/Documents/CoinMind-AI
python3 scripts/offline/capture_news_rss.py \
  --feed-url https://www.federalreserve.gov/feeds/press_monetary.xml \
  --source "Federal Reserve" \
  --archive /home/phive/coinmind-news-first-seen.csv
```

Example CoinDesk RSS feed (publisher feed, independently validate content):

```bash
python3 scripts/offline/capture_news_rss.py \
  --feed-url 'https://www.coindesk.com/arc/outboundfeeds/rss/?outputType=xml' \
  --source "CoinDesk" \
  --archive /home/phive/coinmind-news-first-seen.csv
```

RSS collector follows at most 3 redirect hops, and **every redirect destination must remain HTTPS and inside the approved publisher domain allowlist**. Publisher feeds that redirect externally fail closed; use an operator-confirmed approved final URL instead. CoinDesk's `?outputType=xml` URL is a documented alternate XML endpoint and may avoid a redirect, but success depends on server-side network access. The 2026-11-04 server run of the original CoinDesk URL was blocked by the original fail-closed redirect policy, not by the archive parser. Verify first before scheduling collection.\n\nRepeated invocations append only unseen article IDs, preserving firstSeenUtc
of all prior records, protected by an exclusive local file lock and `fsync`.
The file records **metadata only**: newsId, source, URL, publication timestamp
and first-capture timestamp. It does not store the article body or sentiment,
and publisher publication timestamps are not independently cryptographically
verified. Restrict write permissions and back up the capture log independently;
CSV is *not* a tamper-evident archive.

Use this existing research-only audit once you have a genuine capture archive
AND new historical entry records from the same later period:

```bash
python3 scripts/offline/audit_news_at_decision.py \
  --entries /path/to/new-entry-quality.csv \
  --news-archive /home/phive/coinmind-news-first-seen.csv \
  --output /home/phive/coinmind-news-availability-audit-v1.csv
```

The audit reports availability only, not whether the news is BTC-relevant,
positive/negative, or price-moving. No historical news-based PnL is claimed.

## Locked historical volume-filter comparison

The optional filter already compares baseline against full chronological replays:
closed 5m volume RVOL20 >= 1, taker-buy fraction > 0.5 LONG or < 0.5 SHORT,
and non-decelerating 5m momentum; fixed paper equity sizing and risk limits.
These conditions were established by **2026-11-04**, after June–September
had been inspected. Those four months remain **retrospective exploratory**
and cannot be relabeled prospective. October has an observed kline data gap
and was rejected by the data-integrity gate; never fill it with synthetic bars.

December 2026 is the **first entire calendar month after the frozen rules**.
It becomes eligible for a completed-month future holdout only in January 2027,
subject to independently confirming no one inspected or tuned against it.

Once December ends and the official USD-M monthly archives are available:

```bash
python3 scripts/offline/binance_um_monthly.py \
  --month 2026-12 --include-volume \
  --output /home/phive/coinmind-btcusdt-2026-12-volume

python3 scripts/offline/compare_volume_replays.py \
  --backend backend --dataset-root /home/phive \
  --months 2026-12 --phase future_holdout \
  --output /home/phive/coinmind-volume-holdout-2026-12.csv
```

Do **NOT** run these December commands before January 2027. The completed-month
guard explicitly rejects it before then. The tool cannot prove a dataset was
unseen; record this fact separately. No tuning based on held-out results after
inspection, and no live Futures activation even with a positive result.
