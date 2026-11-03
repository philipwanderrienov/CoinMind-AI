import unittest
from compare_historical_months import parse_report, parse_groups, parse_scenarios, parse_entries


class ComparisonParserTest(unittest.TestCase):
    def test_validated_month_report(self):
        line = ("COINMIND HISTORICAL OFFLINE REPORT month=2026-08 "
                "market=USD_M_FUTURES symbol=BTCUSDT executed=15 waits=10 "
                "riskBlocked=0 initialEquity=1000 finalEquity=990 netPnl=-10 "
                "maxDrawdownQuote=15 wins=5 losses=10 winRatePercent=33.3 profitFactor=0.5 "
                "DISCLAIMER=offline")
        result = parse_report("[INFO] tests\n" + line + "\n", "2026-08")
        self.assertEqual("-10", result["netPnl"])

    def test_rejects_wrong_month(self):
        with self.assertRaises(ValueError):
            parse_report("COINMIND HISTORICAL OFFLINE REPORT month=2026-09 "
                         "market=USD_M_FUTURES symbol=BTCUSDT", "2026-08")

    def test_rejects_missing_report(self):
        with self.assertRaises(ValueError):
            parse_report("BUILD SUCCESS", "2026-08")

    def test_groups_reconcile_to_monthly_pnl(self):
        report = {"executed": "3", "netPnl": "-2.5"}
        lines = [
            "COINMIND HISTORICAL GROUP side=LONG count=2 wins=1 losses=1 gross=0 fees=1 net=-1 averageNet=-0.5",
            "COINMIND HISTORICAL GROUP side=SHORT count=1 wins=0 losses=1 gross=-1 fees=0.5 net=-1.5 averageNet=-1.5",
            "COINMIND HISTORICAL GROUP exit=STOP count=1 wins=0 losses=1 gross=-1 fees=0.5 net=-1.5 averageNet=-1.5",
            "COINMIND HISTORICAL GROUP exit=TARGET count=0 wins=0 losses=0 gross=0 fees=0 net=0 averageNet=null",
            "COINMIND HISTORICAL GROUP exit=TIME_EXIT count=2 wins=1 losses=1 gross=0 fees=1 net=-1 averageNet=-0.5",
        ]
        groups = parse_groups("\n".join(lines), "2026-09", report)
        self.assertEqual(5, len(groups))
        self.assertEqual("LONG", groups[0]["category"])

    def test_rejects_group_pnl_mismatch(self):
        report = {"executed": "1", "netPnl": "-1"}
        lines = [
            "COINMIND HISTORICAL GROUP side=LONG count=1 wins=0 losses=1 gross=0 fees=1 net=-1 averageNet=-1",
            "COINMIND HISTORICAL GROUP side=SHORT count=0 wins=0 losses=0 gross=0 fees=0 net=0 averageNet=null",
            "COINMIND HISTORICAL GROUP exit=STOP count=1 wins=0 losses=1 gross=0 fees=1 net=-2 averageNet=-2",
            "COINMIND HISTORICAL GROUP exit=TARGET count=0 wins=0 losses=0 gross=0 fees=0 net=0 averageNet=null",
            "COINMIND HISTORICAL GROUP exit=TIME_EXIT count=0 wins=0 losses=0 gross=0 fees=0 net=0 averageNet=null",
        ]
        with self.assertRaises(ValueError):
            parse_groups("\n".join(lines), "2026-09", report)

    def test_rejects_incomplete_group_output(self):
        with self.assertRaises(ValueError):
            parse_groups("COINMIND HISTORICAL GROUP side=LONG count=1", "2026-09",
                         {"executed": "1", "netPnl": "0"})

    def test_fixed_experimental_grid_and_baseline_match(self):
        rows = []
        for bars in (2, 3, 6, 12):
            for direction in ("BOTH", "LONG_ONLY"):
                rows.append(
                    f"COINMIND EXPERIMENTAL SCENARIO month=2026-09 "
                    f"direction={direction} bars={bars} executed=3 wins=1 losses=2 "
                    "winRate=33.333 profitFactor=0.25 netPnl=-2 "
                    "maxDrawdownQuote=4 DISCLAIMER=in_sample_exploratory_only")
        baseline = {"executed":"3", "wins":"1", "losses":"2",
                    "netPnl":"-2", "maxDrawdownQuote":"4"}
        parsed = parse_scenarios("\n".join(rows), "2026-09", baseline)
        self.assertEqual(8, len(parsed))
        self.assertEqual(4, sum(row["direction"] == "LONG_ONLY" for row in parsed))

    def test_rejects_incomplete_experimental_grid(self):
        with self.assertRaises(ValueError):
            parse_scenarios(
                "COINMIND EXPERIMENTAL SCENARIO month=2026-09 "
                "direction=BOTH bars=2 executed=3",
                "2026-09", {"executed":"3", "wins":"1", "losses":"2",
                             "netPnl":"-2", "maxDrawdownQuote":"4"})

    def test_entry_quality_rows_reconcile_without_future_data(self):
        scenarios = [
            {"direction": direction, "bars": bars, "executed": "1", "wins": "1",
             "losses": "0", "netPnl": "0.5"}
            for bars in ("2", "3", "6", "12")
            for direction in ("BOTH", "LONG_ONLY")
        ]
        rows = []
        for direction, bars in (("BOTH", "2"), ("BOTH", "12"), ("LONG_ONLY", "12")):
            rows.append(
                "COINMIND ENTRY DIAGNOSTIC month=2026-06 "
                f"direction={direction} bars={bars} decisionUtc=2026-06-12T12:00:00Z "
                "side=LONG volatility=MEDIUM rangePercent=0.6 "
                "momentum5m=60 momentum15m=72 momentum1h=30 momentum4h=35 "
                "strength=64 aligned=4 decelerating=false gross=1 fees=0.5 net=0.5")
        parsed = parse_entries("\n".join(rows), "2026-06", scenarios)
        self.assertEqual(3, len(parsed))
        self.assertEqual("60", parsed[0]["momentum5m"])

    def test_entry_quality_rejects_missing_trade_rows(self):
        scenarios = [
            {"direction": direction, "bars": bars, "executed": "1", "wins": "1",
             "losses": "0", "netPnl": "0.5"}
            for bars in ("2", "3", "6", "12")
            for direction in ("BOTH", "LONG_ONLY")
        ]
        with self.assertRaisesRegex(ValueError, "count disagrees"):
            parse_entries("", "2026-06", scenarios)


if __name__ == "__main__":
    unittest.main()
