import unittest
from validate_frozen_candidates import frozen_comparison, FROZEN, parse_regimes


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

    def test_regime_bands_reconcile_all_frozen_scenarios(self):
        scenarios = self.scenarios()
        lines = []
        for direction, bars in FROZEN:
            for band, count, wins, losses, pnl in (
                    ("LOW", 2, 1, 1, "-5" if bars == "2" else "1"),
                    ("MEDIUM", 0, 0, 0, "0"), ("HIGH", 0, 0, 0, "0")):
                lines.append(
                    f"COINMIND REGIME DIAGNOSTIC month=2026-06 direction={direction} "
                    f"bars={bars} volatility={band} count={count} wins={wins} losses={losses} "
                    f"netPnl={pnl} averageNet={'null' if count == 0 else '-2.5'}")
        rows = parse_regimes("\n".join(lines), "2026-06", scenarios)
        self.assertEqual(9, len(rows))

    def test_regime_missing_band_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "nine distinct"):
            parse_regimes("COINMIND REGIME DIAGNOSTIC month=2026-06 "
                          "direction=BOTH bars=2 volatility=LOW count=1 wins=1 losses=0 "
                          "netPnl=1 averageNet=1", "2026-06", self.scenarios())


if __name__ == "__main__":
    unittest.main()
