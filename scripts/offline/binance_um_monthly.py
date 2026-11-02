#!/usr/bin/env python3
"""Opt-in historical USD-M futures klines downloader; never touches live trading APIs.

Downloads four official Binance public-data monthly ZIP archives and companion
.CHECKSUM files. It verifies ZIP SHA-256 before extracting, converts the 12-field
kline CSV rows to CoinMind's five-field UTC format, and writes a provenance
manifest with SHA-256 of each normalized CSV. No API keys or DB access.
"""
import argparse
import csv
import hashlib
import io
import json
import re
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path

BASE = "https://data.binance.vision/data/futures/um/monthly/klines"
FRAMES = ("5m", "15m", "1h", "4h")
HEADER = "open_time_utc,open,high,low,close"
MAX_ZIP_BYTES = 16_000_000
MAX_CSV_BYTES = 32_000_000


def download(url):
    request = urllib.request.Request(url, headers={"User-Agent": "CoinMind-Offline-History/1.0"})
    with urllib.request.urlopen(request, timeout=35) as response:
        if response.geturl() != url:
            raise ValueError("Redirected archive URL rejected")
        data = response.read(MAX_ZIP_BYTES + 1)
        if len(data) > MAX_ZIP_BYTES:
            raise ValueError("Download too large")
        return data


def normalize(zip_data, expected_csv_name, frame):
    with zipfile.ZipFile(io.BytesIO(zip_data)) as z:
        members = z.infolist()
        if len(members) != 1 or members[0].filename != expected_csv_name:
            raise ValueError("Unexpected archive members")
        if members[0].file_size > MAX_CSV_BYTES:
            raise ValueError("Extracted CSV too large")
        raw = z.read(members[0])
    lines = csv.reader(io.StringIO(raw.decode("utf-8-sig")))
    output = [HEADER]
    prior = None
    step = {"5m":300, "15m":900, "1h":3600, "4h":14400}[frame]
    count = 0
    for cells in lines:
        if cells and cells[0].strip().lower() in ("open_time", "open time"):
            continue
        if len(cells) != 12:
            raise ValueError("Expected 12 Binance kline fields")
        stamp_text = cells[0].strip()
        if not stamp_text.isascii() or not stamp_text.isdecimal():
            raise ValueError(f"{frame} row {count + 1}: invalid timestamp {stamp_text!r}")
        stamp = int(stamp_text)
        # Detect explicit 13-digit millisecond and 16-digit microsecond encoding.
        if len(stamp_text) == 13:
            divisor = 1000
        elif len(stamp_text) == 16:
            divisor = 1000000
        else:
            raise ValueError(f"{frame} row {count + 1}: unexpected timestamp units ({len(stamp_text)} digits)")
        if stamp % divisor:
            raise ValueError(f"{frame} row {count + 1}: subsecond candle open")
        sec = stamp // divisor
        if sec % step:
            raise ValueError(f"{frame} row {count + 1}: unaligned candle open {datetime.fromtimestamp(sec, timezone.utc).isoformat()}")
        if prior is not None and sec - prior != step:
            raise ValueError(
                f"{frame} row {count + 1}: noncontiguous open times, "
                f"expected {datetime.fromtimestamp(prior + step, timezone.utc).isoformat()}, "
                f"got {datetime.fromtimestamp(sec, timezone.utc).isoformat()}, "
                f"delta_seconds={sec - prior}; no missing data were filled")
        from decimal import Decimal
        o, h, l, c = map(Decimal, cells[1:5])
        if min(o, h, l, c) <= 0 or h < max(o, c) or l > min(o, c):
            raise ValueError("Invalid OHLC")
        output.append(",".join([datetime.fromtimestamp(sec, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                                *cells[1:5]]))
        count += 1
        prior = sec
    if count < 3 or count > 100_000:
        raise ValueError("Invalid monthly candle count")
    return ("\n".join(output) + "\n").encode("utf-8"), count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--month", required=True, help="Completed UTC month YYYY-MM")
    parser.add_argument("--output", required=True, help="New output directory")
    args = parser.parse_args()
    if not re.fullmatch(r"20\d{2}-(0[1-9]|1[0-2])", args.month):
        parser.error("Month must be YYYY-MM")
    now = datetime.now(timezone.utc).strftime("%Y-%m")
    if args.month >= now:
        parser.error("Only completed months allowed")
    out = Path(args.output)
    out.mkdir(parents=True, exist_ok=False)
    manifests = []
    for frame in FRAMES:
        name = f"BTCUSDT-{frame}-{args.month}"
        url = f"{BASE}/BTCUSDT/{frame}/{name}.zip"
        checksum_url = url + ".CHECKSUM"
        blob = download(url)
        checksum_text = download(checksum_url).decode("ascii").strip()
        entry = checksum_text.split()
        if len(entry) != 2 or entry[1].lstrip("*") != name + ".zip" or not re.fullmatch("[a-fA-F0-9]{64}", entry[0]):
            raise ValueError("Unexpected upstream checksum format")
        zip_hash = hashlib.sha256(blob).hexdigest()
        if zip_hash.lower() != entry[0].lower():
            raise ValueError("Upstream SHA-256 ZIP checksum mismatch")
        normalized, count = normalize(blob, name + ".csv", frame)
        dst = out / f"{frame}.csv"
        dst.write_bytes(normalized)
        manifests.append({"timeframe":frame, "file":dst.name, "source_url":url,
                          "upstream_zip_sha256":zip_hash,
                          "sha256":hashlib.sha256(normalized).hexdigest(), "candles":count})
    manifest = {"source":"Binance public USD-M monthly klines (CHECKSUM verified)",
                "source_documentation":"https://github.com/binance/binance-public-data",
                "market":"USD_M_FUTURES", "symbol":"BTCUSDT",
                "provenance":"HISTORICAL_REPLAY", "month_utc":args.month,
                "files":manifests}
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2)+"\n", encoding="utf-8")
    print(f"Downloaded and checksum-verified 4 BTCUSDT USD-M candle datasets to {out}")
    print("Inspect manifest/source provenance independently before running historical replay.")


if __name__ == "__main__":
    main()
