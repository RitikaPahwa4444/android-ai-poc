package org.commons.ai.vision

import org.commons.ai.common.DetectionType

private const val FACE_ASSET = "models/face_detection_yunet_2023mar.ort"
private const val FACE_INPUT_WIDTH = 320
private const val FACE_INPUT_HEIGHT = 320
private const val LICENSE_PLATE_ASSET = "models/license_plate_detection_lpd_yunet_2023mar_int8.ort"
private const val LICENSE_PLATE_INPUT_WIDTH = 320
private const val LICENSE_PLATE_INPUT_HEIGHT = 240

internal enum class DetectorKind(
    val assetName: String,
    val inputWidth: Int,
    val inputHeight: Int,
    val detectionType: DetectionType
) {
    FACE(FACE_ASSET, FACE_INPUT_WIDTH, FACE_INPUT_HEIGHT, DetectionType.FACE),
    LICENSE_PLATE(
        LICENSE_PLATE_ASSET,
        LICENSE_PLATE_INPUT_WIDTH,
        LICENSE_PLATE_INPUT_HEIGHT,
        DetectionType.LICENSE_PLATE
    )
}
