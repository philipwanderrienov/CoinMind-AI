#!/usr/bin/env python3
"""Join verified Binance 5m volumes to historical entry decisions; no trading signals.

RVOL is the last fully closed 5m candle's BASE volume divided by the prior
20 fully closed 5m candle mean (excluding the reference candle). This is not
order-book volume, delta, CVD, or proof of directional pressure.
"""
import argparse
import csv
from datetime import datetime, timezone, timedelta
from decimal import Decimal, InvalidOperation
import hashlib
import json
from pathlib import Path

from compare_historical_months import ENTRY_FIELDS

HEADER = ("month", "direction", "bars", "decisionUtc", "side", "net",
          "rvol20", "volumeBase", "volumeQuote", "tradeCount",
          "takerBuyBaseShare", "takerBuyQuoteShare", "decelerating")


def load_verified_month(directory, month):
    directory = Path(directory)
    manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
    if (manifest.get("provenance") != "HISTORICAL_REPLAY"
            or manifest.get("market") != "USD_M_FUTURES"
            or manifest.get("symbol") != "BTCUSDT" or manifest.get("month_utc") != month):
        raise ValueError("Volume manifest identity mismatch")
    meta = manifest.get("volume_5m")
    if not isinstance(meta, dict) or meta.get("schema") != "BINANCE_UM_5M_VOLUME_V1" or meta.get("file") != "volume-5m.csv":
        raise ValueError("Verified volume sidecar missing")
    expected_source = (f"https://data.binance.vision/data/futures/um/monthly/"
                       f"klines/BTCUSDT/5m/BTCUSDT-5m-{month}.zip")
    if meta.get("source_url") != expected_source:
        raise ValueError("Unexpected volume provenance")
    payload = (directory / "volume-5m.csv").read_bytes()
    if len(payload) > 32_000_000 or hashlib.sha256(payload).hexdigest() != meta.get("sha256"):
        raise ValueError("Volume sidecar hash mismatch")
    prices_bytes = (directory / "5m.csv").read_bytes()
    files = manifest.get("files")
    if not isinstance(files, list) or len(files) != 4 or files[0].get("file") != "5m.csv":
        raise ValueError("Legacy OHLC manifest missing")
    if hashlib.sha256(prices_bytes).hexdigest() != files[0].get("sha256"):
        raise ValueError("Legacy OHLC checksum mismatch")
    with (directory / "5m.csv").open(newline="", encoding="utf-8") as handle:
        prices = list(csv.DictReader(handle))
    with (directory / "volume-5m.csv").open(newline="", encoding="utf-8") as handle:
        volumes = list(csv.DictReader(handle))
    if len(prices) != len(volumes) or len(volumes) != meta.get("candles"):
        raise ValueError("Volume and OHLC count mismatch")
    from calendar import monthrange
    year, month_number = map(int, month.split("-"))
    expected_count = monthrange(year, month_number)[1] * 24 * 12
    if len(volumes) != expected_count:
        raise ValueError("Incomplete month")
    out = []
    prev = None
    expected_start = datetime(year, month_number, 1, tzinfo=timezone.utc)
    for price, row in zip(prices, volumes, strict=True):
        if price["open_time_utc"] != row["open_time_utc"]:
            raise ValueError("OHLC volume timestamp mismatch")
        stamp = datetime.strptime(row["open_time_utc"], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
        if stamp != (expected_start if prev is None else prev + timedelta(minutes=5)):
            raise ValueError("Unaligned or missing volume candle")
        prev = stamp
        def d(field):
            try:
                value = Decimal(row[field])
            except (KeyError, InvalidOperation) as exc:
                raise ValueError("Invalid volume field") from exc
            if not value.is_finite() or value < 0:
                raise ValueError("Negative or nonfinite volume")
            return value
        base, quote = d("base_volume"), d("quote_volume")
        buy_base, buy_quote = d("taker_buy_base_volume"), d("taker_buy_quote_volume")
        trades = row["trades"]
        if not trades.isascii() or not trades.isdecimal():
            raise ValueError("Invalid trade count")
        if buy_base > base or buy_quote > quote:
            raise ValueError("Taker buy exceeds total")
        out.append((stamp, base, quote, int(trades), buy_base, buy_quote))
    return out


def join_entry_rows(entries, datasets):
    output = []
    for entry in entries:
        if any(field not in entry for field in ENTRY_FIELDS):
            raise ValueError("Incomplete historical entry record")
        month = entry["month"]
        if month not in datasets:
            raise ValueError("Missing verified dataset for entry month")
        try:
            decision = datetime.fromisoformat(entry["decisionUtc"].replace("Z", "+00:00"))
        except ValueError as exc:
            raise ValueError("Invalid decision time") from exc
        if decision.utcoffset() != timedelta(0):
            raise ValueError("Decision must be UTC")
        candles = datasets[month]
        start = candles[0][0]
        elapsed = (decision - start).total_seconds()
        if elapsed % 300 or elapsed < 21 * 300:
            raise ValueError("Insufficient complete 5m history at decision time")
        # Last closed bar has open time decision-5m. Never read bar opened at decision.
        index = int(elapsed // 300) - 1
        if index >= len(candles) or candles[index][0] != decision - timedelta(minutes=5):
            raise ValueError("Closed-bar timestamp mismatch")
        reference = candles[index]
        prior = candles[index-20:index]
        if len(prior) != 20 or any(item[1] <= 0 for item in prior):
            raise ValueError("Missing positive historical volume for RVOL20")
        mean = sum((item[1] for item in prior), Decimal(0)) / Decimal(20)
        rvol = reference[1] / mean
        base_share = reference[4] / reference[1] if reference[1] else None
        quote_share = reference[5] / reference[2] if reference[2] else None
        output.append({
            "month":month, "direction":entry["direction"], "bars":entry["bars"],
            "decisionUtc":entry["decisionUtc"], "side":entry["side"], "net":entry["net"],
            "rvol20":str(rvol), "volumeBase":str(reference[1]),
            "volumeQuote":str(reference[2]), "tradeCount":str(reference[3]),
            "takerBuyBaseShare":str(base_share) if base_share is not None else "null",
            "takerBuyQuoteShare":str(quote_share) if quote_share is not None else "null",
            "decelerating":entry["decelerating"],
        })
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset-root", required=True)
    parser.add_argument("--entries", required=True)
    parser.add_argument("--output", required=True, help="New CSV path")
    args = parser.parse_args()
    dest = Path(args.output)
    if dest.exists():
        parser.error("Output must not exist")
    root = Path(args.dataset_root).resolve(strict=True)
    with Path(args.entries).open(newline="", encoding="utf-8") as handle:
        entries = list(csv.DictReader(handle))
    if not entries:
        raise ValueError("Entry records empty")
    months = {row["month"] for row in entries}
    datasets = {}
    for month in months:
        directory = (root / f"coinmind-btcusdt-{month}-volume").resolve(strict=True)
        if not directory.is_relative_to(root):
            raise ValueError("Dataset outside root")
        datasets[month] = load_verified_month(directory, month)
    joined = join_entry_rows(entries, datasets)
    with dest.open("x", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=HEADER)
        writer.writeheader()
        writer.writerows(joined)
    print(f"Validated volume records: {len(joined)} months={','.join(sorted(months))}")
    print("Saved:", dest)
    print("DESCRIPTIVE RETROSPECTIVE ONLY; no signal changes and no prospective proof.")


if __name__ == "__main__":
    main()
