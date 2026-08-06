#!/usr/bin/env bash
set -euo pipefail

INPUT="${1:?Usage: $0 <apk-or-aar>}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
unzip -q "$INPUT" -d "$WORK_DIR"
mapfile -d '' libraries < <(find "$WORK_DIR" -type f -name '*.so' -print0)
if [[ "${#libraries[@]}" -eq 0 ]]; then
  echo "No native libraries found" >&2
  exit 1
fi
for library in "${libraries[@]}"; do
  llvm-readelf -l "$library" | awk -v file="$library" '
    /LOAD/ { print file ": " $0; if ($NF != "0x4000") bad=1 }
    END { if (bad) exit 1 }'
done
if command -v zipalign >/dev/null 2>&1; then
  zipalign -c -P 16 4 "$INPUT"
else
  echo "zipalign not found; run zipalign -c -P 16 4 $INPUT separately" >&2
fi
