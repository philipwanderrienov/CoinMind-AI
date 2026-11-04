#!/usr/bin/env python3
"""Read-only taker aggression diagnostics from verified entry+volume CSV.

This does NOT produce order-flow delta, CVD, live signals or a replay-filter
result. It groups historical executed trades; grouped profits cannot be
substituted for a newly simulated strategy.
"""
import argparse
import csv
from collections import defaultdict
from decimal import Decimal, InvalidOperation
from pathlib import Path

from analyze_entry_volume import HEADER

OUTPUT_FIELDS = (
    "month", "direction", "bars", "side", "takerPressure",
    "decelerating", "trades", "wins", "losses", "netPnl",
    "avgNetPnl", "avgTakerBuyBaseShare", "avgRvol20"
)
# Labels only; threshold 50% is not an optimized trade filter.
BUY = "BUY_DOMINANT"
SELL = "SELL_DOMINANT"
EVEN = "BALANCED"
ZERO = "NO_VOLUME"


def classify_share(value):
    if value is None:
        return ZERO
    if value > Decimal("0.5"):
        return BUY
    if value < Decimal("0.5"):
        return SELL
    return EVEN


def _dec(value):
    try:
        result = Decimal(value)
    except (InvalidOperation, TypeError) as exc:
        raise ValueError("Invalid numeric entry-volume field") from exc
    if not result.is_finite():
        raise ValueError("Nonfinite entry-volume field")
    return result


def aggregate(rows):
    """Return fully reconciled summaries split by MONTH, never pool monthly equity."""
    totals = defaultdict(lambda: {
        "trades": 0, "wins": 0, "losses": 0, "net": Decimal(0),
        "share_sum": Decimal(0), "share_count": 0, "rvol_sum": Decimal(0)
    })
    seen = set()
    overall_count = 0
    overall_net = Decimal(0)
    for row in rows:
        if not isinstance(row, dict) or any(key not in row for key in HEADER):
            raise ValueError("Incomplete entry-volume dataset")
        if row["direction"] not in ("BOTH", "LONG_ONLY") or row["bars"] not in ("2", "12"):
            raise ValueError("Unexpected strategy configuration")
        if (row["direction"], row["bars"]) not in
                (("BOTH", "2"), ("BOTH", "12"), ("LONG_ONLY", "12")):
            raise ValueError("Unexpected LONG_ONLY horizon")
        if row["side"] not in ("LONG", "SHORT") or (row["direction"] == "LONG_ONLY" and row["side"] != "LONG"):
            raise ValueError("Invalid trade side")
        if row["decelerating"] not in ("true", "false"):
            raise ValueError("Invalid momentum flag")
        month = row["month"]
        if len(month) != 7 or month[4] != "-" or not month[:4].isdigit() or not month[5:].isdigit() or not 1 <= int(month[5:]) <= 12:
            raise ValueError("Invalid month")
        trade_id = (month, row["direction"], row["bars"], row["decisionUtc"])
        if trade_id in seen:
            raise ValueError("Duplicate settled trade key")
        seen.add(trade_id)
        net = _dec(row["net"])
        rvol = _dec(row["rvol20"])
        base = _dec(row["volumeBase"])
        quote = _dec(row["volumeQuote"])
        if min(rvol, base, quote) < 0:
            raise ValueError("Negative volume or RVOL")
        if (base == 0) != (quote == 0):
            raise ValueError("Invalid zero-volume share")
        share_text = row["takerBuyBaseShare"]
        quote_share_text = row["takerBuyQuoteShare"]
        if share_text == "null":
            if base != 0:
                raise ValueError("Invalid zero-volume share")
            share = None
        else:
            share = _dec(share_text)
            if base == 0 or not 0 <= share <= 1:
                raise ValueError("Invalid taker buy share")
        if quote_share_text == "null":
            if quote != 0:
                raise ValueError("Invalid quote taker share")
        else:
            quote_share = _dec(quote_share_text)
            if quote == 0 or not 0 <= quote_share <= 1:
                raise ValueError("Invalid quote taker buy share")
        key = (month, row["direction"], row["bars"], row["side"],
               classify_share(share), row["decelerating"])
        group = totals[key]
        group["trades"] += 1
        group["wins"] += int(net > 0)
        group["losses"] += int(net < 0)
        group["net"] += net
        group["rvol_sum"] += rvol
        if share is not None:
            group["share_sum"] += share
            group["share_count"] += 1
        overall_count += 1
        overall_net += net
    summaries = []
    for key, group in sorted(totals.items()):
        result = dict(zip(OUTPUT_FIELDS[:6], key))
        result.update({
            "trades": str(group["trades"]),
            "wins": str(group["wins"]),
            "losses": str(group["losses"]),
            "netPnl": str(group["net"]),
            "avgNetPnl": str(group["net"] / group["trades"]),
            "avgTakerBuyBaseShare": (
                str(group["share_sum"] / group["share_count"])
                if group["share_count"] else "null"),
            "avgRvol20": str(group["rvol_sum"] / group["trades"]),
        })
        summaries.append(result)
    if sum(int(row["trades"]) for row in summaries) != overall_count:
        raise ValueError("Grouping lost trades")
    if sum((Decimal(row["netPnl"]) for row in summaries), Decimal(0)) != overall_net:
        raise ValueError("Grouping PnL does not reconcile")
    return summaries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, help="Existing validated entry-with-volume CSV")
    parser.add_argument("--output", required=True, help="New grouped diagnostic CSV, never overwrite")
    args = parser.parse_args()
    output = Path(args.output)
    if output.exists():
        parser.error("Output exists; select a fresh path")
    with Path(args.input).open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    if not rows:
        raise ValueError("No volume-enriched trades found")
    summaries = aggregate(rows)
    with output.open("x", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_FIELDS)
        writer.writeheader()
        writer.writerows(summaries)
    print(f"COINMIND TAKER PRESSURE OBSERVATIONS={len(rows)} GROUPS={len(summaries)}")
    for row in summaries:
        print("month={month} direction={direction} bars={bars} side={side} pressure={takerPressure} "
              "decelerating={decelerating} trades={trades} wins={wins} losses={losses} "
              "netPnl={netPnl}".format(**row))
    print("Saved:", output)
    print("DESCRIPTIVE ONLY: not a replayed/validated filter; no changes to trading.")


if __name__ == "__main__":
    main()
