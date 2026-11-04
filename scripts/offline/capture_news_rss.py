#!/usr/bin/env python3
"""Opt-in point-in-time RSS/Atom news capture for FUTURE offline research.

Records firstSeenUtc at *actual fetch time*, never backdates it to the article's
published time. Existing article first-seen timestamps are immutable.
No AI classification, live trade signals, orders, databases, or production API.
This cannot reconstruct what was known in June-September 2026.
"""
import argparse
import csv
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
import fcntl
import hashlib
import io
import os
from pathlib import Path
import stat
from urllib.parse import urlsplit
from urllib.request import Request, build_opener, HTTPHandler, HTTPSHandler, HTTPRedirectHandler
import xml.etree.ElementTree as ET

from audit_news_at_decision import NEWS_FIELDS, utc, validate_news

MAX_BYTES = 2_000_000
MAX_ROWS = 100_000
ALLOWED_DOMAINS = ("federalreserve.gov", "coindesk.com", "cointelegraph.com")


def safe_feed_url(url):
    parts = urlsplit(url)
    host = (parts.hostname or "").lower().rstrip(".")
    if (parts.scheme != "https" or parts.username or parts.password
            or parts.port not in (None, 443)
            or not any(host == domain or host.endswith("." + domain)
                       for domain in ALLOWED_DOMAINS)):
        raise ValueError("News feed must be HTTPS on an explicitly approved publisher domain")
    return url


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("Publisher feed redirected; set the final approved HTTPS URL explicitly")


def fetch_feed(url):
    safe_feed_url(url)
    opener = build_opener(HTTPSHandler(), HTTPHandler(), NoRedirect())
    request = Request(url, headers={"User-Agent": "CoinMind-Research-RSS-Capture/1.0"})
    with opener.open(request, timeout=20) as response:
        if response.geturl() != url:
            raise ValueError("Redirected publisher feed rejected")
        content = response.read(MAX_BYTES + 1)
        if len(content) > MAX_BYTES:
            raise ValueError("RSS response exceeds capture bound")
        return content


def parse_time(value):
    try:
        result = parsedate_to_datetime(value)
    except (TypeError, ValueError, IndexError):
        try:
            result = datetime.fromisoformat(value.replace("Z", "+00:00"))
        except (AttributeError, ValueError) as exc:
            raise ValueError("Unparseable publisher publication time") from exc
    if result.tzinfo is None:
        raise ValueError("Publisher time lacks timezone; refusing to infer")
    return result.astimezone(timezone.utc)


def iso_utc(value):
    return value.astimezone(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def parse_feed(payload, source, first_seen):
    """Supports standard RSS 2.0 and Atom; rejects future, missing-time entries."""
    if not source or not source.strip() or len(source) > 200:
        raise ValueError("Publisher name is required")
    try:
        root = ET.fromstring(payload)
    except ET.ParseError as exc:
        raise ValueError("Publisher did not return valid RSS/Atom XML") from exc
    if root.tag == "rss":
        items = root.findall("./channel/item")
        def get(row):
            return row.findtext("link"), row.findtext("pubDate")
    elif root.tag == "{http://www.w3.org/2005/Atom}feed":
        ns = "{http://www.w3.org/2005/Atom}"
        items = root.findall(ns + "entry")
        def get(row):
            links = [link.get("href") for link in row.findall(ns + "link")
                     if link.get("rel", "alternate") == "alternate" and link.get("href")]
            return (links[0] if links else None), (
                row.findtext(ns + "published") or row.findtext(ns + "updated"))
    else:
        raise ValueError("Expected RSS 2.0 or Atom feed")
    if len(items) > 500:
        raise ValueError("Too many feed items")
    output = []
    for item in items:
        url, published_raw = get(item)
        if not url or not published_raw:
            continue  # Unverifiable publication time: never assume fetch time.
        parts = urlsplit(url)
        if parts.scheme != "https" or not parts.netloc or parts.username or parts.password:
            continue
        try:
            published = parse_time(published_raw)
        except ValueError:
            continue
        if published > first_seen:
            continue  # Reject future publication; clock skew requires investigation.
        news_id = hashlib.sha256((source + "\n" + url).encode("utf-8")).hexdigest()
        output.append({
            "newsId": news_id, "source": source, "url": url,
            "publishedUtc": iso_utc(published),
            "firstSeenUtc": iso_utc(first_seen),
        })
    validate_news(output)
    return output


def append_first_seen(archive, observations):
    """Append under POSIX exclusive lock; never rewrite existing first-seen records."""
    path = Path(archive)
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.is_symlink() or (path.exists() and not path.is_file()):
        raise ValueError("Unsafe news archive path")
    flags = os.O_RDWR | os.O_CREAT | getattr(os, "O_NOFOLLOW", 0)
    fd = os.open(path, flags, 0o600)
    try:
        if not stat.S_ISREG(os.fstat(fd).st_mode):
            raise ValueError("News archive must be a regular file")
        fcntl.flock(fd, fcntl.LOCK_EX)
        with os.fdopen(fd, "r+", encoding="utf-8", newline="") as handle:
            fd = -1
            existing = list(csv.DictReader(handle)) if os.fstat(handle.fileno()).st_size else []
            if existing:
                validate_news(existing)
            elif os.fstat(handle.fileno()).st_size:
                raise ValueError("Nonempty archive has no valid news records")
            known = {r["newsId"] for r in existing}
            fresh = [r for r in observations if r["newsId"] not in known]
            if len(existing) + len(fresh) > MAX_ROWS:
                raise ValueError("Bounded archive limit reached")
            validate_news(existing + fresh)
            handle.seek(0, os.SEEK_END)
            writer = csv.DictWriter(handle, fieldnames=NEWS_FIELDS, lineterminator="\n")
            if not existing:
                writer.writeheader()
            writer.writerows(fresh)
            handle.flush()
            os.fsync(handle.fileno())
            return len(existing), len(fresh)
    finally:
        if fd >= 0:
            os.close(fd)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--feed-url", required=True, help="Approved publisher's actual HTTPS RSS/Atom feed")
    parser.add_argument("--source", required=True, help="Named real publisher")
    parser.add_argument("--archive", required=True, help="Append-only real-time first-seen CSV")
    args = parser.parse_args()
    data = fetch_feed(args.feed_url)
    captured_at = datetime.now(timezone.utc)
    observations = parse_feed(data, args.source, captured_at)
    old, added = append_first_seen(args.archive, observations)
    print(f"NEWS CAPTURE existing={old} added={added} observed={len(observations)}")
    print("Captures begin NOW; cannot backfill honest firstSeenUtc for prior dates.")
    print("No sentiment/trading filter; no data are sent to production.")


if __name__ == "__main__":
    main()
