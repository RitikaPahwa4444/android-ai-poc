package org.commons.ai.common

import android.graphics.RectF

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
