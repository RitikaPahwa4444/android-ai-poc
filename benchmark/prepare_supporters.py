#!/usr/bin/env python3
"""Download the pinned Supporters fixtures and record their SHA-256 hashes.

Images are deliberately stored below benchmark/images/, which is ignored by Git. The
manifest remains the reviewable source of URLs and checksums.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import shutil
import sys
import time
import urllib.error
import urllib.request
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit
from pathlib import Path


ROOT = Path(__file__).resolve().parent
MANIFEST = ROOT / "supporters" / "manifest.csv"
IMAGE_DIR = ROOT / "images"
DEFAULT_WIDTH = 1600


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def thumbnail_url(url: str, width: int) -> str:
    parts = urlsplit(url)
    query = dict(parse_qsl(parts.query))
    query["width"] = str(width)
    return urlunsplit((parts.scheme, parts.netloc, parts.path, urlencode(query), parts.fragment))


def download(url: str, target: Path, width: int, attempts: int = 5) -> None:
    request = urllib.request.Request(
        thumbnail_url(url, width),
        headers={"User-Agent": "commons-ai-poc-benchmark/1.0"},
    )
    temporary = target.with_suffix(target.suffix + ".download")
    for attempt in range(attempts):
        try:
            with urllib.request.urlopen(request, timeout=120) as response, temporary.open("wb") as output:
                shutil.copyfileobj(response, output)
            temporary.replace(target)
            return
        except urllib.error.HTTPError as error:
            if error.code != 429 or attempt == attempts - 1:
                raise
            retry_after = error.headers.get("Retry-After")
            delay = int(retry_after) if retry_after and retry_after.isdigit() else 10 * (attempt + 1)
            if delay > 120:
                raise RuntimeError(
                    f"Wikimedia requested a {delay}s retry delay; rerun later instead of waiting."
                ) from error
            print(f"Rate limited; retrying in {delay}s", file=sys.stderr)
            time.sleep(delay)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true", help="redownload existing files")
    parser.add_argument(
        "--width",
        type=int,
        default=DEFAULT_WIDTH,
        help=f"thumbnail width in pixels (default: {DEFAULT_WIDTH})",
    )
    args = parser.parse_args()

    IMAGE_DIR.mkdir(parents=True, exist_ok=True)
    with MANIFEST.open(newline="") as source:
        rows = list(csv.DictReader(source))

    for index, row in enumerate(rows):
        target = IMAGE_DIR / row["file_name"]
        target.parent.mkdir(parents=True, exist_ok=True)
        if args.force or not target.exists():
            print(f"Downloading {row['file_name']}")
            download(row["source_url"], target, args.width)
        row["sha256"] = sha256(target)
        print(f"{row['sha256']}  {row['file_name']}")
        if index + 1 < len(rows):
            time.sleep(2)

        # Persist after every file so an interrupted or rate-limited run is resumable.
        with MANIFEST.open("w", newline="") as destination:
            writer = csv.DictWriter(destination, fieldnames=rows[0].keys())
            writer.writeheader()
            writer.writerows(rows)

    print(f"Prepared {len(rows)} fixtures in {IMAGE_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
