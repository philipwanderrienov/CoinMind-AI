#!/usr/bin/env python3
"""Read-only monthly out-of-sample backtest launcher. No orders or DB writes."""
import argparse
import csv
import os
import re
import subprocess
import sys
from pathlib import Path

REQUIRED = ("month", "market", "symbol", "executed", "waits", "riskBlocked",
            "initialEquity", "finalEquity", "netPnl", "maxDrawdownQuote",
            "wins", "losses", "winRatePercent", "profitFactor")


def parse_report(output, expected_month):
    lines = [line.strip() for line in output.splitlines()
             if "COINMIND HISTORICAL OFFLINE REPORT month=" in line]
    if len(lines) != 1:
        raise ValueError("Expected precisely one verified offline report")
    fields = dict(re.findall(r"([A-Za-z]+)=([^\s]+)", lines[0]))
    if any(key not in fields for key in REQUIRED):
        raise ValueError("Historical report missing required metrics")
    if fields["month"] != expected_month or fields["market"] != "USD_M_FUTURES" or fields["symbol"] != "BTCUSDT":
        raise ValueError("Historical report dataset identity mismatch")
    return {key: fields[key] for key in REQUIRED}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backend", default="backend")
    parser.add_argument("--dataset-root", required=True, help="Directory holding coinmind-btcusdt-YYYY-MM folders")
    parser.add_argument("--months", nargs="+", default=["2026-07", "2026-08", "2026-09"])
    parser.add_argument("--output", required=True, help="New CSV output path")
    args = parser.parse_args()
    if len(set(args.months)) != len(args.months):
        parser.error("Duplicate months")
    if any(not re.fullmatch(r"20\d{2}-(0[1-9]|1[0-2])", month) for month in args.months):
        parser.error("Months must use YYYY-MM")
    backend = Path(args.backend).resolve(strict=True)
    root = Path(args.dataset_root).resolve(strict=True)
    output = Path(args.output)
    if output.exists():
        parser.error("Output already exists; choose a new path")
    results = []
    for month in args.months:
        dataset = (root / ("coinmind-btcusdt-" + month)).resolve(strict=True)
        if not dataset.is_dir() or not dataset.is_relative_to(root):
            raise ValueError("Dataset directory outside trusted root")
        env = os.environ.copy()
        env["COINMIND_HISTORICAL_DATASET_DIR"] = str(dataset)
        result = subprocess.run(
            ["mvn", "-Dtest=FuturesHistoricalDatasetReplayIT", "test"],
            cwd=backend, env=env, text=True, capture_output=True, timeout=180)
        if result.returncode != 0:
            sys.stderr.write(result.stdout[-3500:] + result.stderr[-1500:])
            raise RuntimeError(f"Verified replay failed for {month}; comparison aborted")
        row = parse_report(result.stdout, month)
        results.append(row)
        print("Validated:", month, "executed:", row["executed"], "netPnl:", row["netPnl"])
    with output.open("x", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=REQUIRED)
        writer.writeheader()
        writer.writerows(results)
    print("Read-only historical comparison saved:", output)
    print("No parameter selection: July/August are earlier holdout comparisons, not prospective validation.")


if __name__ == "__main__":
    main()
