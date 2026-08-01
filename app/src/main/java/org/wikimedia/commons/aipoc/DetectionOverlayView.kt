package org.wikimedia.commons.aipoc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** Displays detections and permits dragging or selecting a suggested redaction. */
class DetectionOverlayView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        style = Paint.Style.FILL
    }
    private val detections = mutableListOf<Detection>()
    private var sourceWidth = 1
    private var sourceHeight = 1
    private var selectedIndex = -1
    private var lastX = 0f
    private var lastY = 0f

    /** Sets the source bitmap dimensions used to mirror ImageView FIT_CENTER. */
    fun setSourceSize(width: Int, height: Int) {
        sourceWidth = width.coerceAtLeast(1)
        sourceHeight = height.coerceAtLeast(1)
        invalidate()
    }

    /** Replaces the currently displayed predictions. */
    fun setDetections(values: List<Detection>) {
        detections.clear()
        detections.addAll(values)
        selectedIndex = -1
        invalidate()
    }

    /** Removes the selected detection, allowing false positives to be corrected. */
    fun removeSelected() {
        if (selectedIndex in detections.indices) detections.removeAt(selectedIndex)
        selectedIndex = -1
        invalidate()
    }

    /** Returns the user-corrected regions. */
    fun getDetections(): List<Detection> = detections.map { it.copy(bounds = RectF(it.bounds)) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
        val offsetX = (width - sourceWidth * scale) / 2f
        val offsetY = (height - sourceHeight * scale) / 2f
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        detections.forEachIndexed { index, detection ->
            paint.color = if (index == selectedIndex) Color.YELLOW else Color.CYAN
            canvas.drawRect(detection.bounds, paint)
            textPaint.color = paint.color
            canvas.drawText(
                "${detection.label} ${(detection.confidence * 100).toInt()}%",
                detection.bounds.left,
                (detection.bounds.top - 8f).coerceAtLeast(28f),
                textPaint
            )
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scale = minOf(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
        val offsetX = (width - sourceWidth * scale) / 2f
        val offsetY = (height - sourceHeight * scale) / 2f
        val imageX = (event.x - offsetX) / scale
        val imageY = (event.y - offsetY) / scale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                selectedIndex = detections.indexOfLast { it.bounds.contains(imageX, imageY) }
                lastX = imageX
                lastY = imageY
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> if (selectedIndex in detections.indices) {
                val dx = imageX - lastX
                val dy = imageY - lastY
                detections[selectedIndex].bounds.offset(dx, dy)
                lastX = imageX
                lastY = imageY
                invalidate()
                return true
            }
        }
        return true
    }
}
