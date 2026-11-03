import unittest
from compare_historical_months import parse_report, parse_groups


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
        groups = parse_groups("\\n".join(lines), "2026-09", report)
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
            parse_groups("\\n".join(lines), "2026-09", report)

    def test_rejects_incomplete_group_output(self):
        with self.assertRaises(ValueError):
            parse_groups("COINMIND HISTORICAL GROUP side=LONG count=1", "2026-09",
                         {"executed": "1", "netPnl": "0"})


if __name__ == "__main__":
    unittest.main()
