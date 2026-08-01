# Commons AI POC

Standalone Android proof of concept for local face and license-plate detection using
ONNX Runtime Mobile and OpenCV Zoo ONNX model assets.

The app does not import or modify `apps-android-commons`. It is intentionally isolated so
runtime/model experiments do not affect the production Commons application.

The POC targets minSdk 21 and uses ONNX Runtime Android 1.20.0. Published AAR manifests
for 1.21.x and newer declare API 24, while 1.20.0 was verified to declare API 21. This
keeps the POC aligned with the production Commons app baseline without using a risky
`tools:overrideLibrary` manifest override.

## What it demonstrates

- Fully local inference through `com.microsoft.onnxruntime:onnxruntime-android`.
- Bundled YuNet face and LPD-YuNet license-plate models.
- Model input preprocessing and multi-head YuNet decoding.
- Detection boxes mapped back to the displayed image.
- Dragging and deleting suggested regions.
- Local pixelation of accepted regions.
- Runtime latency and model-size reporting in the UI/build output.

The redaction implementation is a POC pixelation preview. The production app should use
the existing `BlurRegion`/jpegtran implementation after the model and coordinate pipeline
have been validated.

## Build and run

Configure an Android SDK in `local.properties` or `ANDROID_HOME`, then run:

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew printPocSize
```

The bundled models are 227 KB and 4.0 MB. Their SHA-256 values and source are recorded in
`app/src/main/assets/models/README.md`.

## Known limitation

The current decoder assumes the OpenCV Zoo YuNet output-head naming and encoding. The first
device run should be used to compare detections with the OpenCV reference implementation;
if a model revision changes output names, the model adapter should reject it with a clear
error rather than silently producing incorrect boxes.
