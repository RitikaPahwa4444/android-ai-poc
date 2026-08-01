package org.wikimedia.commons.aipoc

import android.graphics.RectF

/** A model prediction expressed in pixels of the displayed source bitmap. */
data class Detection(
    val label: String,
    val confidence: Float,
    val bounds: RectF
)

/** Supported bundled detectors. */
enum class DetectorKind(
    val label: String,
    val assetName: String,
    val inputWidth: Int,
    val inputHeight: Int,
    val threshold: Float
) {
    FACE("face", "models/face_detection_yunet_2023mar.onnx", 320, 320, 0.55f),
    LICENSE_PLATE(
        "license plate",
        "models/license_plate_detection_lpd_yunet_2023mar.onnx",
        320,
        240,
        0.45f
    )
}
