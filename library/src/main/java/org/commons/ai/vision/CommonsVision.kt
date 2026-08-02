package org.commons.ai.vision

import android.content.Context
import android.graphics.Bitmap
import org.commons.ai.common.*

object CommonsVision {
    fun detector(context: Context): AiDetector = CombinedDetector(context)

    private class CombinedDetector(private val context: Context) : AiDetector {
        private val face = if (android.os.Build.VERSION.SDK_INT >= 24) OnnxYuNetDetector(context, DetectorKind.FACE) else null
        private val plate = if (android.os.Build.VERSION.SDK_INT >= 24) OnnxYuNetDetector(context, DetectorKind.LICENSE_PLATE) else null

        override suspend fun detect(bitmap: Bitmap, options: DetectionOptions): DetectionResult {
            val faceResult = face?.detect(bitmap, options) ?: MediaFaceFallback().detect(bitmap, options)
            val plateResult = plate?.detect(bitmap, options)
                ?: DetectionResult.Partial(emptyList(), listOf(DetectionCapability.LICENSE_PLATE))
            val faces = (faceResult as? DetectionResult.Success)?.detections.orEmpty()
            val plates = (plateResult as? DetectionResult.Success)?.detections.orEmpty()
            return if (plate == null) DetectionResult.Partial(faces + plates, listOf(DetectionCapability.LICENSE_PLATE))
            else DetectionResult.Success(faces + plates)
        }

        override fun close() {
            face?.close()
            plate?.close()
        }
    }

    private class MediaFaceFallback : AiDetector {
        override suspend fun detect(bitmap: Bitmap, options: DetectionOptions): DetectionResult {
            val detector = android.media.FaceDetector(bitmap.width, bitmap.height, options.maximumResults)
            val faces = arrayOfNulls<android.media.FaceDetector.Face>(options.maximumResults)
            val count = detector.findFaces(bitmap, faces)
            val detections = faces.take(count).mapNotNull { face ->
                face ?: return@mapNotNull null
                val midpoint = android.graphics.PointF()
                face.getMidPoint(midpoint)
                val radius = face.eyesDistance() * 1.8f
                Detection(DetectionType.FACE, face.confidence(), android.graphics.RectF(
                    midpoint.x - radius, midpoint.y - radius, midpoint.x + radius, midpoint.y + radius
                ).apply { intersect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()) })
            }
            return DetectionResult.Success(detections)
        }
        override fun close() = Unit
    }
}
