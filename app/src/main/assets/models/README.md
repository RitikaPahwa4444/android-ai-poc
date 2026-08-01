# Bundled POC models

These models are downloaded from the OpenCV Zoo model directories and are included only
in the standalone POC so that benchmarks are reproducible.

| Model | Size | SHA-256 |
|---|---:|---|
| `face_detection_yunet_2023mar.onnx` | 227 KB raw / 199 KB packaged | `8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4` |
| `license_plate_detection_lpd_yunet_2023mar_int8.onnx` | 1.0 MB raw / 769.4 KB packaged | `d67982a014fe93ad04612f565ed23ca010dcb0fd925d880ef0edf9cd7bdf931a` |

Source: https://github.com/opencv/opencv_zoo

The plate output decoder follows the public OpenCV Zoo reference implementation:
https://github.com/opencv/opencv_zoo/blob/main/models/license_plate_detection_yunet/lpd_yunet.py
The POC keeps only the Android/ONNX Runtime port needed for these two models; it does not
bundle OpenCV's full Android DNN runtime.

The face YuNet directory is MIT licensed. Review the exact model and dataset terms again
before redistributing either model in the Commons app.
