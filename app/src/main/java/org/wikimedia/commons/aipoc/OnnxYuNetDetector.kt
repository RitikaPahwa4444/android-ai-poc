package org.wikimedia.commons.aipoc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Runs the OpenCV Zoo YuNet and LPD-YuNet ONNX graphs through ONNX Runtime.
 *
 * Face YuNet and LPD-YuNet share the ONNX Runtime integration, but they do not
 * share the output contract: face YuNet has cls/obj/bbox heads, while LPD-YuNet
 * has SSD-style loc/conf/iou outputs and four corner points per detection.
 */
class OnnxYuNetDetector(
    context: Context,
    private val kind: DetectorKind
) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputWidth: Int
    private val inputHeight: Int

    init {
        val assetFileName = kind.assetName.substringAfterLast('/')
        val bundledModels = context.assets.list("models")?.toList().orEmpty()
        require(assetFileName in bundledModels) {
            "Missing model asset '${kind.assetName}'. Bundled models: ${bundledModels.joinToString()}"
        }
        val modelBytes = context.assets.open(kind.assetName).use { it.readBytes() }
        session = environment.createSession(modelBytes, OrtSession.SessionOptions())
        val inputInfo = session.inputInfo.values.first().info as TensorInfo
        val shape = inputInfo.shape
        require(shape.size == 4) { "Expected NCHW model input, got ${shape.contentToString()}" }
        inputHeight = shape[2].takeIf { it > 0 }?.toInt() ?: kind.inputHeight
        inputWidth = shape[3].takeIf { it > 0 }?.toInt() ?: kind.inputWidth
    }

    /** Detects faces or plates and maps model coordinates back to the source bitmap. */
    fun detect(source: Bitmap): List<Detection> {
        val regions = if (kind == DetectorKind.LICENSE_PLATE) {
            plateRegions(source)
        } else {
            listOf(InferenceRegion(0, 0, source.width, source.height))
        }
        val detections = regions.flatMap { region ->
            val crop = Bitmap.createBitmap(source, region.left, region.top, region.width, region.height)
            try {
                detectRegion(crop).map { detection ->
                    detection.copy(
                        bounds = RectF(detection.bounds).apply {
                            offset(region.left.toFloat(), region.top.toFloat())
                        }
                    )
                }
            } finally {
                crop.recycle()
            }
        }
        return nonMaximumSuppression(detections)
    }

    private fun detectRegion(source: Bitmap): List<Detection> {
        // The bundled models have fixed input dimensions. Plate detection gets
        // more detail through tiled crops rather than an invalid dynamic shape.
        val inferenceWidth = inputWidth
        val inferenceHeight = inputHeight
        val input = FloatArray(1 * 3 * inferenceWidth * inferenceHeight)
        val resized = Bitmap.createScaledBitmap(source, inferenceWidth, inferenceHeight, true)
        try {
            val pixels = IntArray(inferenceWidth * inferenceHeight)
            resized.getPixels(pixels, 0, inferenceWidth, 0, 0, inferenceWidth, inferenceHeight)
            val planeSize = inferenceWidth * inferenceHeight
            for (y in 0 until inferenceHeight) {
                for (x in 0 until inferenceWidth) {
                    val color = pixels[y * inferenceWidth + x]
                    val index = y * inferenceWidth + x
                    // OpenCV Zoo models are trained with OpenCV's BGR input convention.
                    // Match OpenCV's FaceDetectorYN/blobFromImage preprocessing:
                    // BGR channels, unchanged 8-bit pixel scale (0..255).
                    input[index] = (color and 0xff).toFloat()
                    input[planeSize + index] = ((color shr 8) and 0xff).toFloat()
                    input[2 * planeSize + index] = ((color shr 16) and 0xff).toFloat()
                }
            }

            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(input),
                longArrayOf(1, 3, inferenceHeight.toLong(), inferenceWidth.toLong())
            ).use { tensor ->
                session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                    val heads = mutableListOf<Head>()
                    for (index in 0 until result.size()) {
                        val value = result[index]
                        (value as? OnnxTensor)?.let { tensorValue ->
                            Head(session.outputNames.elementAt(index), tensorValue)
                        }?.let(heads::add)
                    }
                    return if (kind == DetectorKind.LICENSE_PLATE) {
                        decodeLicensePlates(
                            heads,
                            source.width,
                            source.height,
                            inferenceWidth,
                            inferenceHeight
                        )
                    } else {
                        decodeFaces(heads, source.width, source.height, inferenceWidth, inferenceHeight)
                    }
                }
            }
        } finally {
            if (resized !== source) resized.recycle()
        }
    }

    private fun decodeFaces(
        heads: List<Head>,
        sourceWidth: Int,
        sourceHeight: Int,
        modelWidth: Int,
        modelHeight: Int
    ): List<Detection> {
        val grouped = heads.associateBy { it.name }
        val detections = mutableListOf<Detection>()
        for (stride in intArrayOf(8, 16, 32)) {
            val cls = find(grouped, "cls_$stride") ?: continue
            val obj = find(grouped, "obj_$stride") ?: continue
            val bbox = find(grouped, "bbox_$stride") ?: continue
            val count = min(cls.size, min(obj.size, bbox.size / 4))
            val gridWidth = (modelWidth + stride - 1) / stride
            for (index in 0 until count) {
                val score = sqrt(probability(cls[index]) * probability(obj[index]))
                if (score < kind.threshold) continue
                val gridX = index % gridWidth
                val gridY = index / gridWidth
                val offset = index * 4
                val centerX = (bbox[offset] + gridX) * stride
                val centerY = (bbox[offset + 1] + gridY) * stride
                val width = exp(bbox[offset + 2]) * stride
                val height = exp(bbox[offset + 3]) * stride
                val left = centerX - width / 2f
                val top = centerY - height / 2f
                val box = RectF(
                    left * sourceWidth / modelWidth,
                    top * sourceHeight / modelHeight,
                    (left + width) * sourceWidth / modelWidth,
                    (top + height) * sourceHeight / modelHeight
                )
                box.intersect(0f, 0f, sourceWidth.toFloat(), sourceHeight.toFloat())
                if (box.width() > 1f && box.height() > 1f) {
                    detections += Detection(kind.label, score, box)
                }
            }
        }
        return nonMaximumSuppression(detections)
    }

    /**
     * Decodes OpenCV Zoo's LPD-YuNet output contract. The model emits:
     *   loc: [N, 14] (SSD offsets; four corners use columns 4, 6, 10, 12)
     *   conf: [N, 2]  (background and plate confidence)
     *   iou: [N, 1]   (predicted localization quality)
     *
     * This follows the reference lpd_yunet.py implementation. The app's
     * BlurRegion pipeline currently accepts axis-aligned rectangles, so the
     * predicted quadrilateral is conservatively enclosed by one rectangle.
     */
    private fun decodeLicensePlates(
        heads: List<Head>,
        sourceWidth: Int,
        sourceHeight: Int,
        modelWidth: Int,
        modelHeight: Int
    ): List<Detection> {
        val grouped = heads.associateBy { it.name.lowercase() }
        val loc = findByName(grouped, "loc") ?: return emptyList()
        val conf = findByName(grouped, "conf") ?: return emptyList()
        val iou = findByName(grouped, "iou") ?: return emptyList()
        val priors = generatePlatePriors(modelWidth, modelHeight)
        val count = min(priors.size, min(loc.size / 14, min(conf.size / 2, iou.size)))
        val detections = mutableListOf<Detection>()
        val scaleX = modelWidth.toFloat()
        val scaleY = modelHeight.toFloat()

        for (index in 0 until count) {
            val classScore = conf[index * 2 + 1].coerceIn(0f, 1f)
            val iouScore = iou[index].coerceIn(0f, 1f)
            val score = sqrt(classScore * iouScore)
            if (score < kind.threshold) continue

            val prior = priors[index]
            val offset = index * 14
            val points = floatArrayOf(
                (prior.cx + loc[offset + 4] * 0.1f * prior.width) * scaleX,
                (prior.cy + loc[offset + 5] * 0.1f * prior.height) * scaleY,
                (prior.cx + loc[offset + 6] * 0.1f * prior.width) * scaleX,
                (prior.cy + loc[offset + 7] * 0.1f * prior.height) * scaleY,
                (prior.cx + loc[offset + 10] * 0.1f * prior.width) * scaleX,
                (prior.cy + loc[offset + 11] * 0.1f * prior.height) * scaleY,
                (prior.cx + loc[offset + 12] * 0.1f * prior.width) * scaleX,
                (prior.cy + loc[offset + 13] * 0.1f * prior.height) * scaleY
            )
            val left = points.filterIndexed { point, _ -> point % 2 == 0 }.minOrNull() ?: continue
            val top = points.filterIndexed { point, _ -> point % 2 == 1 }.minOrNull() ?: continue
            val right = points.filterIndexed { point, _ -> point % 2 == 0 }.maxOrNull() ?: continue
            val bottom = points.filterIndexed { point, _ -> point % 2 == 1 }.maxOrNull() ?: continue
            val box = RectF(
                left * sourceWidth / scaleX,
                top * sourceHeight / scaleY,
                right * sourceWidth / scaleX,
                bottom * sourceHeight / scaleY
            )
            box.intersect(0f, 0f, sourceWidth.toFloat(), sourceHeight.toFloat())
            if (box.width() > 1f && box.height() > 1f) {
                detections += Detection(kind.label, score, box)
            }
        }
        return nonMaximumSuppression(detections)
    }

    private fun generatePlatePriors(modelWidth: Int, modelHeight: Int): List<PlatePrior> {
        val minSizes = arrayOf(
            intArrayOf(10, 16, 24),
            intArrayOf(32, 48),
            intArrayOf(64, 96),
            intArrayOf(128, 192, 256)
        )
        val steps = intArrayOf(8, 16, 32, 64)
        fun halve(value: Int): Int = value / 2
        val featureMap2 = intArrayOf(halve((modelHeight + 1) / 2), halve((modelWidth + 1) / 2))
        val featureMap3 = intArrayOf(halve(featureMap2[0]), halve(featureMap2[1]))
        val featureMap4 = intArrayOf(halve(featureMap3[0]), halve(featureMap3[1]))
        val featureMap5 = intArrayOf(halve(featureMap4[0]), halve(featureMap4[1]))
        val featureMap6 = intArrayOf(halve(featureMap5[0]), halve(featureMap5[1]))
        val featureMaps = arrayOf(featureMap3, featureMap4, featureMap5, featureMap6)
        val priors = mutableListOf<PlatePrior>()
        for (feature in featureMaps.indices) {
            val rows = featureMaps[feature][0]
            val columns = featureMaps[feature][1]
            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    for (size in minSizes[feature]) {
                        priors += PlatePrior(
                            cx = (column + 0.5f) * steps[feature] / modelWidth,
                            cy = (row + 0.5f) * steps[feature] / modelHeight,
                            width = size.toFloat() / modelWidth,
                            height = size.toFloat() / modelHeight
                        )
                    }
                }
            }
        }
        return priors
    }

    private fun plateRegions(source: Bitmap): List<InferenceRegion> {
        val targetAspect = inputWidth.toFloat() / inputHeight
        val sourceAspect = source.width.toFloat() / source.height
        if (sourceAspect <= targetAspect) {
            val cropHeight = min(source.height, (source.width / targetAspect).roundToInt())
            return slidingRegions(source.height, cropHeight).map {
                InferenceRegion(0, it, source.width, cropHeight)
            }
        }

        val cropWidth = min(source.width, (source.height * targetAspect).roundToInt())
        return slidingRegions(source.width, cropWidth).map {
            InferenceRegion(it, 0, cropWidth, source.height)
        }
    }

    private fun slidingRegions(total: Int, window: Int): List<Int> {
        if (window >= total) return listOf(0)
        val last = total - window
        return listOf(0, last / 2, last).distinct()
    }

    private fun find(heads: Map<String, Head>, expectedName: String): FloatArray? {
        val head = heads.entries.firstOrNull { it.key.contains(expectedName) }?.value ?: return null
        return head.values
    }

    private fun findByName(heads: Map<String, Head>, expectedName: String): FloatArray? =
        heads.entries.firstOrNull { it.key == expectedName || it.key.endsWith("/$expectedName") }?.value?.values

    /** YuNet exports sigmoid probabilities, not logits. Match OpenCV's clamp. */
    private fun probability(value: Float): Float = value.coerceIn(0f, 1f)

    private fun nonMaximumSuppression(input: List<Detection>): List<Detection> {
        val remaining = input.sortedByDescending { it.confidence }.toMutableList()
        val selected = mutableListOf<Detection>()
        while (remaining.isNotEmpty()) {
            val best = remaining.removeAt(0)
            selected += best
            remaining.removeAll { intersectionOverUnion(best.bounds, it.bounds) > 0.3f }
        }
        return selected
    }

    private fun intersectionOverUnion(first: RectF, second: RectF): Float {
        val overlap = RectF(first)
        if (!overlap.intersect(second)) return 0f
        val intersection = overlap.width() * overlap.height()
        val union = first.width() * first.height() + second.width() * second.height() - intersection
        return if (union <= 0f) 0f else max(0f, min(1f, intersection / union))
    }

    override fun close() {
        session.close()
    }

    private data class Head(val name: String, val tensor: OnnxTensor) {
        val size: Int get() = tensor.info.shape.fold(1L) { acc, value -> acc * value }.toInt()
        val values: FloatArray
            get() = tensor.floatBuffer.let { buffer ->
                val copy = FloatArray(buffer.remaining())
                buffer.get(copy)
                copy
        }
    }

    private data class PlatePrior(
        val cx: Float,
        val cy: Float,
        val width: Float,
        val height: Float
    )

    private data class InferenceRegion(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int
    )
}
