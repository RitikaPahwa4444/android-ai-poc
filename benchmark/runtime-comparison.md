# Runtime comparison: ONNX POC vs TFLite Tester

This is an initial comparison from Android Studio APK Analyzer screenshots. It is useful for
direction, but it is not yet a controlled benchmark: the TFLite app contains three face models,
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

## Interpretation

- TFLite's native runtime is smaller in this comparison.
- The ONNX POC is smaller overall because it contains fewer/smaller model assets and less
  surrounding application code.
- The current ONNX face model produced materially better detections in the device tests, but
  that observation must be measured on the pinned Supporters corpus.
- TFLite has not yet been compared with an equivalent plate detector.
- No final runtime decision should be made from complete APK size alone.

## Decision gate

Run both runtimes on the same 27-image corpus and the same device. Record recall, precision,
false positives, p50/p95 latency, peak memory, and decomposed runtime/model sizes. Prefer TFLite
only if its accuracy remains acceptable and its runtime-size advantage is meaningful. Prefer ONNX
if its recall advantage remains substantial for faces or plates; missing a sensitive object is
more serious than a moderate runtime-size increase.
