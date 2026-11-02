import io
import unittest
import zipfile
from binance_um_monthly import normalize


def sample_archive(rows, name="BTCUSDT-5m-2026-10.csv"):
    data = io.BytesIO()
    with zipfile.ZipFile(data, "w") as z:
        z.writestr(name, rows)
    return data.getvalue()


class NormalizeTests(unittest.TestCase):
    def test_normalizes_regular_candles(self):
        rows = "".join(
            f"{1790812800000 + 300000*i},100,110,90,105,1,{1790813099999 + 300000*i},1,1,1,1,0\n"
            for i in range(3))
        result, count = normalize(sample_archive(rows), "BTCUSDT-5m-2026-10.csv", "5m")
        self.assertEqual(count, 3)
        self.assertEqual(result.decode().splitlines()[0], "open_time_utc,open,high,low,close")
        self.assertEqual(len(result.decode().splitlines()), 4)

    def test_rejects_missing_candle(self):
        rows = "".join(
            f"{1790812800000 + 300000*i},100,110,90,105,1,{1790813099999 + 300000*i},1,1,1,1,0\n"
            for i in (0, 1, 3))
        with self.assertRaises(ValueError):
            normalize(sample_archive(rows), "BTCUSDT-5m-2026-10.csv", "5m")

    def test_accepts_explicit_microsecond_timestamps(self):
        rows = "".join(
            f"{1790812800000000 + 300000000*i},100,110,90,105,1,{1790813099999999 + 300000000*i},1,1,1,1,0\n"
            for i in range(3))
        result, count = normalize(sample_archive(rows), "BTCUSDT-5m-2026-10.csv", "5m")
        self.assertEqual(count, 3)
        self.assertIn("2026-", result.decode())

    def test_gap_error_identifies_expected_and_actual_open(self):
        rows = "".join(
            f"{1790812800000 + 300000*i},100,110,90,105,1,{1790813099999 + 300000*i},1,1,1,1,0\n"
            for i in (0, 1, 3))
        with self.assertRaisesRegex(ValueError, "expected .* got .* delta_seconds=600"):
            normalize(sample_archive(rows), "BTCUSDT-5m-2026-10.csv", "5m")


if __name__ == "__main__":
    unittest.main()
