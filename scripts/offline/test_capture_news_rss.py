import csv
from datetime import datetime, timezone
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from capture_news_rss import safe_feed_url, parse_feed, append_first_seen

RSS = b"""<?xml version="1.0"?>
<rss version="2.0"><channel>
<item><link>https://www.federalreserve.gov/newsevents/pressreleases/a.htm</link>
<pubDate>Wed, 04 Nov 2026 05:00:00 GMT</pubDate></item>
<item><link>https://www.federalreserve.gov/newsevents/pressreleases/future.htm</link>
<pubDate>Thu, 05 Nov 2026 05:00:00 GMT</pubDate></item>
<item><link>https://www.federalreserve.gov/without-time.htm</link></item>
</channel></rss>"""
ATOM = b"""<feed xmlns="http://www.w3.org/2005/Atom">
<entry><link href="https://www.federalreserve.gov/atom.htm"/>
<published>2026-11-04T05:00:00Z</published></entry>
</feed>"""


class NewsCaptureTests(unittest.TestCase):
    def setUp(self):
        self.now = datetime(2026, 11, 4, 6, 0, tzinfo=timezone.utc)

    def test_rss_uses_actual_capture_time_and_skips_future_or_undated(self):
        records = parse_feed(RSS, "Federal Reserve", self.now)
        self.assertEqual(1, len(records))
        self.assertEqual("2026-11-04T06:00:00Z", records[0]["firstSeenUtc"])
        self.assertEqual("2026-11-04T05:00:00Z", records[0]["publishedUtc"])

    def test_atom_published_and_url(self):
        rows = parse_feed(ATOM, "Federal Reserve", self.now)
        self.assertEqual(1, len(rows))
        self.assertEqual("https://www.federalreserve.gov/atom.htm", rows[0]["url"])

    def test_repeated_capture_never_rewrites_first_seen(self):
        with TemporaryDirectory() as folder:
            dest = Path(folder) / "news.csv"
            first = parse_feed(RSS, "Federal Reserve", self.now)
            self.assertEqual((0, 1), append_first_seen(dest, first))
            tomorrow = datetime(2026, 11, 5, 6, 0, tzinfo=timezone.utc)
            second = parse_feed(RSS, "Federal Reserve", tomorrow)
            self.assertEqual((1, 1), append_first_seen(dest, second))
            with dest.open(newline="") as f:
                rows = list(csv.DictReader(f))
            self.assertEqual(2, len(rows))
            self.assertEqual("2026-11-04T06:00:00Z", rows[0]["firstSeenUtc"])
            self.assertEqual("2026-11-05T06:00:00Z", rows[1]["firstSeenUtc"])

    def test_rejects_unapproved_publisher_or_plain_http(self):
        for url in ("http://www.federalreserve.gov/rss",
                    "https://localhost/rss", "https://example.com/news",
                    "https://federalreserve.gov.evil.example/feed"):
            with self.assertRaises(ValueError):
                safe_feed_url(url)

    def test_malformed_archive_fails_without_overwriting(self):
        with TemporaryDirectory() as folder:
            dest = Path(folder) / "news.csv"
            dest.write_text("invalid,data\nnot,news\n")
            with self.assertRaises(ValueError):
                append_first_seen(dest, parse_feed(RSS, "Federal Reserve", self.now))
            self.assertEqual("invalid,data\nnot,news\n", dest.read_text())


if __name__ == "__main__":
    unittest.main()
