# Size-first comparison: one face model plus one plate model

This is the comparison relevant to the current POC decision. It includes exactly one face model
and one license-plate model per runtime. The TFLite plate value is a reported measurement and
still needs provenance before it can be reproduced.

## Current model-only comparison

| Task | TFLite model size | ONNX model size | Difference |
|---|---:|---:|---:|
| Face detection | 966 KB | 199 KB packaged | ONNX −767 KB |
| License-plate detection | 1.5 MB | 769.4 KB packaged | ONNX −about 731 KB |
| Both models | about 2.47 MB | about 968.4 KB packaged | ONNX −about 1.50 MB |

The ONNX APK Analyzer showed approximately 969.1 KB under `assets/models` when model metadata
is included. The model files themselves account for approximately 968.4 KB.

## Historical multi-model APK comparison

The following table must not be used as the current runtime/model decision. The TFLite APK
contained three face models simultaneously, while the ONNX APK contained one face model and one
plate model. It also had different application code and dependencies.

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

## Candidate set

Use only one model per task; do not bundle unused alternatives:

| Task | TFLite candidate | ONNX candidate | Status |
|---|---|---|---|
| Face | `face_det_lite.tflite`, 966 KB reported | YuNet, 199 KB packaged | Available |
| License plate | Candidate, 1.5 MB reported | LPD-YuNet, 769.4 KB packaged | TFLite provenance needed |

The reported 1.5 MB TFLite plate model still needs a source URL, checksum, license, input/output
contract, and device test before selection.

## Interpretation

- The reported ONNX model pair is approximately 1.50 MB smaller than the reported TFLite pair.
- The historical APK table cannot establish which runtime is smaller when both contain only one
  face model and one plate model.
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
