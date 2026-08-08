package org.commons.ml.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.RectF
import android.media.FaceDetector
import android.os.Build
import android.util.Log
import org.commons.ml.common.AiDetector
import org.commons.ml.common.Detection
import org.commons.ml.common.DetectionOptions
import org.commons.ml.common.DetectionResult
import org.commons.ml.common.DetectionType
import org.commons.ml.runtime.MlRuntimeException
import org.commons.ml.runtime.ModelRuntime
import org.commons.ml.runtime.OrtRuntime
import org.commons.ml.runtime.RuntimeClosedException

/**
 * Facade for on-device face and license-plate detection.
 *
 * Create one instance for the lifetime of the consumer and close it when it is
 * no longer needed. Detection is suspendable so callers can choose their own
 * coroutine dispatcher.
 */
class CommonsVision(context: Context) : AutoCloseable {
    private val detector: AiDetector = CombinedDetector(context.applicationContext)

    suspend fun detect(
        bitmap: Bitmap,
        options: DetectionOptions = DetectionOptions()
    ): DetectionResult = detector.detect(bitmap, options)

    override fun close() {
        detector.close()
    }

    private class CombinedDetector(
        context: Context
    ) : AiDetector {
        private val fallback = MediaFaceFallback()
        private val runtime: ModelRuntime? =
            if (Build.VERSION.SDK_INT >= 24) OrtRuntime(context) else null
        private val face: AiDetector?
        private val plate: AiDetector?
        private val plateInitializationError: MlRuntimeException?
        private var closed = false

        init {
            val activeRuntime = runtime
            if (activeRuntime == null) {
                face = null
                plate = null
                plateInitializationError = null
            } else {
                val faceResult = openDetector(activeRuntime, DetectorKind.FACE)
                face = faceResult.first

                val plateResult = openDetector(activeRuntime, DetectorKind.LICENSE_PLATE)
                plate = plateResult.first
                plateInitializationError = plateResult.second
            }
        }

        override suspend fun detect(bitmap: Bitmap, options: DetectionOptions): DetectionResult {
            checkOpen()
            val faceResult = try {
                face?.detect(bitmap, options) ?: fallback.detect(bitmap, options)
            } catch (error: MlRuntimeException) {
                Log.e(TAG, "Face detection failed (${error.code}).", error)
                return DetectionResult.Unavailable(
                    "Face detection unavailable (${error.code}): ${error.message}"
                )
            }

            val faces = when (faceResult) {
                is DetectionResult.Success -> faceResult.detections
                is DetectionResult.Partial -> faceResult.detections
                is DetectionResult.Unavailable -> return faceResult
            }

            val plates = if (plate == null) {
                plateInitializationError?.let {
                    Log.e(TAG, "License-plate detector unavailable (${it.code}).", it)
                }
                emptyList()
            } else {
                try {
                    when (val result = plate.detect(bitmap, options)) {
                        is DetectionResult.Success -> result.detections
                        is DetectionResult.Partial -> result.detections
                        is DetectionResult.Unavailable -> emptyList()
                    }
                } catch (error: MlRuntimeException) {
                    Log.e(TAG, "License-plate detection failed (${error.code}).", error)
                    emptyList()
                }
            }

            return if (plate == null) {
                DetectionResult.Partial(faces + plates, listOf(DetectionType.LICENSE_PLATE))
            } else {
                DetectionResult.Success(faces + plates)
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            var failure: MlRuntimeException? = null
            listOf(face, plate, fallback, runtime).forEach { resource ->
                if (resource == null) return@forEach
                try {
                    resource.close()
                } catch (error: MlRuntimeException) {
                    if (failure == null) failure = error
                }
            }
            failure?.let { throw it }
        }

        private fun checkOpen() {
            if (closed) throw RuntimeClosedException()
        }

        private fun openDetector(
            runtime: ModelRuntime,
            kind: DetectorKind
        ): Pair<AiDetector?, MlRuntimeException?> = try {
            OnnxYuNetDetector(runtime, kind) to null
        } catch (error: MlRuntimeException) {
            Log.e(TAG, "Unable to initialize ${kind.detectionType} detector (${error.code}).", error)
            null to error
        }

        private companion object {
            const val TAG = "CommonsVision"
        }
    }

    private class MediaFaceFallback : AiDetector {
        override suspend fun detect(bitmap: Bitmap, options: DetectionOptions): DetectionResult {
            val scale = minOf(1f, 2048f / maxOf(bitmap.width, bitmap.height).toFloat())
            val detectionBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else {
                bitmap
            }
            var width = detectionBitmap.width
            if (width % 2 != 0) width--
            if (width <= 0 || detectionBitmap.height <= 0) {
                return DetectionResult.Success(emptyList())
            }
            val rgb565 = Bitmap.createBitmap(width, detectionBitmap.height, Bitmap.Config.RGB_565)
            try {
                Canvas(rgb565).drawBitmap(detectionBitmap, 0f, 0f, null)
                val detector = FaceDetector(rgb565.width, rgb565.height, options.maximumResults)
                val faces = arrayOfNulls<FaceDetector.Face>(options.maximumResults)
                val count = detector.findFaces(rgb565, faces)
                val detections = faces.take(count).mapNotNull { face ->
                    face ?: return@mapNotNull null
                    val midpoint = PointF()
                    face.getMidPoint(midpoint)
                    val radius = face.eyesDistance() * 1.8f
                    Detection(
                        DetectionType.FACE,
                        face.confidence(),
                        RectF(
                            (midpoint.x - radius) / scale,
                            (midpoint.y - radius) / scale,
                            (midpoint.x + radius) / scale,
                            (midpoint.y + radius) / scale
                        ).apply {
                            intersect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                        }
                    )
                }
                return DetectionResult.Success(detections)
            } finally {
                rgb565.recycle()
                if (detectionBitmap !== bitmap) detectionBitmap.recycle()
            }
        }

        override fun close() = Unit
    }
}
