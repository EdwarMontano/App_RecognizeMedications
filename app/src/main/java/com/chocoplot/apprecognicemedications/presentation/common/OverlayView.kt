package com.chocoplot.apprecognicemedications.presentation.common

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.chocoplot.apprecognicemedications.ml.model.BoundingBox
import kotlin.math.max

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val boxPaint: Paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
        color = Color.RED
        isAntiAlias = true
    }

    private val textBgPaint: Paint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(160, 0, 0, 0)
        isAntiAlias = true
    }

    private val textPaint: Paint = Paint().apply {
        color = Color.WHITE
        textSize = 14f * resources.displayMetrics.scaledDensity
        isAntiAlias = true
    }

    private var results: List<BoundingBox> = emptyList()
    private var sourceWidth: Int = 0
    private var sourceHeight: Int = 0

    fun setResults(boundingBoxes: List<BoundingBox>) {
        android.util.Log.d("OverlayView", "Setting ${boundingBoxes.size} bounding boxes")
        boundingBoxes.forEachIndexed { index, box ->
            android.util.Log.d("OverlayView", "Box $index: ${box.clsName} at (${box.x1}, ${box.y1}, ${box.x2}, ${box.y2}) conf=${box.cnf}")
        }
        results = boundingBoxes
        invalidate()
    }

    /** Call this once you know the input image size to scale boxes properly. */
    fun setImageSourceInfo(width: Int, height: Int) {
        android.util.Log.d("OverlayView", "Setting image source size: ${width}x${height}")
        sourceWidth = width
        sourceHeight = height
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        android.util.Log.d("OverlayView", "onDraw called. Results: ${results.size}, View size: ${width}x${height}, Source: ${sourceWidth}x${sourceHeight}")
        
        if (results.isEmpty()) {
            android.util.Log.d("OverlayView", "No results to draw")
            return
        }

        val scaleX: Float = if (sourceWidth > 0) width.toFloat() / sourceWidth else 1f
        val scaleY: Float = if (sourceHeight > 0) height.toFloat() / sourceHeight else 1f
        val padding: Float = 8f * resources.displayMetrics.density

        android.util.Log.d("OverlayView", "Scale factors: scaleX=$scaleX, scaleY=$scaleY")

        results.forEachIndexed { index, box ->
            // Scale coordinates from normalized (0-1) to actual pixels
            val left = box.x1 * sourceWidth * scaleX
            val top = box.y1 * sourceHeight * scaleY
            val right = box.x2 * sourceWidth * scaleX
            val bottom = box.y2 * sourceHeight * scaleY

            android.util.Log.d("OverlayView", "Box $index scaled: ($left, $top, $right, $bottom)")

            val rect = RectF(left, top, right, bottom)
            canvas.drawRect(rect, boxPaint)

            val label = "${box.clsName} ${(box.cnf * 100).toInt()}%"
            val textWidth = textPaint.measureText(label)
            val bgRect = RectF(
                rect.left,
                max(0f, rect.top - textPaint.textSize - padding),
                rect.left + textWidth + padding * 2,
                rect.top
            )
            canvas.drawRect(bgRect, textBgPaint)
            canvas.drawText(label, bgRect.left + padding, rect.top - padding / 2, textPaint)
        }
    }
}
