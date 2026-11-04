import unittest
from audit_news_at_decision import validate_news, audit


class HistoricalNewsAvailabilityTests(unittest.TestCase):
    def article(self, **overrides):
        row = {"newsId": "a1", "source": "Sample publisher",
               "url": "https://example.com/article/1",
               "publishedUtc": "2026-06-05T11:00:00Z",
               "firstSeenUtc": "2026-06-05T11:15:00Z"}
        row.update(overrides)
        return row

    def entry(self, at):
        return {"month": "2026-06", "direction": "BOTH", "bars": "12",
                "decisionUtc": at, "side": "LONG", "net": "0",
                "volatility": "MEDIUM", "rangePercent": "0.5",
                "momentum5m": "50", "momentum15m": "50",
                "momentum1h": "50", "momentum4h": "50",
                "strength": "50", "aligned": "4", "decelerating": "false",
                "gross": "0.5", "fees": "0.5"}

    def test_late_capture_must_not_leak_into_earlier_trade(self):
        news = validate_news([self.article()])
        records = audit([
            self.entry("2026-06-05T11:10:00Z"),
            self.entry("2026-06-05T11:20:00Z")], news)
        self.assertEqual("0", records[0]["availableNewsCount"])
        self.assertEqual("1", records[1]["availableNewsCount"])
        self.assertEqual("2026-06-05T11:15:00Z", records[1]["latestFirstSeenUtc"])

    def test_rejects_backdated_and_duplicate_capture(self):
        with self.assertRaisesRegex(ValueError, "cannot precede"):
            validate_news([self.article(firstSeenUtc="2026-06-05T10:59:00Z")])
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            validate_news([self.article(), self.article()])

    def test_rejects_non_https_and_unbounded_lookback(self):
        with self.assertRaisesRegex(ValueError, "HTTPS"):
            validate_news([self.article(url="http://example.com/post")])
        with self.assertRaisesRegex(ValueError, "lookback"):
            audit([self.entry("2026-06-05T11:20:00Z")], [], 1000)

    def test_stale_captures_outside_lookback_not_counted(self):
        news = validate_news([self.article()])
        row = audit([self.entry("2026-06-07T11:20:00Z")], news, 24)[0]
        self.assertEqual("0", row["availableNewsCount"])


if __name__ == "__main__":
    unittest.main()
