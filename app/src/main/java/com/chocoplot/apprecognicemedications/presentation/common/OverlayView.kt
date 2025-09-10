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
    
    // Surface lifecycle management
    private var isAttachedToWindow = false
    private var pendingInvalidation = false
    
    // Thread safety
    private val overlayLock = Object()

    fun setResults(boundingBoxes: List<BoundingBox>) {
        synchronized(overlayLock) {
            try {
                android.util.Log.d("OverlayView", "Setting ${boundingBoxes.size} bounding boxes")
                
                // Validate bounding boxes before setting
                val validBoxes = boundingBoxes.filter { box ->
                    box.x1 >= 0f && box.x1 <= 1f &&
                    box.y1 >= 0f && box.y1 <= 1f &&
                    box.x2 >= 0f && box.x2 <= 1f &&
                    box.y2 >= 0f && box.y2 <= 1f &&
                    box.x2 > box.x1 && box.y2 > box.y1
                }
                
                if (validBoxes.size != boundingBoxes.size) {
                    android.util.Log.w("OverlayView", "Filtered out ${boundingBoxes.size - validBoxes.size} invalid bounding boxes")
                }
                
                results = validBoxes
                
                // Safe invalidation
                if (isAttachedToWindow) {
                    post { invalidate() }
                } else {
                    pendingInvalidation = true
                }
                
            } catch (e: Exception) {
                android.util.Log.e("OverlayView", "Error setting results", e)
            }
        }
    }

    /** Call this once you know the input image size to scale boxes properly. */
    fun setImageSourceInfo(width: Int, height: Int) {
        synchronized(overlayLock) {
            try {
                if (width <= 0 || height <= 0) {
                    android.util.Log.w("OverlayView", "Invalid source dimensions: ${width}x${height}")
                    return
                }
                
                android.util.Log.d("OverlayView", "Setting image source size: ${width}x${height}")
                sourceWidth = width
                sourceHeight = height
                
                // Safe invalidation
                if (isAttachedToWindow) {
                    post { invalidate() }
                } else {
                    pendingInvalidation = true
                }
                
            } catch (e: Exception) {
                android.util.Log.e("OverlayView", "Error setting image source info", e)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        synchronized(overlayLock) {
            try {
                super.onDraw(canvas)
                
                android.util.Log.d("OverlayView", "onDraw called. Results: ${results.size}, View size: ${width}x${height}, Source: ${sourceWidth}x${sourceHeight}")
                
                if (results.isEmpty() || width <= 0 || height <= 0) {
                    android.util.Log.d("OverlayView", "No results to draw or invalid view size")
                    return
                }

                val scaleX: Float = if (sourceWidth > 0) width.toFloat() / sourceWidth else 1f
                val scaleY: Float = if (sourceHeight > 0) height.toFloat() / sourceHeight else 1f
                val padding: Float = 8f * resources.displayMetrics.density

                android.util.Log.d("OverlayView", "Scale factors: scaleX=$scaleX, scaleY=$scaleY")

                results.forEachIndexed { index, box ->
                    try {
                        // Scale coordinates from normalized (0-1) to actual pixels
                        val left = box.x1 * sourceWidth * scaleX
                        val top = box.y1 * sourceHeight * scaleY
                        val right = box.x2 * sourceWidth * scaleX
                        val bottom = box.y2 * sourceHeight * scaleY

                        // Validate scaled coordinates
                        if (left < 0 || top < 0 || right > width || bottom > height ||
                            right <= left || bottom <= top) {
                            android.util.Log.w("OverlayView", "Skipping invalid box $index: ($left, $top, $right, $bottom)")
                            return@forEachIndexed
                        }

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
                        
                    } catch (e: Exception) {
                        android.util.Log.e("OverlayView", "Error drawing box $index", e)
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("OverlayView", "Error in onDraw", e)
            }
        }
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        synchronized(overlayLock) {
            isAttachedToWindow = true
            if (pendingInvalidation) {
                post { invalidate() }
                pendingInvalidation = false
            }
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        synchronized(overlayLock) {
            isAttachedToWindow = false
            // Clear results to prevent memory leaks
            results = emptyList()
        }
    }
    
    fun clearResults() {
        synchronized(overlayLock) {
            results = emptyList()
            if (isAttachedToWindow) {
                post { invalidate() }
            }
        }
    }
}
