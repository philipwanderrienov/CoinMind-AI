import unittest
from compare_volume_replays import parse_volume_replays


class VolumeReplayComparisonTests(unittest.TestCase):
    def scenarios(self):
        return [
            {"direction": direction, "bars": str(bars), "executed": "4",
             "netPnl": "-2"}
            for bars in (2, 3, 6, 12)
            for direction in ("BOTH", "LONG_ONLY")
        ]

    def output(self):
        return "\n".join(
            "COINMIND VOLUME REPLAY month=2026-06 direction={direction} bars={bars} "
            "baselineTrades=4 baselineNet=-2 gatedTrades=2 gatedNet=-1 "
            "gatedDrawdown=1 gatedFees=0.4".format(direction=direction, bars=bars)
            for direction, bars in (("BOTH", "2"), ("BOTH", "12"), ("LONG_ONLY", "12"))
        )

    def test_all_three_frozen_scenarios_reconcile(self):
        rows = parse_volume_replays(self.output(), "2026-06", self.scenarios())
        self.assertEqual(3, len(rows))
        self.assertTrue(all(row["deltaNet"] == "1" for row in rows))

    def test_rejects_incomplete_or_fabricated_replay(self):
        with self.assertRaisesRegex(ValueError, "exactly three"):
            parse_volume_replays(self.output().splitlines()[0], "2026-06", self.scenarios())
        with self.assertRaisesRegex(ValueError, "does not reconcile"):
            parse_volume_replays(self.output().replace("baselineNet=-2", "baselineNet=9"),
                                 "2026-06", self.scenarios())

    def test_rejects_nonfinite_and_empty_trade_inconsistencies(self):
        with self.assertRaisesRegex(ValueError, "Nonfinite"):
            parse_volume_replays(self.output().replace("gatedNet=-1", "gatedNet=NaN"),
                                 "2026-06", self.scenarios())
        with self.assertRaisesRegex(ValueError, "Empty gated replay"):
            parse_volume_replays(self.output().replace("gatedTrades=2", "gatedTrades=0"),
                                 "2026-06", self.scenarios())


if __name__ == "__main__":
    unittest.main()
