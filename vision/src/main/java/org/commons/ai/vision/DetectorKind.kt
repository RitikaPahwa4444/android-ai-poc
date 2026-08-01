package org.commons.ai.vision

import org.commons.ai.common.DetectionType

internal enum class DetectorKind(
    val label: String,
    val assetName: String,
    val inputWidth: Int,
    val inputHeight: Int,
    val threshold: Float,
    val detectionType: DetectionType
) {
    FACE("face", "models/face_detection_yunet_2023mar.ort", 320, 320, 0.55f, DetectionType.FACE),
    LICENSE_PLATE(
        "license plate",
        "models/license_plate_detection_lpd_yunet_2023mar_int8.ort",
        320,
        240,
        0.45f,
        DetectionType.LICENSE_PLATE
    )
}
