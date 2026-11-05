import csv
from datetime import datetime, timezone
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch
import unittest

from gemini_news_research import (
    headlines_from_feed, append_headlines, validate_scores,
    evaluate_batch, score_archive, MODEL, HEADLINE_FIELDS
)

RSS = b"""<rss version="2.0"><channel><item>
<title>Bitcoin ETF inflows rise</title>
<link>https://www.coindesk.com/story/btc</link>
<pubDate>Thu, 05 Nov 2026 03:00:00 GMT</pubDate>
</item></channel></rss>"""


class GeminiNewsResearchTests(unittest.TestCase):
    def headline(self, news_id="a" * 64):
        return {
            "newsId": news_id, "source": "CoinDesk",
            "url": "https://www.coindesk.com/story/btc",
            "publishedUtc": "2026-11-05T03:00:00Z",
            "firstSeenUtc": "2026-11-05T04:00:00Z",
            "title": "Bitcoin ETF inflows rise",
        }

    def test_capture_real_title_and_first_seen_not_publication(self):
        rows = headlines_from_feed(
            RSS, "CoinDesk", datetime(2026, 11, 5, 4, tzinfo=timezone.utc))
        self.assertEqual(1, len(rows))
        self.assertEqual("Bitcoin ETF inflows rise", rows[0]["title"])
        self.assertEqual("2026-11-05T04:00:00Z", rows[0]["firstSeenUtc"])

    def test_archive_is_immutable_and_deduplicated(self):
        with TemporaryDirectory() as folder:
            path = Path(folder) / "headlines.csv"
            first = self.headline()
            self.assertEqual((0, 1), append_headlines(path, [first]))
            later = {**first, "firstSeenUtc": "2026-11-05T05:00:00Z",
                     "title": "Updated title"}
            self.assertEqual((1, 0), append_headlines(path, [later]))
            with path.open(newline="") as f:
                result = list(csv.DictReader(f))
            self.assertEqual(HEADLINE_FIELDS, tuple(result[0]))
            self.assertEqual("Bitcoin ETF inflows rise", result[0]["title"])
            self.assertEqual("2026-11-05T04:00:00Z", result[0]["firstSeenUtc"])

    def test_gemini_response_requires_full_identity_and_valid_labels(self):
        candidate = {"items": [{
            "newsId": "a" * 64, "btcRelevance": 0.8,
            "sentiment": "BULLISH", "impact": "MEDIUM",
            "rationale": "Headline directly concerns Bitcoin flows."
        }]}
        with patch("gemini_news_research._request_gemini", return_value=candidate):
            result = evaluate_batch(
                [self.headline()], "fake-test-only",
                datetime(2026, 11, 5, 5, tzinfo=timezone.utc))
        self.assertEqual("BULLISH", result[0]["sentiment"])
        self.assertEqual("2026-11-05T05:00:00Z", result[0]["scoredUtc"])
        validate_scores(result)
        bad = {"items": [{**candidate["items"][0], "newsId": "b" * 64}]}
        with patch("gemini_news_research._request_gemini", return_value=bad):
            with self.assertRaisesRegex(ValueError, "identity mismatch"):
                evaluate_batch([self.headline()], "fake-test-only")
        with self.assertRaisesRegex(ValueError, "Score cannot predate"):
            validate_scores([{**result[0], "scoredUtc": "2026-11-05T02:00:00Z"}])
        with self.assertRaisesRegex(ValueError, "BTC relevance"):
            validate_scores([{**result[0], "btcRelevance": "nan"}])

    def test_dry_run_no_key_and_no_external_calls(self):
        with TemporaryDirectory() as folder:
            source = Path(folder) / "headlines.csv"
            dest = Path(folder) / "scores.csv"
            append_headlines(source, [self.headline()])
            with patch("gemini_news_research._request_gemini") as remote:
                score_archive(source, dest, None, 1, False)
                remote.assert_not_called()
            self.assertFalse(dest.exists())
            with self.assertRaisesRegex(ValueError, "GEMINI_API_KEY"):
                score_archive(source, dest, None, 1, True)

    def test_cache_skips_completed_news(self):
        with TemporaryDirectory() as folder:
            source = Path(folder) / "headlines.csv"
            dest = Path(folder) / "scores.csv"
            append_headlines(source, [self.headline()])
            model_return = {"items": [{
                "newsId": "a" * 64, "btcRelevance": 1,
                "sentiment": "NEUTRAL", "impact": "LOW",
                "rationale": "No clear directional prediction."
            }]}
            with patch("gemini_news_research._request_gemini", return_value=model_return) as remote:
                score_archive(source, dest, "test-only", 1, True)
                score_archive(source, dest, "test-only", 1, True)
                self.assertEqual(1, remote.call_count)
            with dest.open(newline="") as handle:
                results = list(csv.DictReader(handle))
            self.assertEqual(1, len(results))
            self.assertEqual(MODEL, results[0]["model"])


if __name__ == "__main__":
    unittest.main()
