# Commons AI Android library and ajpegtran demo

Android library for local face and license-plate suggestions, with a standalone demo app.

<img width="277" height="606" alt="Screenshot 2026-08-01 at 8 05 44 PM" src="https://github.com/user-attachments/assets/44973050-9f46-48e8-b568-ecf62b20530a" />


## Library structure

The publishable `library` module is one artifact with internal package boundaries:

- `common`: public generic detection contract.
- `runtime`: internal ONNX Runtime lifecycle.
- `vision`: internal YuNet and legacy face fallback implementations.
- `demo`: application UI, manual review, and the ajpegtran-facing export path.

Consumers depend only on `org.commons:commons-ai:0.1.0` and use the stable factory API:

```kotlin
val detector = CommonsVision.detector(context)
val result = detector.detect(bitmap, DetectionOptions())
when (result) {
    is DetectionResult.Success -> use(result.detections)
    is DetectionResult.Partial -> showUnsupported(result.skipped)
    is DetectionResult.Unavailable -> showError(result.reason)
}
```

`detect` is suspend and must run from a coroutine. `Detection.bounds` are axis-aligned
pixel coordinates in the exact bitmap supplied; do not scale the bitmap between detection
and ajpegtran conversion. On API 24+, ONNX Runtime provides face and INT8 LPD-YuNet
plate detection. Below API 24, face detection uses `MediaFaceDetector` and plates are
reported explicitly in `Partial.skipped`.
The library does not depend on ajpegtran and does not apply blur or pixelation.

### Connecting detections to ajpegtran

Consumers can decode an image with known dimensions, then map each `Detection.bounds` to
integer `left/top/width/height` values in the original JPEG coordinate space.
Map them to ajpegtran's `BlurRegion(width, height, cornerX, cornerY,
blockWidth, blockHeight, aligned)` and call `Jpegtran.blur(regions)`, followed by
`save(destinationUri)`. `Jpegtran` manages its native file descriptors and
temporary files; callers should call `cleanup()` after saving.

The demo's `Ajpegtran` adapter mirrors the upstream JNI API. Add the upstream
native module from [commons-app/ajpegtran](https://github.com/commons-app/ajpegtran)
to the app build, then call:

```kotlin
val jpegtran = Jpegtran(context, inputUri)
try {
    jpegtran.blur(regions)
    jpegtran.save(outputUri)
} finally {
    jpegtran.cleanup()
}
```

The demo uses `ContentResolver` file descriptors and runs the native transformation on
`Dispatchers.IO`, displaying both success and native-error states. The library has no
ajpegtran dependency and never applies blur or pixelization.

## Current implementation

- ONNX Runtime Android 1.22.0; minSdk 24; configured for Android 16 KB page-size packaging.
- OpenCV Zoo YuNet face detector.
- OpenCV Zoo LPD-YuNet license-plate detector.
- Model-specific ONNX preprocessing and output decoders.
- Fixed-size overlapping crops for plate detection, mapped back to source coordinates.
- Manual box dragging/deletion and local pixelation preview.

The POC uses ONNX Runtime directly and does not bundle OpenCV's full Android DNN runtime.
The demo's **Export JPEG** action uses the ajpegtran adapter for lossless JPEG
redaction; **Redact** remains a bitmap preview for manual review.

The current one-face/one-plate comparison is in
[`benchmark/runtime-comparison.md`](benchmark/runtime-comparison.md). Model provenance,
checksums, and upstream licensing are in
[`library/src/main/assets/models/README.md`](library/src/main/assets/models/README.md).

## Build and publish

Configure an Android SDK in `local.properties` or `ANDROID_HOME`, and use JDK 17
(`JAVA_HOME`) for the Android Gradle build. JDK 24 can fail AGP’s `jlink` transform
of `core-for-system-modules.jar`. Then run:

```bash
./gradlew :demo:assembleDebug
./gradlew :library:assembleRelease
./gradlew :library:publishReleasePublicationToMavenLocal
./gradlew :library:publishRuntimePublicationToMavenLocal
```

The library uses Maven coordinates `org.commons:commons-ai:0.1.0`; consume the local
artifact with `mavenLocal()` and one dependency:

```kotlin
implementation("org.commons:commons-ai:0.1.0")
```

The release POM brings `org.commons:commons-ai-runtime:0.1.0` transitively. Publish
both publications to the same Maven repository; consumers still declare only the
`commons-ai` dependency.

configure a repository, signing, and release version in CI before publishing to
Maven Central.

Use JDK 17, Android SDK 36, NDK 27.2.12479018, and CMake 3.22.1. Run size and benchmark
reports from `benchmark/`. Verify packaged native objects with `llvm-readelf -l` and
confirm every `LOAD` segment alignment is compatible with 16 KB pages. If Gradle fails,
report the exact task and complete error output.

## Adding a model

Add the model under the appropriate internal asset directory and record its source URL,
license, checksum, input dimensions, tensor layout, preprocessing, output tensors, and
decoder. Add a model descriptor, backend decoder, typed detection capability, fixture
images and expected cases; measure accuracy, latency, memory, and packaged size. Decide
whether it is bundled, optional, or benchmark-only, update the model inventory and
provenance, and add release notes. A model returning the generic contract does not require
Commons UI or ajpegtran changes.

## Optional reduced ONNX Runtime build

The app loads ORT-format versions of the source ONNX models. The original ONNX
files are preserved under `tools/source_models/` for provenance, while only the
converted `.ort` files under `library/src/main/assets/models/` are packaged. Conversion
preserves their graphs while saving optimization results
for a smaller mobile runtime. The build script regenerates the operator config from
the `.ort` files and copies the resulting AAR into `runtime`. To reproduce the native
build, install
Git, Python 3, the Android SDK/NDK, and CMake, then run:

```bash
tools/build_reduced_onnxruntime.sh
```

The script pins ONNX Runtime `v1.22.0`, creates a temporary Python environment,
installs the model-analysis dependencies, and builds Java bindings for `arm64-v8a`.
It uses the official `--minimal_build` flow because the app loads ORT-format files.
The script fetches Eigen commit `1d8b82b0740839c0de7f1242a3585e3390ff5f33`; this
works around the stale Eigen archive checksum currently encountered by the upstream
build. The generated
`libonnxruntime.so` and `libonnxruntime4j_jni.so` are packaged in
`library/src/main/onnxruntime-android-1.22.0-reduced.aar` before publishing. Verify the
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
