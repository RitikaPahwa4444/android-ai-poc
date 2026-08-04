package org.commons.ai.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import org.commons.ai.common.*

object CommonsVision {
    fun detector(context: Context): AiDetector = CombinedDetector(context)

    private class CombinedDetector(private val context: Context) : AiDetector {
        private val fallback = MediaFaceFallback()
        private val face: AiDetector?
        private val plate: AiDetector?

        init {
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                face = runCatching { OnnxYuNetDetector(context, DetectorKind.FACE) }.getOrNull()
                plate = runCatching { OnnxYuNetDetector(context, DetectorKind.LICENSE_PLATE) }.getOrNull()
            } else {
                face = null
                plate = null
            }
        }

        override suspend fun detect(bitmap: Bitmap, options: DetectionOptions): DetectionResult {
            val faceResult = runCatching {
                face?.detect(bitmap, options) ?: fallback.detect(bitmap, options)
            }.getOrElse { return DetectionResult.Unavailable("Face detection unavailable: ${it.message}") }
            val plateResult = plate?.let {
                runCatching { it.detect(bitmap, options) }.getOrNull()
            }
            val faces = (faceResult as? DetectionResult.Success)?.detections.orEmpty()
            val plates = (plateResult as? DetectionResult.Success)?.detections.orEmpty()
            return if (plateResult == null) DetectionResult.Partial(faces + plates, listOf(DetectionCapability.LICENSE_PLATE))
            else DetectionResult.Success(faces + plates)
        }

        override fun close() {
            face?.close()
            plate?.close()
        }
    }

    private class MediaFaceFallback : AiDetector {
        override suspend fun detect(bitmap: Bitmap, options: DetectionOptions): DetectionResult {
            var width = bitmap.width
            if (width % 2 != 0) width--
            if (width <= 0 || bitmap.height <= 0) {
                return DetectionResult.Success(emptyList())
            }
            val rgb565 = Bitmap.createBitmap(width, bitmap.height, Bitmap.Config.RGB_565)
            try {
                Canvas(rgb565).drawBitmap(bitmap, 0f, 0f, null)
                val detector = android.media.FaceDetector(rgb565.width, rgb565.height, options.maximumResults)
                val faces = arrayOfNulls<android.media.FaceDetector.Face>(options.maximumResults)
                val count = detector.findFaces(rgb565, faces)
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
            } finally {
                rgb565.recycle()
            }
        }
        override fun close() = Unit
    }
}
