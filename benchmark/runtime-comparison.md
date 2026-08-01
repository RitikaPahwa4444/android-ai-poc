# Runtime comparison: ONNX POC vs TFLite Tester

This is an initial comparison from Android Studio APK Analyzer screenshots. It is useful for
direction, but it is not yet a controlled benchmark: the TFLite app originally contained three face models,
while the ONNX POC contains one face model and one license-plate model. The applications also
have different UI and dependency code.

## Observed artifacts

| Component | ONNX POC | TFLite Tester | Difference |
|---|---:|---:|---:|
| APK size | 13.0 MB | 24.8 MB | TFLite +11.8 MB |
| Download size | 12.9 MB | 19.0 MB | TFLite +6.1 MB |
| Native runtime, installed | 6.8 MB | 5.1 MB | TFLite -1.7 MB |
| Native runtime, download | 6.3 MB | 2.1 MB | TFLite -4.2 MB |
| Model assets, installed | 3.9 MB | 9.6 MB | TFLite +5.7 MB |
| Model assets, download | 3.9 MB | 7.3 MB | TFLite +3.4 MB |

The TFLite artifact contains these three face models:

| Model | Installed size | Download size |
|---|---:|---:|
| `detect (1).tflite` | 5.3 MB | 3.4 MB |
| `Lightweight-Face-Detection.tflite` | 3.4 MB | 3.1 MB |
| `face_det_lite.tflite` | 966.6 KB | 802.3 KB |

The ONNX artifact contains a roughly 0.23 MB face model and a roughly 4.0 MB plate model.

## Size-first candidate set

Use only one model per task; do not bundle unused alternatives:

| Task | TFLite candidate | ONNX candidate | Status |
|---|---|---|---|
| Face | `face_det_lite.tflite`, 966.6 KB installed / 802.3 KB download | YuNet, 232.6 KB | Available |
| License plate | No verified model in `TfliteTester` | LPD-YuNet, 4.0 MB | TFLite candidate needed |

The reported ~1.5 MB TFLite plate model is not present in the current TFLite repository. It
needs a source URL, checksum, license, input/output contract, and device test before selection.

## Interpretation

- TFLite's native runtime is smaller in this comparison.
- The ONNX POC is smaller overall because it contains fewer/smaller model assets and less
  surrounding application code.
- The current ONNX face model produced materially better detections in the device tests, but
  size is now the primary selection criterion and that tradeoff must be measured on the pinned
  Supporters corpus.
- TFLite has not yet been compared with an equivalent plate detector.
- No final runtime decision should be made from complete APK size alone.

## Decision gate

Run the minimal face candidates and minimal plate candidates on the same 27-image corpus and the
same device. Record recall, precision, false positives, p50/p95 latency, peak memory, and
decomposed runtime/model sizes. Choose the smallest candidate that passes the minimum recall
safety gate. Reject a candidate only when it misses too many sensitive objects, even if it is
smaller.
