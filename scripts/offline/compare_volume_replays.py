#!/usr/bin/env python3
"""Integrity-gated offline comparison of actual replayed volume confirmation.

Month-by-month retrospective only. Replays complete candidate chronology,
unlike filtering historical winner/loser rows. No orders, APIs or DB.
"""
import argparse
import csv
from decimal import Decimal, InvalidOperation
import os
from pathlib import Path
import re
import subprocess
import sys

from compare_historical_months import parse_report, parse_scenarios
from validate_frozen_candidates import FROZEN

FIELDS = ("month", "direction", "bars", "baselineTrades", "baselineNet",
          "gatedTrades", "gatedNet", "gatedDrawdown", "gatedFees", "deltaNet")


def parse_volume_replays(output, month, scenarios):
    rows = []
    scenario_index = {(s["direction"], s["bars"]): s for s in scenarios}
    for line in output.splitlines():
        if "COINMIND VOLUME REPLAY month=" not in line:
            continue
        fields = dict(re.findall(r"([A-Za-z]+)=([^\s]+)", line))
        required = FIELDS[:-1]
        if any(field not in fields for field in required):
            raise ValueError("Incomplete volume replay")
        key = (fields["direction"], fields["bars"])
        if fields["month"] != month or key not in FROZEN:
            raise ValueError("Unexpected volume replay scenario")
        baseline = scenario_index.get(key)
        if baseline is None:
            raise ValueError("Missing reference scenario")
        if int(fields["baselineTrades"]) != int(baseline["executed"]):
            raise ValueError("Baseline trade-count mismatch")
        try:
            net = Decimal(fields["baselineNet"])
            gated = Decimal(fields["gatedNet"])
            drawdown = Decimal(fields["gatedDrawdown"])
            fees = Decimal(fields["gatedFees"])
            count = int(fields["gatedTrades"])
        except (InvalidOperation, ValueError) as exc:
            raise ValueError("Malformed volume replay numbers") from exc
        if any(not x.is_finite() for x in (net, gated, drawdown, fees)):
            raise ValueError("Nonfinite volume replay numbers")
        if net != Decimal(baseline["netPnl"]) or min(count, drawdown, fees) < 0:
            raise ValueError("Volume replay does not reconcile")
        if (count == 0 and (gated != 0 or fees != 0 or drawdown != 0)):
            raise ValueError("Empty gated replay must have zero result")
        row = {field: fields[field] for field in required}
        row["deltaNet"] = str(gated - net)
        rows.append(row)
    seen = {(r["direction"], r["bars"]) for r in rows}
    if len(rows) != 3 or seen != set(FROZEN):
        raise ValueError("Expected exactly three frozen volume replay scenarios")
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backend", default="backend")
    parser.add_argument("--dataset-root", required=True)
    parser.add_argument("--months", nargs="+", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    if len(args.months) != len(set(args.months)) or any(
            not re.fullmatch(r"20\d{2}-(0[1-9]|1[0-2])", month) for month in args.months):
        parser.error("Distinct YYYY-MM months required")
    dest = Path(args.output)
    if dest.exists():
        parser.error("Output path already exists")
    backend = Path(args.backend).resolve(strict=True)
    root = Path(args.dataset_root).resolve(strict=True)
    rows = []
    for month in args.months:
        dataset = (root / f"coinmind-btcusdt-{month}-volume").resolve(strict=True)
        if not dataset.is_dir() or not dataset.is_relative_to(root):
            raise ValueError("Dataset outside trusted root")
        env = os.environ.copy()
        env["COINMIND_HISTORICAL_DATASET_DIR"] = str(dataset)
        process = subprocess.run(
            ["mvn", "-Dtest=FuturesHistoricalDatasetReplayIT", "test"],
            cwd=backend, env=env, capture_output=True, text=True, timeout=240)
        if process.returncode:
            sys.stderr.write(process.stdout[-4500:] + process.stderr[-1500:])
            raise RuntimeError(f"Verified volume replay failed for {month}")
        report = parse_report(process.stdout, month)
        scenarios = parse_scenarios(process.stdout, month, report)
        partition = parse_volume_replays(process.stdout, month, scenarios)
        rows.extend(partition)
        for row in partition:
            print("VOLUME REPLAY month={month} direction={direction} bars={bars} "
                  "baselineTrades={baselineTrades} gatedTrades={gatedTrades} "
                  "baselineNet={baselineNet} gatedNet={gatedNet} "
                  "deltaNet={deltaNet} gatedDrawdown={gatedDrawdown}".format(**row))
    with dest.open("x", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    print("Saved:", dest)
    print("RESEARCH ONLY: all supplied months were previously inspected; no prospective evidence.")


if __name__ == "__main__":
    main()
