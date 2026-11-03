import unittest
from validate_frozen_candidates import frozen_comparison, FROZEN


class FrozenResearchValidationTest(unittest.TestCase):
    def scenarios(self):
        return [
            {"direction": direction, "bars": bars, "executed": "2", "wins": "1",
             "losses": "1", "netPnl": "-5" if (direction, bars) == ("BOTH", "2") else "1",
             "maxDrawdownQuote": "8"}
            for bars in ("2", "3", "6", "12")
            for direction in ("BOTH", "LONG_ONLY")
        ]

    def test_frozen_candidates_keep_order_and_baseline_delta(self):
        rows = frozen_comparison("2026-06", {"netPnl": "-5"}, self.scenarios())
        self.assertEqual(list(FROZEN), [(r["direction"], str(int(r["horizonMinutes"]) // 5))
                                          for r in rows])
        self.assertEqual(["0", "6", "6"], [r["baselineDeltaUsdt"] for r in rows])
        self.assertTrue(all(r["resultType"].startswith("RETROSPECTIVE") for r in rows))

    def test_rejects_baseline_mismatch(self):
        with self.assertRaisesRegex(ValueError, "Baseline mismatch"):
            frozen_comparison("2026-06", {"netPnl": "-4"}, self.scenarios())

    def test_rejects_incomplete_grid(self):
        with self.assertRaisesRegex(ValueError, "Incomplete research grid"):
            frozen_comparison("2026-06", {"netPnl": "-5"}, self.scenarios()[:-1])


if __name__ == "__main__":
    unittest.main()
