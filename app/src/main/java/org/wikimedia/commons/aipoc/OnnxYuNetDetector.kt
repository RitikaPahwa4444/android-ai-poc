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
import kotlin.math.sqrt

/**
 * Runs the OpenCV Zoo YuNet and LPD-YuNet ONNX graphs through ONNX Runtime.
 *
 * Both graphs expose the same three detection heads (strides 8, 16 and 32).
 * The plate graph uses the same box encoding, so it can share this decoder.
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
        val input = FloatArray(1 * 3 * inputWidth * inputHeight)
        val resized = Bitmap.createScaledBitmap(source, inputWidth, inputHeight, true)
        try {
            val pixels = IntArray(inputWidth * inputHeight)
            resized.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
            val planeSize = inputWidth * inputHeight
            for (y in 0 until inputHeight) {
                for (x in 0 until inputWidth) {
                    val color = pixels[y * inputWidth + x]
                    val index = y * inputWidth + x
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
                longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
            ).use { tensor ->
                session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                    val heads = mutableListOf<Head>()
                    for (index in 0 until result.size()) {
                        val value = result[index]
                        (value as? OnnxTensor)?.let { tensorValue ->
                            Head(session.outputNames.elementAt(index), tensorValue)
                        }?.let(heads::add)
                    }
                    return decode(heads, source.width, source.height)
                }
            }
        } finally {
            if (resized !== source) resized.recycle()
        }
    }

    private fun decode(heads: List<Head>, sourceWidth: Int, sourceHeight: Int): List<Detection> {
        val grouped = heads.associateBy { it.name }
        val detections = mutableListOf<Detection>()
        for (stride in intArrayOf(8, 16, 32)) {
            val cls = find(grouped, "cls_$stride") ?: continue
            val obj = find(grouped, "obj_$stride") ?: continue
            val bbox = find(grouped, "bbox_$stride") ?: continue
            val count = min(cls.size, min(obj.size, bbox.size / 4))
            val gridWidth = (inputWidth + stride - 1) / stride
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
                    left * sourceWidth / inputWidth,
                    top * sourceHeight / inputHeight,
                    (left + width) * sourceWidth / inputWidth,
                    (top + height) * sourceHeight / inputHeight
                )
                box.intersect(0f, 0f, sourceWidth.toFloat(), sourceHeight.toFloat())
                if (box.width() > 1f && box.height() > 1f) {
                    detections += Detection(kind.label, score, box)
                }
            }
        }
        return nonMaximumSuppression(detections)
    }

    private fun find(heads: Map<String, Head>, expectedName: String): FloatArray? {
        val head = heads.entries.firstOrNull { it.key.contains(expectedName) }?.value ?: return null
        return head.values
    }

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
}
