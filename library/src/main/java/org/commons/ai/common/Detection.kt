package org.commons.ai.common

import android.graphics.RectF
import android.graphics.Bitmap

interface AiDetector : AutoCloseable {
    suspend fun detect(bitmap: Bitmap, options: DetectionOptions = DetectionOptions()): DetectionResult
}

sealed interface DetectionResult {
    data class Success(val detections: List<Detection>) : DetectionResult
    data class Partial(val detections: List<Detection>, val skipped: List<DetectionCapability>) : DetectionResult
    data class Unavailable(val reason: String) : DetectionResult
}

enum class DetectionCapability { FACE, LICENSE_PLATE }

/** A model prediction expressed in pixels of the displayed source bitmap. */
data class Detection(
    val type: DetectionType,
    val confidence: Float,
    val bounds: RectF
)

enum class DetectionType { FACE, LICENSE_PLATE }

data class DetectionOptions(
    val confidenceThreshold: Float = 0.5f,
    val maximumResults: Int = 100
) {
    init {
        require(confidenceThreshold in 0f..1f) { "confidenceThreshold must be between 0 and 1" }
        require(maximumResults > 0) { "maximumResults must be positive" }
    }
}
