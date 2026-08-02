#!/usr/bin/env bash
set -euo pipefail

INPUT="${1:?Usage: $0 <apk-or-aar>}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
unzip -q "$INPUT" -d "$WORK_DIR"
find "$WORK_DIR" -type f -name '*.so' -print0 | while IFS= read -r -d '' library; do
  llvm-readelf -l "$library" | awk -v file="$library" '
    /LOAD/ { print file ": " $0; if ($NF != "0x4000") bad=1 }
    END { if (bad) exit 1 }'
done
