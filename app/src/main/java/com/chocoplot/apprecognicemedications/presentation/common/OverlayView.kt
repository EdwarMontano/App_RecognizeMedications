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
        results = boundingBoxes
        invalidate()
    }

    /** Call this once you know the input image size to scale boxes properly. */
    fun setImageSourceInfo(width: Int, height: Int) {
        sourceWidth = width
        sourceHeight = height
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (results.isEmpty()) return

        val scaleX: Float = if (sourceWidth > 0) width.toFloat() / sourceWidth else 1f
        val scaleY: Float = if (sourceHeight > 0) height.toFloat() / sourceHeight else 1f
        val padding: Float = 8f * resources.displayMetrics.density

        results.forEach { box ->
            val left = box.left * scaleX
            val top = box.top * scaleY
            val right = box.right * scaleX
            val bottom = box.bottom * scaleY

            val rect = RectF(left, top, right, bottom)
            canvas.drawRect(rect, boxPaint)

            val label = "${box.label} ${(box.score * 100).toInt()}%"
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
