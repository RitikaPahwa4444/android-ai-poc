package org.commons.ai.vision

import android.content.Context

object CommonsVision {
    fun faceDetector(context: Context): FaceDetector =
        OnnxYuNetDetector(context, DetectorKind.FACE)

    fun plateDetector(context: Context): PlateDetector =
        OnnxYuNetDetector(context, DetectorKind.LICENSE_PLATE)
}
