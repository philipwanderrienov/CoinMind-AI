import unittest
from analyze_taker_pressure import aggregate, classify_share, BUY, SELL, EVEN, ZERO
from decimal import Decimal


class TakerPressureResearchTests(unittest.TestCase):
    def entry(self, **updates):
        row = {
            "month": "2026-06", "direction": "BOTH", "bars": "12",
            "decisionUtc": "2026-06-01T15:00:00Z", "side": "LONG",
            "net": "-2.5", "rvol20": "1.4", "volumeBase": "100",
            "volumeQuote": "10000", "tradeCount": "150",
            "takerBuyBaseShare": "0.7", "takerBuyQuoteShare": "0.68",
            "decelerating": "false",
        }
        row.update(updates)
        return row

    def test_classification_neutral_and_zero_are_not_buy_or_sell(self):
        self.assertEqual(BUY, classify_share(Decimal("0.51")))
        self.assertEqual(SELL, classify_share(Decimal("0.49")))
        self.assertEqual(EVEN, classify_share(Decimal("0.5")))
        self.assertEqual(ZERO, classify_share(None))

    def test_grouping_keeps_month_side_and_pnl_separate(self):
        a = self.entry()
        b = self.entry(month="2026-07", net="4",
                       decisionUtc="2026-07-02T10:00:00Z")
        c = self.entry(side="SHORT", takerBuyBaseShare="0.25",
                       decisionUtc="2026-06-01T16:00:00Z")
        result = aggregate([a, b, c])
        self.assertEqual(3, len(result))
        self.assertEqual("-2.5",
                         next(row["grossNetPnl"] for row in result
                              if row["month"] == "2026-06" and row["side"] == "LONG"))
        self.assertEqual({"BUY_DOMINANT", "SELL_DOMINANT"},
                         {row["takerPressure"] for row in result})

    def test_rejects_duplicate_decision_within_candidate(self):
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            aggregate([self.entry(), self.entry(net="9")])

    def test_rejects_invalid_shares_and_nonfinite_data(self):
        with self.assertRaisesRegex(ValueError, "Invalid taker buy share"):
            aggregate([self.entry(takerBuyBaseShare="1.2")])
        with self.assertRaisesRegex(ValueError, "Nonfinite"):
            aggregate([self.entry(net="NaN")])
        with self.assertRaisesRegex(ValueError, "Invalid zero-volume share"):
            aggregate([self.entry(volumeBase="0", takerBuyBaseShare="null")])

    def test_no_volume_is_not_mislabeled_directional_pressure(self):
        result = aggregate([self.entry(volumeBase="0", volumeQuote="0",
                                       takerBuyBaseShare="null", takerBuyQuoteShare="null")])
        self.assertEqual("NO_VOLUME", result[0]["takerPressure"])
        self.assertEqual("null", result[0]["avgTakerBuyBaseShare"])


if __name__ == "__main__":
    unittest.main()
