package org.commons.ai.demo

import org.commons.ai.common.*
import org.commons.ai.vision.CommonsVision

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment

/** Standalone benchmark and redaction POC for local face and plate detection. */
class MainActivity : ComponentActivity() {
    private lateinit var imageView: ImageView
    private lateinit var overlay: DetectionOverlayView
    private lateinit var status: TextView
    private var bitmap: Bitmap? = null
    private var sourceUri: Uri? = null
    private var detector: CommonsVision? = null
    private var threshold = 0.5f
    private var thresholdState by mutableFloatStateOf(0.5f)
    private var statusMessage by mutableStateOf("")

    private val createRedactedImage =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
            val source = sourceUri
            val regions = overlay.getDetections()
            if (uri == null || source == null || regions.isEmpty()) return@registerForActivityResult
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        Ajpegtran.pixelize(
                                    this@MainActivity,
                                    source,
                                    uri,
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
                result.onSuccess { setStatus("Saved ajpegtran-redacted JPEG.") }
                    .onFailure { setStatus("ajpegtran failed: ${diagnosticMessage(it)}") }
            }
        }

    private val openImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { loadImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        setStatus("Select a photo. Inference stays on this device.")
    }

    private fun buildUi() {
        status = TextView(this)
        setContent {
            MaterialTheme {
                Scaffold { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Commons AI", style = MaterialTheme.typography.headlineSmall)
                        Text("Review detected faces and license plates", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { openImage.launch(arrayOf("image/*")) }) { Text("Open") }
                            Button(onClick = { detect() }, enabled = bitmap != null) { Text("Detect") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { applyRedaction() }, enabled = ::overlay.isInitialized && overlay.getDetections().isNotEmpty()) { Text("Redact") }
                            TextButton(onClick = { overlay.removeSelected() }) { Text("Delete") }
                        }
                        Text(statusMessage, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
                        Text("Confidence threshold: ${(thresholdState * 100).toInt()}")
                        Slider(
                            value = thresholdState,
                            onValueChange = { thresholdState = it; threshold = it },
                            valueRange = 0.05f..0.95f,
                            onValueChangeFinished = { if (bitmap != null) detect() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            factory = {
                                val frame = android.widget.FrameLayout(it)
                                imageView = ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER; background = ColorDrawable(0xffeeeeee.toInt()) }
                                overlay = DetectionOverlayView(it)
                                frame.addView(imageView)
                                frame.addView(overlay)
                                frame
                            }
                        )
                    }
                }
            }
        }
    }

    private fun loadImage(uri: Uri) {
        sourceUri = uri
        contentResolver.openInputStream(uri)?.use { stream ->
            val decoded = BitmapFactory.decodeStream(stream) ?: return
            bitmap = decoded
            imageView.setImageBitmap(bitmap)
            overlay.setSourceSize(bitmap!!.width, bitmap!!.height)
            overlay.setDetections(emptyList())
            setStatus("Loaded ${bitmap!!.width}×${bitmap!!.height}.")
        }
    }

    private fun detect() {
        val source = bitmap ?: run {
            setStatus("Open an image first.")
            return
        }
        setStatus("Running ONNX Runtime locally…")
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
                setStatus(String.format(
                    Locale.US,
                    "Detected %d regions (%d faces, %d plates) in %d ms. Tap/drag boxes; delete false positives.",
                    detections.size,
                    detections.count { it.type == DetectionType.FACE },
                    detections.count { it.type == DetectionType.LICENSE_PLATE },
                    result.second
                ))
            }.onFailure { error ->
                setStatus("Detection failed: ${diagnosticMessage(error)}")
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

    private fun setStatus(message: String) {
        statusMessage = message
        if (::status.isInitialized) status.text = message
    }

    private fun applyRedaction() {
        val source = bitmap ?: return
        val regions = overlay.getDetections().map { it.bounds }
        if (regions.isEmpty()) {
            setStatus("No regions selected. Run detection or draw boxes in a later POC iteration.")
            return
        }
        val redacted = source.copy(Bitmap.Config.ARGB_8888, true)
        pixelate(redacted, regions)
        bitmap?.recycle()
        bitmap = redacted
        imageView.setImageBitmap(redacted)
        overlay.setDetections(emptyList())
        setStatus("Applied local pixelation to ${regions.size} regions.")
    }

    private fun getDetector(): CommonsVision =
        detector ?: CommonsVision(this).also { detector = it }

    override fun onDestroy() {
        detector?.close()
        bitmap?.recycle()
        super.onDestroy()
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
