# Runtime comparison: ONNX POC vs TFLite Tester

This document combines the original whole-APK comparison with the latest model-only measurements.
The whole-APK values are historical and not a controlled apples-to-apples benchmark because the
applications have different UI and dependency code. The model-only values below are the current
size-first candidates.

## Observed artifacts

| Component | ONNX POC | TFLite Tester | Difference |
|---|---:|---:|---:|
| APK size | 13.0 MB | 24.8 MB | TFLite +11.8 MB |
| Download size | 12.9 MB | 19.0 MB | TFLite +6.1 MB |
| Native runtime, installed | 6.8 MB | 5.1 MB | TFLite -1.7 MB |
| Native runtime, download | 6.3 MB | 2.1 MB | TFLite -4.2 MB |
| Model assets, installed | 3.9 MB | 9.6 MB | TFLite +5.7 MB |
| Model assets, download | 3.9 MB | 7.3 MB | TFLite +3.4 MB |

The earlier TFLite artifact contained these three face models:

| Model | Installed size | Download size |
|---|---:|---:|
| `detect (1).tflite` | 5.3 MB | 3.4 MB |
| `Lightweight-Face-Detection.tflite` | 3.4 MB | 3.1 MB |
| `face_det_lite.tflite` | 966.6 KB | 802.3 KB |

The latest ONNX APK Analyzer measurement contains approximately 199 KB of face-model assets and
769.4 KB of plate-model assets, or about 969.1 KB under `assets/models` including metadata.

## Latest model-only comparison

| Task | TFLite model size | ONNX model size | Difference |
|---|---:|---:|---:|
| Face detection | 966 KB | 199 KB packaged | ONNX −767 KB |
| License-plate detection | 1.5 MB | 769.4 KB packaged | ONNX −about 731 KB |
| Both models | about 2.47 MB | about 969.1 KB packaged | ONNX −about 1.50 MB |

The TFLite plate value is the latest reported measurement and still needs its exact model
filename, source URL, checksum, license, and input/output contract recorded in the POC before
it can be reproduced.

## Size-first candidate set

Use only one model per task; do not bundle unused alternatives:

| Task | TFLite candidate | ONNX candidate | Status |
|---|---|---|---|
| Face | `face_det_lite.tflite`, 966 KB reported | YuNet, 199 KB packaged | Available |
| License plate | Candidate, 1.5 MB reported | LPD-YuNet, 769.4 KB packaged | TFLite provenance needed |

The reported 1.5 MB TFLite plate model still needs a source URL, checksum, license, input/output
contract, and device test before selection.

## Interpretation

- TFLite's native runtime is smaller in this comparison.
- The ONNX POC is smaller overall because it contains fewer/smaller model assets and less
  surrounding application code.
- The current ONNX face model produced materially better detections in the device tests, but
  size is now the primary selection criterion and that tradeoff must be measured on the pinned
  Supporters corpus.
- The ONNX model assets are currently smaller than the reported equivalent TFLite candidates.
- No final runtime decision should be made from complete APK size alone.

## Decision gate

Run the minimal face candidates and minimal plate candidates on the same 27-image corpus and the
same device. Record recall, precision, false positives, p50/p95 latency, peak memory, and
decomposed runtime/model sizes. Choose the smallest candidate that passes the minimum recall
safety gate. Reject a candidate only when it misses too many sensitive objects, even if it is
smaller.
