import unittest
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from analyze_entry_volume import join_entry_rows


class EntryVolumeDiagnosticsTests(unittest.TestCase):
    def candles(self, count=30):
        start = datetime(2026, 6, 1, tzinfo=timezone.utc)
        return [
            (start + timedelta(minutes=5*i), Decimal(i+1),
             Decimal(10*(i+1)), 10, Decimal(i+1)/2, Decimal(5*(i+1)))
            for i in range(count)
        ]

    def entry(self, minute=125):
        return {
            "month": "2026-06", "direction": "BOTH", "bars": "12",
            "decisionUtc": (datetime(2026,6,1,tzinfo=timezone.utc)
                            + timedelta(minutes=minute)).isoformat().replace("+00:00","Z"),
            "side": "LONG", "net": "-1", "decelerating": "false",
            "volatility": "MEDIUM", "rangePercent": "0.7",
            "momentum5m": "80", "momentum15m": "70", "momentum1h": "60",
            "momentum4h": "50", "strength": "75", "aligned": "4",
            "gross": "-0.5", "fees": "0.5",
        }

    def test_rvol_excludes_reference_candle_from_prior_mean(self):
        result = join_entry_rows([self.entry()], {"2026-06": self.candles()})
        self.assertEqual(1, len(result))
        # At minute 125 the last closed bar is index 24, preceding 20 are indices 4..23.
        self.assertEqual(Decimal(25) / Decimal("14.5"), Decimal(result[0]["rvol20"]))
        self.assertEqual("0.5", result[0]["takerBuyBaseShare"])
        self.assertEqual("0.5", result[0]["takerBuyQuoteShare"])

    def test_rejects_future_or_insufficient_history(self):
        with self.assertRaisesRegex(ValueError, "Insufficient"):
            join_entry_rows([self.entry(100)], {"2026-06": self.candles()})
        with self.assertRaisesRegex(ValueError, "Closed-bar"):
            join_entry_rows([self.entry(160)], {"2026-06": self.candles()})

    def test_rejects_missing_month(self):
        with self.assertRaisesRegex(ValueError, "Missing verified"):
            join_entry_rows([self.entry()], {})


if __name__ == "__main__":
    unittest.main()
