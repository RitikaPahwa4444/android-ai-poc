package org.commons.ai.demo

import org.commons.ai.common.*
import org.commons.ai.vision.CommonsVision

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.SeekBar
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

/** Standalone benchmark and redaction POC for local face and plate detection. */
class MainActivity : ComponentActivity() {
    private lateinit var imageView: ImageView
    private lateinit var overlay: DetectionOverlayView
    private lateinit var status: TextView
    private var bitmap: Bitmap? = null
    private var sourceUri: Uri? = null
    private var detector: AiDetector? = null
    private var threshold = 0.5f
    private lateinit var thresholdLabel: TextView

    private val createRedactedImage =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
            val source = sourceUri
            val regions = overlay.getDetections()
            if (uri == null || source == null || regions.isEmpty()) return@registerForActivityResult
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        contentResolver.openFileDescriptor(source, "r").use { input ->
                            contentResolver.openFileDescriptor(uri, "w").use { output ->
                                checkNotNull(input)
                                checkNotNull(output)
                                Ajpegtran.pixelize(
                                    input,
                                    output,
                                    regions.map {
                                        val bounds = RectF(it.bounds).apply {
                                            inset(-width() * 0.12f, -height() * 0.12f)
                                        }
                                        Ajpegtran.PixelizeRegion(
                                            bounds.left.toInt().coerceAtLeast(0),
                                            bounds.top.toInt().coerceAtLeast(0),
                                            bounds.width().toInt().coerceAtLeast(1),
                                            bounds.height().toInt().coerceAtLeast(1)
                                        )
                                    }
                                ).getOrThrow()
                            }
                        }
                    }
                }
                result.onSuccess { status.text = "Saved ajpegtran-redacted JPEG." }
                    .onFailure { status.text = "ajpegtran failed: ${diagnosticMessage(it)}" }
            }
        }

    private val openImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { loadImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        status.text = "Select a photo. Inference stays on this device."
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { action() }
        }
        controls.addView(button("Open") { openImage.launch(arrayOf("image/*")) })
        controls.addView(button("Detect") { detect() })
        controls.addView(button("Redact") { applyRedaction() })
        controls.addView(button("Export JPEG") {
            if (sourceUri != null && overlay.getDetections().isNotEmpty()) {
                createRedactedImage.launch("redacted.jpg")
            } else {
                status.text = "Detect regions before exporting."
            }
        })
        controls.addView(button("Delete selected") { overlay.removeSelected() })
        root.addView(controls, LinearLayout.LayoutParams(-1, -2))

        status = TextView(this).apply { setPadding(0, 8, 0, 8) }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))

        thresholdLabel = TextView(this).apply {
            text = "Confidence threshold: 50%"
            setPadding(0, 8, 0, 0)
        }
        root.addView(thresholdLabel, LinearLayout.LayoutParams(-1, -2))
        val thresholdSeekBar = SeekBar(this).apply {
            max = 95
            progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                    threshold = (value.coerceAtLeast(5) / 100f)
                    thresholdLabel.text = "Confidence threshold: ${(threshold * 100).toInt()}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) { if (bitmap != null) detect() }
            })
        }
        root.addView(thresholdSeekBar, LinearLayout.LayoutParams(-1, -2))

        val imageFrame = object : android.widget.FrameLayout(this) {}
        imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = ColorDrawable(0xffeeeeee.toInt())
        }
        overlay = DetectionOverlayView(this)
        imageFrame.addView(imageView, android.widget.FrameLayout.LayoutParams(-1, -1))
        imageFrame.addView(overlay, android.widget.FrameLayout.LayoutParams(-1, -1))
        root.addView(imageFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun loadImage(uri: Uri) {
        sourceUri = uri
        contentResolver.openInputStream(uri)?.use { stream ->
            val decoded = BitmapFactory.decodeStream(stream) ?: return
            bitmap = downsample(decoded, 1800)
            if (bitmap !== decoded) decoded.recycle()
            imageView.setImageBitmap(bitmap)
            overlay.setSourceSize(bitmap!!.width, bitmap!!.height)
            overlay.setDetections(emptyList())
            status.text = "Loaded ${bitmap!!.width}×${bitmap!!.height}."
        }
    }

    private fun detect() {
        val source = bitmap ?: run {
            status.text = "Open an image first."
            return
        }
        status.text = "Running ONNX Runtime locally…"
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    val started = System.nanoTime()
                    val options = DetectionOptions(confidenceThreshold = threshold)
                    val result = getDetector().detect(source, options)
                    Pair(result, (System.nanoTime() - started) / 1_000_000)
                }
            }.onSuccess { result ->
                val detections = when (val value = result.first) {
                    is DetectionResult.Success -> value.detections
                    is DetectionResult.Partial -> value.detections
                    is DetectionResult.Unavailable -> emptyList()
                }
                overlay.setDetections(detections)
                status.text = String.format(
                    Locale.US,
                    "Detected %d regions (%d faces, %d plates) in %d ms. Tap/drag boxes; delete false positives.",
                    detections.size,
                    detections.count { it.type == DetectionType.FACE },
                    detections.count { it.type == DetectionType.LICENSE_PLATE },
                    result.second
                )
            }.onFailure { error ->
                status.text = "Detection failed: ${diagnosticMessage(error)}"
            }
        }
    }

    private fun diagnosticMessage(error: Throwable): String {
        val messages = buildList {
            var current: Throwable? = error
            while (current != null && size < 4) {
                val detail = current.message?.takeIf { it.isNotBlank() }
                add(detail ?: current.javaClass.simpleName)
                current = current.cause
            }
        }
        return messages.joinToString(" → ")
    }

    private fun applyRedaction() {
        val source = bitmap ?: return
        val regions = overlay.getDetections().map { it.bounds }
        if (regions.isEmpty()) {
            status.text = "No regions selected. Run detection or draw boxes in a later POC iteration."
            return
        }
        val redacted = source.copy(Bitmap.Config.ARGB_8888, true)
        pixelate(redacted, regions)
        bitmap?.recycle()
        bitmap = redacted
        imageView.setImageBitmap(redacted)
        overlay.setDetections(emptyList())
        status.text = "Applied local pixelation to ${regions.size} regions."
    }

    private fun getDetector(): AiDetector =
        detector ?: CommonsVision.detector(this).also { detector = it }

    override fun onDestroy() {
        detector?.close()
        bitmap?.recycle()
        super.onDestroy()
    }

    private fun downsample(source: Bitmap, maxDimension: Int): Bitmap {
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(source.width, source.height))
        return if (scale == 1f) source else Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt(),
            (source.height * scale).toInt(),
            true
        )
    }

    private fun pixelate(bitmap: Bitmap, regions: List<RectF>) {
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (original in regions) {
            val region = RectF(original)
            region.inset(-region.width() * 0.12f, -region.height() * 0.12f)
            region.intersect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            if (region.width() < 2f || region.height() < 2f) continue
            val left = region.left.toInt()
            val top = region.top.toInt()
            val width = region.width().toInt().coerceAtLeast(2)
            val height = region.height().toInt().coerceAtLeast(2)
            val crop = Bitmap.createBitmap(bitmap, left, top, width, height)
            val tiny = Bitmap.createScaledBitmap(crop, 12, 12, true)
            canvas.drawBitmap(tiny, null, region, paint)
            crop.recycle()
            tiny.recycle()
        }
    }
}
