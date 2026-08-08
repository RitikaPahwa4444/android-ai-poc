# Bundled Android models

These models are downloaded from the OpenCV Zoo model directories. The original ONNX
files are preserved in `tools/source_models/`; the library packages only their converted
ORT files in this directory so the APK does not contain duplicate model data.

| Model | Size | SHA-256 |
|---|---:|---|
| `face_detection_yunet_2023mar.onnx` → `face_detection_yunet_2023mar.ort` | 228 KiB / 292 KiB | ONNX `8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4`<br>ORT `cf29145b114f046143a0f6f684f71ad65010df3aad0bc304e937c86d4259511b` |
| `license_plate_detection_lpd_yunet_2023mar_int8.onnx` → `license_plate_detection_lpd_yunet_2023mar_int8.ort` | 1.0 MiB / 1.1 MiB | ONNX `d67982a014fe93ad04612f565ed23ca010dcb0fd925d880ef0edf9cd7bdf931a`<br>ORT `7c807651293a65155ab0baebe0a53d7e839f5a1a091f8455948d53b95c257435` |

Source repository: https://github.com/opencv/opencv_zoo

Model files:

- Face YuNet:
  https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet
- LPD-YuNet:
  https://github.com/opencv/opencv_zoo/tree/main/models/license_plate_detection_yunet

The plate output decoder follows the public OpenCV Zoo reference implementation:
https://github.com/opencv/opencv_zoo/blob/main/models/license_plate_detection_yunet/lpd_yunet.py
Commons ML keeps only the Android/ONNX Runtime port needed for these models; it does not
bundle OpenCV's full Android DNN runtime.

The face YuNet directory is MIT licensed and the LPD-YuNet directory is Apache-2.0 licensed.
Review the exact model and dataset terms again before redistribution.

The library loads the face and INT8 plate `.ort` assets listed above. The
full-precision plate model is not bundled; add it only after measuring the
size/accuracy tradeoff and updating this inventory.

Both graphs use NCHW BGR input with the model-specific fixed dimensions
(face `320x320`, plate `320x240`) and 8-bit pixel values in the `0..255` range.
Face output heads are decoded per stride (`cls_*`, `obj_*`, `bbox_*`). Plate
outputs use `loc`, `conf`, and `iou`; the predicted quadrilateral is conservatively
represented as an axis-aligned rectangle for the public detection contract.
The active runtime is the reduced ONNX Runtime Java/native build pinned to
`1.22.0` in `tools/build_reduced_onnxruntime.sh`.
