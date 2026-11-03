#!/usr/bin/env python3
"""Descriptive grouping of verified offline entry-quality CSV; never changes strategy."""
import argparse
import csv
from collections import defaultdict
from decimal import Decimal
from pathlib import Path
from compare_historical_months import ENTRY_FIELDS


def summarize(rows):
    totals = defaultdict(lambda: {"trades": 0, "wins": 0, "gross": Decimal(0),
                                  "fees": Decimal(0), "net": Decimal(0)})
    for row in rows:
        if any(field not in row for field in ENTRY_FIELDS):
            raise ValueError("Incomplete entry row")
        if row["decelerating"] not in ("true", "false"):
            raise ValueError("Invalid deceleration flag")
        if row["aligned"] not in ("0", "1", "2", "3", "4"):
            raise ValueError("Invalid alignment count")
        key = (row["direction"], row["bars"], row["side"], row["decelerating"],
               row["aligned"])
        gross, fees, net = (Decimal(row[name]) for name in ("gross", "fees", "net"))
        if any(not v.is_finite() for v in (gross, fees, net)):
            raise ValueError("Nonfinite PnL")
        if abs(gross - fees - net) > Decimal("0.00000001"):
            raise ValueError("Gross fees net mismatch")
        group = totals[key]
        group["trades"] += 1
        group["wins"] += int(net > 0)
        group["gross"] += gross
        group["fees"] += fees
        group["net"] += net
    result = []
    for key, values in sorted(totals.items()):
        result.append(dict(zip(("direction", "bars", "side", "decelerating", "aligned"), key))
                      | {"trades": str(values["trades"]), "wins": str(values["wins"]),
                         "gross": str(values["gross"]), "fees": str(values["fees"]),
                         "net": str(values["net"]),
                         "averageNet": str(values["net"] / values["trades"])})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--entries", required=True, help="Verified entry CSV")
    args = parser.parse_args()
    with Path(args.entries).open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    print("direction,bars,side,decelerating,aligned,trades,wins,gross,fees,net,averageNet")
    for row in summarize(rows):
        print(",".join(row.values()))
    print("Research-only grouped by entry features, not evidence of a predictive signal.")


if __name__ == "__main__":
    main()
