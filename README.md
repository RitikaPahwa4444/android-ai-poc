# Android face and license plate detector POC

Standalone Android proof of concept for local face and license-plate suggestions. Built to understand impact on app size.  

<img width="277" height="606" alt="Screenshot 2026-08-01 at 8 05 44 PM" src="https://github.com/user-attachments/assets/44973050-9f46-48e8-b568-ecf62b20530a" />


## Current implementation

- ONNX Runtime Android 1.22.0; minSdk 24; configured for Android 16 KB page-size packaging.
- OpenCV Zoo YuNet face detector.
- OpenCV Zoo LPD-YuNet license-plate detector.
- Model-specific ONNX preprocessing and output decoders.
- Fixed-size overlapping crops for plate detection, mapped back to source coordinates.
- Manual box dragging/deletion and local pixelation preview.

The POC uses ONNX Runtime directly and does not bundle OpenCV's full Android DNN runtime.
The production app should connect validated suggestions to its existing `BlurRegion`/jpegtran
pipeline rather than reuse this pixelation preview.

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

## Optional reduced ONNX Runtime build

The checked-in [`tools/reduced_ops.config`](tools/reduced_ops.config) covers the two
active models plus the bundled full-precision plate variant. The build script
regenerates it from every `.onnx` file under `app/src/main/assets/models`, preventing
the runtime from failing if the full-precision plate asset is selected during testing.
To reproduce the native build, install
Git, Python 3, the Android SDK/NDK, and CMake, then run:

```bash
tools/build_reduced_onnxruntime.sh
```

The script pins ONNX Runtime `v1.22.0`, creates a temporary Python environment,
installs the model-analysis dependencies, and builds Java bindings for `arm64-v8a`.
It intentionally uses a reduced-operator build without `--minimal_build`, because
the app loads ONNX files directly; minimal builds require ORT-format model files.
The script fetches Eigen commit `1d8b82b0740839c0de7f1242a3585e3390ff5f33`; this
works around the stale Eigen archive checksum currently encountered by the upstream
build. The generated
`libonnxruntime.so` and `libonnxruntime4j_jni.so` must both be copied to
`app/src/main/jniLibs/arm64-v8a/` before removing the Maven dependency. Verify the
release APK, detector behavior, and 16 KB ELF alignment first.

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
