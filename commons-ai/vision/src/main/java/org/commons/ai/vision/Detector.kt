package org.commons.ai.vision

import android.graphics.Bitmap
import org.commons.ai.common.Detection
import org.commons.ai.common.DetectionOptions

interface Detector : AutoCloseable {
    fun detect(source: Bitmap, options: DetectionOptions = DetectionOptions()): List<Detection>
}

interface FaceDetector : Detector

interface PlateDetector : Detector
