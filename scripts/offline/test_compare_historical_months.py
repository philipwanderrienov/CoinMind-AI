import unittest
from compare_historical_months import parse_report


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


if __name__ == "__main__":
    unittest.main()
