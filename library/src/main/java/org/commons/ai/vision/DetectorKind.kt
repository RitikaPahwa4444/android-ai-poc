package org.commons.ai.vision

import org.commons.ai.common.DetectionType

internal enum class DetectorKind(
    val assetName: String,
    val inputWidth: Int,
    val inputHeight: Int,
    val detectionType: DetectionType
) {
    FACE("models/face_detection_yunet_2023mar.ort", 320, 320, DetectionType.FACE),
    LICENSE_PLATE(
        "models/license_plate_detection_lpd_yunet_2023mar_int8.ort",
        320,
        240,
        DetectionType.LICENSE_PLATE
    )
}
