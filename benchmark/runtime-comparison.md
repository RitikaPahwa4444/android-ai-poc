# Size-first comparison: one face model plus one plate model

This is the comparison relevant to the current POC decision. It includes exactly one face model
and one license-plate model per runtime. The TFLite plate value is a reported measurement and
still needs provenance before it can be reproduced.

## Current model-only comparison

| Task | TFLite model size | ONNX model size | Difference |
|---|---:|---:|---:|
| Face detection | 966 KB | 199 KB packaged | ONNX −767 KB |
| License-plate detection | 1.5 MB | about 4.0 MB raw | TFLite −about 2.5 MB |
| Both models | about 2.47 MB | about 4.2 MB raw | TFLite −about 1.7 MB |

The ONNX values now use the full-precision LPD-YuNet model because it gave materially better
plate results. Packaged APK size must be re-measured from the next release build.

## Runtime measurements

These native-runtime values come from the earlier APK Analyzer comparison. They exclude model
assets and are retained only as runtime-overhead reference points.

| Runtime component | ONNX Runtime | TFLite runtime | Difference |
|---|---:|---:|---:|
| Native runtime, installed | 6.8 MB | 5.1 MB | TFLite -1.7 MB |
| Native runtime, download | 6.3 MB | 2.1 MB | TFLite -4.2 MB |

## Candidate set

Use only one model per task; do not bundle unused alternatives:

| Task | TFLite candidate | ONNX candidate | Status |
|---|---|---|---|
| Face | `face_det_lite.tflite`, 966 KB reported | YuNet, 199 KB packaged | Available |
| License plate | Candidate, 1.5 MB reported | LPD-YuNet, about 4.0 MB raw | TFLite provenance needed |

The reported 1.5 MB TFLite plate model still needs a source URL, checksum, license, input/output
contract, and device test before selection.

## Interpretation

- The full-precision ONNX plate model is larger than the reported TFLite candidate, but currently
  gives materially better plate results than the quantized model.
- These runtime values are not a controlled single-model APK comparison; the applications used
  different dependency and packaging configurations.
- The current ONNX face model produced materially better detections in the device tests, but
  size is now the primary selection criterion and that tradeoff must be measured on the pinned
  Supporters corpus.
- Runtime size, APK/AAB size, latency, and model size still need a controlled single-model
  comparison on the same device.

## Decision gate

Run the minimal face candidates and minimal plate candidates on the same 27-image corpus and the
same device. Record recall, precision, false positives, p50/p95 latency, peak memory, and
decomposed runtime/model sizes. Choose the smallest candidate that passes the minimum recall
safety gate. Reject a candidate only when it misses too many sensitive objects, even if it is
smaller.
