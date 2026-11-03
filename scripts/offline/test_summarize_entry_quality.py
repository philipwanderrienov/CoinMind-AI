import unittest
from summarize_entry_quality import summarize


class EntryQualitySummaryTest(unittest.TestCase):
    def row(self, net):
        return {
            "month":"2026-06", "direction":"BOTH", "bars":"2",
            "decisionUtc":"2026-06-01T10:00:00Z", "side":"LONG",
            "volatility":"MEDIUM", "rangePercent":"0.6", "momentum5m":"60",
            "momentum15m":"62", "momentum1h":"70", "momentum4h":"80",
            "strength":"65", "aligned":"4", "decelerating":"true",
            "gross":str(net + 0.5), "fees":"0.5", "net":str(net)
        }

    def test_groups_and_fees_reconcile(self):
        grouped = summarize([self.row(1), self.row(-2)])
        self.assertEqual(1, len(grouped))
        self.assertEqual("2", grouped[0]["trades"])
        self.assertEqual("1", grouped[0]["wins"])
        self.assertEqual("1.0", grouped[0]["fees"])
        self.assertEqual("-1", grouped[0]["net"])

    def test_rejects_corrupt_cost_record(self):
        corrupted = self.row(1)
        corrupted["gross"] = "99"
        with self.assertRaisesRegex(ValueError, "mismatch"):
            summarize([corrupted])


if __name__ == "__main__":
    unittest.main()
