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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backend", default="backend")
    parser.add_argument("--dataset-root", required=True)
    parser.add_argument("--month", default="2026-06",
                        help="Previously uninspected month, default June 2026")
    parser.add_argument("--output", required=True, help="New CSV output path")
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
    if output.exists():
        parser.error("Output already exists; choose a fresh path")
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
    with output.open("x", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)
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
