#!/usr/bin/env python3
"""Point-in-time news availability audit for offline historical trade decisions.

Input must be a real, externally retained news capture archive; this script
never reconstructs first-seen times from publication dates, guesses sentiment,
or inserts historical articles. No news feed => do not claim news backtesting.
"""
import argparse
import csv
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import urlsplit

from compare_historical_months import ENTRY_FIELDS

NEWS_FIELDS = ("newsId", "source", "url", "publishedUtc", "firstSeenUtc")
OUTPUT_FIELDS = ("month", "direction", "bars", "decisionUtc", "side",
                 "availableNewsCount", "latestFirstSeenUtc")
MAX_NEWS = 100_000


def utc(value):
    if not value or not value.endswith("Z"):
        raise ValueError("Explicit UTC Z news timestamp required")
    try:
        result = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise ValueError("Invalid UTC news timestamp") from exc
    if result.utcoffset() != timedelta(0):
        raise ValueError("UTC news timestamp required")
    return result


def validate_news(rows):
    records, seen = [], set()
    for row in rows:
        if any(field not in row or not row[field] for field in NEWS_FIELDS):
            raise ValueError("Complete captured news record required")
        if len(row["newsId"]) > 256 or len(row["source"]) > 200:
            raise ValueError("Oversized news identifier")
        url = urlsplit(row["url"])
        if url.scheme != "https" or not url.netloc or url.username or url.password:
            raise ValueError("Valid HTTPS news source URL required")
        published = utc(row["publishedUtc"])
        first_seen = utc(row["firstSeenUtc"])
        if first_seen < published:
            raise ValueError("First seen cannot precede original publication")
        key = row["newsId"]
        if key in seen:
            raise ValueError("Duplicate captured news ID")
        seen.add(key)
        records.append((published, first_seen))
        if len(records) > MAX_NEWS:
            raise ValueError("Too many news records")
    return records


def audit(entries, news_records, lookback_hours=24):
    if not isinstance(lookback_hours, int) or not 1 <= lookback_hours <= 168:
        raise ValueError("Bounded news lookback required")
    window = timedelta(hours=lookback_hours)
    results = []
    for entry in entries:
        if any(field not in entry for field in ENTRY_FIELDS):
            raise ValueError("Complete historical entry row required")
        decision = utc(entry["decisionUtc"])
        # Both timestamps must be <= decision time. Count only events whose
        # actual FIRST CAPTURE occurred inside the requested availability window.
        eligible = [first_seen for published, first_seen in news_records
                    if published <= decision and decision - window <= first_seen <= decision]
        results.append({
            "month": entry["month"], "direction": entry["direction"],
            "bars": entry["bars"], "decisionUtc": entry["decisionUtc"],
            "side": entry["side"], "availableNewsCount": str(len(eligible)),
            "latestFirstSeenUtc": max(eligible).isoformat().replace("+00:00", "Z")
                if eligible else "null",
        })
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--entries", required=True, help="Verified historical entry quality CSV")
    parser.add_argument("--news-archive", required=True,
                        help="Actual historical capture CSV with firstSeenUtc; no synthetic events")
    parser.add_argument("--output", required=True, help="New output CSV")
    parser.add_argument("--lookback-hours", type=int, default=24)
    args = parser.parse_args()
    output = Path(args.output)
    if output.exists():
        parser.error("Output file must not exist")
    with Path(args.entries).open(newline="", encoding="utf-8") as f:
        entries = list(csv.DictReader(f))
    if not entries:
        raise ValueError("Missing historical trade entries")
    with Path(args.news_archive).open(newline="", encoding="utf-8") as f:
        news = list(csv.DictReader(f))
    if not news:
        raise ValueError("No authenticated historical news capture; refusing invented news")
    rows = audit(entries, validate_news(news), args.lookback_hours)
    with output.open("x", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    print(f"NEWS AVAILABILITY AUDIT entries={len(rows)} "
          f"newsPresent={sum(int(row['availableNewsCount']) > 0 for row in rows)}")
    print("Saved:", output)
    print("Availability only, not sentiment or a news-augmented strategy backtest.")


if __name__ == "__main__":
    main()
