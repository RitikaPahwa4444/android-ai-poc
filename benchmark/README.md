# Commons AI benchmark corpus

The initial evaluation corpus is the 27 media files currently listed in
[Wikimedia Commons Category:Supporters](https://commons.wikimedia.org/wiki/Category:Supporters).
The source listing is intentionally recorded in `supporters/manifest.csv`; do not rely on
the live category remaining unchanged.

## Reproducing the corpus

1. Download each file from its `source_url`.
2. Store local copies outside Git, under `benchmark/images/`.
3. Record the downloaded file's SHA-256 in the manifest.
4. Record the Commons file-page revision used for the download.
5. Obtain ground-truth boxes in COCO-style JSON with these classes only:
   `face` and `license_plate`.

The images are test fixtures, not application assets. They must never be bundled into the
Android APK. Each file's own description page remains the source of truth for attribution,
license, and other reuse conditions.

## Required benchmark output

For every runtime/model pair, record:

- model and runtime version/checksum;
- APK/AAB and model-pack sizes;
- device, Android version, ABI, and CPU/GPU delegate;
- cold-start, p50, and p95 latency;
- peak memory;
- face and plate recall/precision;
- false positives per image;
- coordinate mapping failures and inference errors.

Sensitive-object redaction prioritizes recall. A missed face or plate is more serious than a
false-positive suggestion, but false positives must remain manually removable in the UI.
