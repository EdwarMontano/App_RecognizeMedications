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
        try {
            super.onDraw(canvas)
            
            // Quick check without synchronization for performance
            if (results.isEmpty() || width <= 0 || height <= 0) {
                return
            }
            
            // Get a snapshot of the current state with minimal synchronization
            val currentResults: List<BoundingBox>
            val currentSourceWidth: Int
            val currentSourceHeight: Int
            
            synchronized(overlayLock) {
                currentResults = results.toList() // Create defensive copy
                currentSourceWidth = sourceWidth
                currentSourceHeight = sourceHeight
            }
            
            if (currentResults.isEmpty() || currentSourceWidth <= 0 || currentSourceHeight <= 0) {
                return
            }

            // Calculate scaling factor for centerCrop behavior
            val sourceAspectRatio = currentSourceWidth.toFloat() / currentSourceHeight.toFloat()
            val viewAspectRatio = width.toFloat() / height.toFloat()
            
            val scaleX: Float
            val scaleY: Float
            val offsetX: Float
            val offsetY: Float
            
            if (sourceAspectRatio > viewAspectRatio) {
                // Image is wider than view, scale by height and center horizontally
                scaleY = height.toFloat() / currentSourceHeight.toFloat()
                scaleX = scaleY
                offsetX = (width - currentSourceWidth * scaleX) / 2f
                offsetY = 0f
            } else {
                // Image is taller than view, scale by width and center vertically
                scaleX = width.toFloat() / currentSourceWidth.toFloat()
                scaleY = scaleX
                offsetX = 0f
                offsetY = (height - currentSourceHeight * scaleY) / 2f
            }

            val padding: Float = 8f * resources.displayMetrics.density

            // Draw without synchronization to avoid blocking
            currentResults.forEachIndexed { index, box ->
                try {
                    // Scale coordinates from normalized (0-1) to actual pixels with centerCrop logic
                    val left = (box.x1 * currentSourceWidth * scaleX) + offsetX
                    val top = (box.y1 * currentSourceHeight * scaleY) + offsetY
                    val right = (box.x2 * currentSourceWidth * scaleX) + offsetX
                    val bottom = (box.y2 * currentSourceHeight * scaleY) + offsetY

                    // Validate scaled coordinates are within view bounds
                    if (right <= left || bottom <= top || right < 0 || bottom < 0 ||
                        left > width || top > height) {
                        return@forEachIndexed
                    }

                    // Clamp coordinates to view bounds
                    val clampedLeft = left.coerceAtLeast(0f)
                    val clampedTop = top.coerceAtLeast(0f)
                    val clampedRight = right.coerceAtMost(width.toFloat())
                    val clampedBottom = bottom.coerceAtMost(height.toFloat())

                    val rect = RectF(clampedLeft, clampedTop, clampedRight, clampedBottom)
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
