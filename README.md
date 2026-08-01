# Commons AI POC

Standalone Android proof of concept for local face and license-plate suggestions. It is
isolated from `apps-android-commons` so model, runtime, and size experiments cannot affect
the production app.

## Current implementation

- ONNX Runtime Android 1.20.0; minSdk 21.
- OpenCV Zoo YuNet face detector.
- OpenCV Zoo LPD-YuNet license-plate detector.
- Model-specific ONNX preprocessing and output decoders.
- Fixed-size overlapping crops for plate detection, mapped back to source coordinates.
- Manual box dragging/deletion and local pixelation preview.

The POC uses ONNX Runtime directly and does not bundle OpenCV's full Android DNN runtime.
The production app should connect validated suggestions to its existing `BlurRegion`/jpegtran
pipeline rather than reuse this pixelation preview.

## Model and size summary

| Model | Raw file | Packaged APK measurement |
|---|---:|---:|
| Face YuNet | 227 KB | 199 KB |
| LPD-YuNet full precision (active) | about 4.0 MB | measure from release APK |
| LPD-YuNet int8 (benchmark candidate) | about 1.0 MB | measure from release APK |
| Both models plus both plate variants | about 5.2 MB raw | measure from release APK |

The current one-face/one-plate comparison is in
[`benchmark/runtime-comparison.md`](benchmark/runtime-comparison.md). Model provenance,
checksums, and upstream licensing are in
[`app/src/main/assets/models/README.md`](app/src/main/assets/models/README.md).

## Build and run

Configure an Android SDK in `local.properties` or `ANDROID_HOME`, then run:

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew printPocSize
```

## Evaluation

The pinned evaluation corpus and measurement requirements are documented in
[`benchmark/README.md`](benchmark/README.md). The app is a benchmark harness, not a complete
privacy-redaction product: detection recall, false positives, latency, memory, and coordinate
mapping must be evaluated on real Commons images before integration.

## Limitations

- LPD-YuNet was trained primarily on Chinese plates; recall may be poor for other regions.
- The bundled plate graph has fixed input dimensions, so wide images use overlapping crops.
- Adding a new model family requires a model adapter for its input/output contract.
- These models cover faces and plates only; they do not implement the other future AI use cases.

## Licensing

POC application code is MIT-licensed. Model files and reused upstream decoder logic retain
their own licenses; consult the model README before redistribution.
