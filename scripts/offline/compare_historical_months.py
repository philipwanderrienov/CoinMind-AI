#!/usr/bin/env python3
"""Read-only monthly out-of-sample backtest launcher. No orders or DB writes."""
import argparse
import csv
from decimal import Decimal
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


GROUP_FIELDS = ("month", "dimension", "category", "count", "wins", "losses",
                "gross", "fees", "net", "averageNet")
GROUP_CATEGORIES = {"side": ("LONG", "SHORT"),
                    "exit": ("STOP", "TARGET", "TIME_EXIT")}


def parse_groups(output, expected_month, monthly_report):
    """Require complete grouping of every realized trade; reject partial Maven output."""
    rows = []
    for line in output.splitlines():
        if "COINMIND HISTORICAL GROUP " not in line:
            continue
        fields = dict(re.findall(r"([A-Za-z]+)=([^\s]+)", line))
        dimensions = [name for name in GROUP_CATEGORIES if name in fields]
        if len(dimensions) != 1:
            raise ValueError("Ambiguous historical group dimension")
        dimension = dimensions[0]
        category = fields[dimension]
        if category not in GROUP_CATEGORIES[dimension]:
            raise ValueError("Unknown historical trade category")
        if any(key not in fields for key in ("count", "wins", "losses",
                                             "gross", "fees", "net", "averageNet")):
            raise ValueError("Incomplete historical trade breakdown")
        row = {"month": expected_month, "dimension": dimension, "category": category}
        row.update({key: fields[key] for key in GROUP_FIELDS[3:]})
        rows.append(row)
    if len(rows) != 5 or len({(r["dimension"], r["category"]) for r in rows}) != 5:
        raise ValueError("Expected exactly five unique historical trade groups")
    total_trades = int(monthly_report["executed"])
    expected_pnl = Decimal(monthly_report["netPnl"])
    for dimension, categories in GROUP_CATEGORIES.items():
        part = [r for r in rows if r["dimension"] == dimension]
        if {r["category"] for r in part} != set(categories):
            raise ValueError("Missing trade group")
        if sum(int(r["count"]) for r in part) != total_trades:
            raise ValueError("Grouped trade count disagrees with monthly total")
        if sum((Decimal(r["net"]) for r in part), Decimal(0)) != expected_pnl:
            raise ValueError("Grouped net PnL disagrees with monthly total")
        for group in part:
            count = int(group["count"])
            wins = int(group["wins"])
            losses = int(group["losses"])
            if min(count, wins, losses) < 0 or wins + losses > count:
                raise ValueError("Invalid group win/loss count")
            if Decimal(group["fees"]) < 0:
                raise ValueError("Negative transaction fees")
            # Existing engine uses decimal64 for per-trade PnL, so allow only
            # tiny rounding difference when comparing grouped gross-fees.
            if abs(Decimal(group["gross"]) - Decimal(group["fees"])
                   - Decimal(group["net"])) > Decimal("0.00000001"):
                raise ValueError("Inconsistent grouped gross/fees/net PnL")
            if count == 0 and group["averageNet"] != "null":
                raise ValueError("Missing group must have null average")
    return rows

SCENARIO_FIELDS = ("month", "direction", "bars", "executed", "wins", "losses",
                   "winRate", "profitFactor", "netPnl", "maxDrawdownQuote")


def parse_scenarios(output, expected_month, monthly_report):
    """Exactly the predeclared 2x4 research grid, no post-hoc selection."""
    rows = []
    for line in output.splitlines():
        if "COINMIND EXPERIMENTAL SCENARIO month=" not in line:
            continue
        fields = dict(re.findall(r"([A-Za-z]+)=([^\\s]+)", line))
        if any(name not in fields for name in SCENARIO_FIELDS):
            raise ValueError("Incomplete experimental scenario")
        if fields["month"] != expected_month or fields["direction"] not in ("BOTH", "LONG_ONLY"):
            raise ValueError("Unexpected experiment identity")
        if fields["bars"] not in ("2", "3", "6", "12"):
            raise ValueError("Unexpected experimental horizon")
        rows.append({name: fields[name] for name in SCENARIO_FIELDS})
    keys = {(row["direction"], row["bars"]) for row in rows}
    if len(rows) != 8 or len(keys) != 8:
        raise ValueError("Expected eight distinct experimental scenarios")
    baseline = next(row for row in rows if row["direction"] == "BOTH" and row["bars"] == "2")
    for key in ("executed", "netPnl", "wins", "losses", "maxDrawdownQuote"):
        if (Decimal(baseline[key]) != Decimal(monthly_report[key])
                if key != "executed" and key != "wins" and key != "losses"
                else int(baseline[key]) != int(monthly_report[key])):
            raise ValueError("Experimental baseline disagrees with historical report")
    for row in rows:
        executed, wins, losses = (int(row[k]) for k in ("executed", "wins", "losses"))
        if min(executed, wins, losses) < 0 or wins + losses > executed:
            raise ValueError("Invalid experimental outcome counts")
        if not Decimal(row["netPnl"]).is_finite() or Decimal(row["maxDrawdownQuote"]) < 0:
            raise ValueError("Nonfinite experimental statistics")
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backend", default="backend")
    parser.add_argument("--dataset-root", required=True, help="Directory holding coinmind-btcusdt-YYYY-MM folders")
    parser.add_argument("--months", nargs="+", default=["2026-07", "2026-08", "2026-09"])
    parser.add_argument("--output", required=True, help="New monthly CSV output path")
    parser.add_argument("--groups-output", required=True, help="New per-side/exit CSV output path")
    parser.add_argument("--experiments-output", help="Optional new scenario comparison CSV path")
    args = parser.parse_args()
    if len(set(args.months)) != len(args.months):
        parser.error("Duplicate months")
    if any(not re.fullmatch(r"20\d{2}-(0[1-9]|1[0-2])", month) for month in args.months):
        parser.error("Months must use YYYY-MM")
    backend = Path(args.backend).resolve(strict=True)
    root = Path(args.dataset_root).resolve(strict=True)
    output = Path(args.output)
    group_output = Path(args.groups_output)
    experiments_output = Path(args.experiments_output) if args.experiments_output else None
    paths = [output, group_output] + ([experiments_output] if experiments_output else [])
    if len(set(paths)) != len(paths) or any(path.exists() for path in paths):
        parser.error("All output paths must be distinct and nonexistent")
    results = []
    all_groups = []
    all_scenarios = []
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
        groups = parse_groups(result.stdout, month, row)
        results.append(row)
        all_groups.extend(groups)
        if experiments_output:
            all_scenarios.extend(parse_scenarios(result.stdout, month, row))
        print("Validated:", month, "executed:", row["executed"], "netPnl:", row["netPnl"])
    with output.open("x", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=REQUIRED)
        writer.writeheader()
        writer.writerows(results)
    with group_output.open("x", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=GROUP_FIELDS)
        writer.writeheader()
        writer.writerows(all_groups)
    if experiments_output:
        with experiments_output.open("x", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=SCENARIO_FIELDS)
            writer.writeheader()
            writer.writerows(all_scenarios)
        print("Research scenario comparison saved:", experiments_output)
    print("Read-only historical comparison saved:", output)
    print("Trade side/exit diagnostics saved:", group_output)
    total_net = sum((Decimal(row["netPnl"]) for row in results), Decimal(0))
    side_groups = [row for row in all_groups if row["dimension"] == "side"]
    total_fees = sum((Decimal(row["fees"]) for row in side_groups), Decimal(0))
    print("COINMIND MULTI-MONTH DIAGNOSTICS months=" + str(len(results))
          + " trades=" + str(sum(int(row["executed"]) for row in results))
          + " netPnl=" + str(total_net) + " tradingFees=" + str(total_fees)
          + " DISCLAIMER=independent_monthly_replays_not_compounded_portfolio")
    print("No parameter selection: July/August are earlier holdout comparisons, not prospective validation.")


if __name__ == "__main__":
    main()
