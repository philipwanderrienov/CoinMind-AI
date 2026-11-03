#!/usr/bin/env python3
"""One-month, locked-candidate retrospective validation (no tuning or live orders).

The fixed candidates were picked after reviewing July-September 2026 results.
Earlier months are retrospective holdouts, NEVER prospective/out-of-sample
proof. A failed data check aborts the experiment without writing a report.
"""
import argparse
import csv
from decimal import Decimal
import os
from pathlib import Path
import re
import subprocess
import sys

from compare_historical_months import parse_report, parse_groups, parse_scenarios

# Frozen BEFORE opening the proposed June 2026 holdout.
FROZEN = (("BOTH", "2"), ("BOTH", "12"), ("LONG_ONLY", "12"))
FIELDS = ("month", "candidate", "direction", "horizonMinutes", "trades", "wins",
          "losses", "netPnlUsdt", "maxDrawdownUsdt", "baselineDeltaUsdt",
          "resultType")


def frozen_comparison(month, report, scenarios):
    if len(scenarios) != 8:
        raise ValueError("Incomplete research grid")
    selection = {(row["direction"], row["bars"]): row for row in scenarios}
    if set(FROZEN) - selection.keys():
        raise ValueError("Frozen candidate missing")
    baseline = Decimal(selection[("BOTH", "2")]["netPnl"])
    if baseline != Decimal(report["netPnl"]):
        raise ValueError("Baseline mismatch")
    result = []
    for direction, bars in FROZEN:
        row = selection[(direction, bars)]
        pnl = Decimal(row["netPnl"])
        result.append({
            "month": month, "candidate": "BASELINE" if bars == "2" else "FROZEN_RESEARCH",
            "direction": direction, "horizonMinutes": str(int(bars) * 5),
            "trades": row["executed"], "wins": row["wins"], "losses": row["losses"],
            "netPnlUsdt": str(pnl),
            "maxDrawdownUsdt": str(Decimal(row["maxDrawdownQuote"])),
            "baselineDeltaUsdt": str(pnl - baseline),
            "resultType": "RETROSPECTIVE_UNSEEN_MONTH_NOT_PROSPECTIVE",
        })
    return result


REGIME_FIELDS = ("month", "direction", "bars", "volatility",
                 "count", "wins", "losses", "netPnl", "averageNet")


def parse_regimes(output, month, scenarios):
    """Validate pre-entry volatility partitions against each frozen scenario."""
    rows = []
    for line in output.splitlines():
        if "COINMIND REGIME DIAGNOSTIC month=" not in line:
            continue
        fields = dict(re.findall(r"([A-Za-z]+)=([^\s]+)", line))
        if any(key not in fields for key in REGIME_FIELDS):
            raise ValueError("Incomplete market regime report")
        row = {key: fields[key] for key in REGIME_FIELDS}
        if row["month"] != month or (row["direction"], row["bars"]) not in FROZEN:
            raise ValueError("Unexpected regime report candidate")
        if row["volatility"] not in ("LOW", "MEDIUM", "HIGH"):
            raise ValueError("Unexpected volatility band")
        rows.append(row)
    keys = {(r["direction"], r["bars"], r["volatility"]) for r in rows}
    if len(rows) != 9 or len(keys) != 9:
        raise ValueError("Expected nine distinct frozen volatility groups")
    scenarios_by_key = {(r["direction"], r["bars"]): r for r in scenarios}
    for key in FROZEN:
        subset = [r for r in rows if (r["direction"], r["bars"]) == key]
        expected = scenarios_by_key[key]
        if sum(int(r["count"]) for r in subset) != int(expected["executed"]):
            raise ValueError("Market regime counts mismatch")
        if sum(int(r["wins"]) for r in subset) != int(expected["wins"]):
            raise ValueError("Market regime wins mismatch")
        if sum(int(r["losses"]) for r in subset) != int(expected["losses"]):
            raise ValueError("Market regime losses mismatch")
        if sum((Decimal(r["netPnl"]) for r in subset), Decimal(0)) != Decimal(expected["netPnl"]):
            raise ValueError("Market regime net PnL mismatch")
        for row in subset:
            count, wins, losses = (int(row[k]) for k in ("count", "wins", "losses"))
            if min(count, wins, losses) < 0 or wins + losses > count:
                raise ValueError("Invalid market regime win/loss counts")
            if count == 0 and row["averageNet"] != "null":
                raise ValueError("Empty volatility band must have null average")
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backend", default="backend")
    parser.add_argument("--dataset-root", required=True)
    parser.add_argument("--month", default="2026-06",
                        help="Previously uninspected month, default June 2026")
    parser.add_argument("--output", required=True, help="New CSV output path")
    parser.add_argument("--regimes-output", help="Optional new descriptive regime CSV path")
    args = parser.parse_args()
    if not re.fullmatch(r"20\d{2}-(0[1-9]|1[0-2])", args.month):
        parser.error("Invalid month")
    # Do not reuse the three months that determined the frozen research candidates.
    if args.month in {"2026-07", "2026-08", "2026-09"}:
        parser.error("Exploratory July-September data cannot be a holdout")
    backend = Path(args.backend).resolve(strict=True)
    root = Path(args.dataset_root).resolve(strict=True)
    dataset = (root / ("coinmind-btcusdt-" + args.month)).resolve(strict=True)
    if not dataset.is_dir() or not dataset.is_relative_to(root):
        raise ValueError("Dataset outside trusted root")
    output = Path(args.output)
    regimes_output = Path(args.regimes_output) if args.regimes_output else None
    if output.exists() or (regimes_output is not None and (regimes_output.exists() or regimes_output == output)):
        parser.error("Output paths must be new and distinct")
    env = os.environ.copy()
    env["COINMIND_HISTORICAL_DATASET_DIR"] = str(dataset)
    proc = subprocess.run(
        ["mvn", "-Dtest=FuturesHistoricalDatasetReplayIT", "test"],
        cwd=backend, env=env, capture_output=True, text=True, timeout=180,
    )
    if proc.returncode:
        sys.stderr.write(proc.stdout[-3500:] + proc.stderr[-1500:])
        raise RuntimeError("Verified historical integration test failed; no report saved")
    report = parse_report(proc.stdout, args.month)
    parse_groups(proc.stdout, args.month, report)
    scenarios = parse_scenarios(proc.stdout, args.month, report)
    rows = frozen_comparison(args.month, report, scenarios)
    regime_rows = parse_regimes(proc.stdout, args.month, scenarios) if regimes_output else None
    with output.open("x", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    if regimes_output:
        with regimes_output.open("x", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=REGIME_FIELDS)
            writer.writeheader()
            writer.writerows(regime_rows)
        print("Market regime diagnostics:", regimes_output)
    print("COINMIND FROZEN CANDIDATE RETROSPECTIVE month=" + args.month)
    for row in rows:
        print("direction=" + row["direction"] + " horizonMinutes=" + row["horizonMinutes"]
              + " trades=" + row["trades"] + " netPnl=" + row["netPnlUsdt"]
              + " maxDrawdown=" + row["maxDrawdownUsdt"]
              + " baselineDelta=" + row["baselineDeltaUsdt"])
    print("Report:", output)
    print("RESEARCH ONLY: June precedes July-September; not a prospective test. No parameter selection or live trading.")


if __name__ == "__main__":
    main()
