// app/src/main/java/com/chocoplot/apprecognicemedications/ml/Detector.kt
package com.chocoplot.apprecognicemedications.ml

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.chocoplot.apprecognicemedications.ml.model.BoundingBox
import com.chocoplot.apprecognicemedications.core.CrashRecoveryManager
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String,
    private val detectorListener: DetectorListener
) {

    private var interpreter: Interpreter? = null
    private var labels = mutableListOf<String>()

    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numChannel = 0
    private var numElements = 0
    
    // Add thread safety and state management
    private var isInitialized = false
    private var isProcessing = false
    private val detectorLock = Object()
    
    // Memory management
    private var lastGcTime = 0L
    private var consecutiveOomErrors = 0
    
    // Performance monitoring
    private var totalInferences = 0
    private var failedInferences = 0

    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    fun setup() {
        synchronized(detectorLock) {
            try {
                if (isInitialized) {
                    android.util.Log.w("Detector", "Detector already initialized")
                    return
                }
                
                // Load model with error handling
                val model = try {
                    FileUtil.loadMappedFile(context, modelPath)
                } catch (e: Exception) {
                    android.util.Log.e("Detector", "Error loading model file", e)
                    return
                }
                
                val options = Interpreter.Options().apply {
                    numThreads = Math.min(4, Runtime.getRuntime().availableProcessors())
                    useNNAPI = false // Disable NNAPI to avoid potential crashes
                    // Note: allowFp16PrecisionForFp32 is not accessible in this TensorFlow Lite version
                }
                
                interpreter = try {
                    Interpreter(model, options)
                } catch (e: Exception) {
                    android.util.Log.e("Detector", "Error creating TensorFlow Lite interpreter", e)
                    return
                }

                val currentInterpreter = interpreter ?: return
                val inputShape = try {
                    currentInterpreter.getInputTensor(0)?.shape()
                } catch (e: Exception) {
                    android.util.Log.e("Detector", "Error getting input tensor shape", e)
                    return
                } ?: return
                
                val outputShape = try {
                    currentInterpreter.getOutputTensor(0)?.shape()
                } catch (e: Exception) {
                    android.util.Log.e("Detector", "Error getting output tensor shape", e)
                    return
                } ?: return

                tensorWidth = inputShape[1]
                tensorHeight = inputShape[2]
                numChannel = outputShape[1]
                numElements = outputShape[2]

                android.util.Log.d("Detector", "Model setup - Input: ${inputShape.contentToString()}, Output: ${outputShape.contentToString()}")
                android.util.Log.d("Detector", "Tensor dimensions: ${tensorWidth}x${tensorHeight}, channels: $numChannel, elements: $numElements")

                // Load labels with better error handling
                labels.clear()
                try {
                    context.assets.open(labelPath).use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            var line: String? = reader.readLine()
                            while (line != null && line.isNotEmpty()) {
                                labels.add(line.trim())
                                line = reader.readLine()
                            }
                        }
                    }
                    android.util.Log.d("Detector", "Loaded ${labels.size} labels: ${labels.take(5)}")
                } catch (e: IOException) {
                    android.util.Log.e("Detector", "Error loading labels", e)
                    return
                }
                
                isInitialized = true
                totalInferences = 0
                failedInferences = 0
                consecutiveOomErrors = 0
                
            } catch (e: Exception) {
                android.util.Log.e("Detector", "Unexpected error during setup", e)
                clear()
            }
        }
    }

    fun clear() {
        synchronized(detectorLock) {
            try {
                isInitialized = false
                isProcessing = false
                
                interpreter?.let { interp ->
                    try {
                        interp.close()
                    } catch (e: Exception) {
                        android.util.Log.e("Detector", "Error closing interpreter", e)
                    }
                }
                interpreter = null
                
                labels.clear()
                
                // Reset dimensions
                tensorWidth = 0
                tensorHeight = 0
                numChannel = 0
                numElements = 0
                
                // Log performance stats
                if (totalInferences > 0) {
                    val successRate = ((totalInferences - failedInferences) * 100.0 / totalInferences)
                    android.util.Log.d("Detector", "Detector cleared. Success rate: ${successRate.toInt()}% ($totalInferences total, $failedInferences failed)")
                }
                
                // Force garbage collection to clean up native resources
                System.gc()
                
            } catch (e: Exception) {
                android.util.Log.e("Detector", "Error clearing detector", e)
            }
        }
    }

    fun detect(frame: Bitmap) {
        synchronized(detectorLock) {
            // Check if detector is properly initialized and not already processing
            if (!isInitialized || isProcessing) {
                detectorListener.onEmptyDetect()
                return
            }
            
            // Check recovery mode and memory pressure
            if (CrashRecoveryManager.isInRecoveryMode()) {
                android.util.Log.w("Detector", "Skipping detection - in recovery mode")
                detectorListener.onEmptyDetect()
                return
            }
            
            val memoryStatus = CrashRecoveryManager.checkMemoryPressure()
            if (memoryStatus == CrashRecoveryManager.MemoryStatus.CRITICAL) {
                android.util.Log.w("Detector", "Skipping detection - critical memory pressure")
                CrashRecoveryManager.performRecoveryAction(CrashRecoveryManager.RecoveryAction.FORCE_GARBAGE_COLLECTION)
                detectorListener.onEmptyDetect()
                return
            }
            
            // Check if too many consecutive OOM errors
            if (consecutiveOomErrors >= MAX_CONSECUTIVE_OOM_ERRORS) {
                android.util.Log.e("Detector", "Too many consecutive OOM errors, skipping detection")
                detectorListener.onEmptyDetect()
                return
            }
            
            isProcessing = true
            totalInferences++
        }
        
        var resizedBitmap: Bitmap? = null
        var tensorImage: TensorImage? = null
        var output: TensorBuffer? = null
        var success = false
        
        try {
            val currentInterpreter = interpreter
            if (currentInterpreter == null || !isInitialized) {
                android.util.Log.w("Detector", "Interpreter not ready")
                return
            }
            
            // Validate frame
            if (frame.isRecycled || frame.width <= 0 || frame.height <= 0) {
                android.util.Log.w("Detector", "Invalid input frame")
                return
            }

            // Enhanced memory checking with adaptive thresholds
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val freeMemory = maxMemory - usedMemory
            val memoryPressureThreshold = if (consecutiveOomErrors > 0) 80 * 1024 * 1024 else 50 * 1024 * 1024
            
            if (freeMemory < memoryPressureThreshold) {
                android.util.Log.w("Detector", "Low memory (${freeMemory / (1024 * 1024)}MB free), skipping detection")
                
                // Trigger GC if we haven't done it recently
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastGcTime > 5000) { // 5 seconds
                    System.gc()
                    lastGcTime = currentTime
                }
                return
            }

            var inferenceTime = SystemClock.uptimeMillis()

            // Create resized bitmap with better error handling
            resizedBitmap = try {
                // Use ARGB_8888 for better compatibility
                Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false)?.also { scaled ->
                    if (scaled.config != Bitmap.Config.ARGB_8888) {
                        val converted = scaled.copy(Bitmap.Config.ARGB_8888, false)
                        if (scaled != frame) scaled.recycle()
                        converted
                    } else {
                        scaled
                    }
                }
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("Detector", "OOM creating resized bitmap", e)
                synchronized(detectorLock) { consecutiveOomErrors++ }
                return
            } catch (e: Exception) {
                android.util.Log.e("Detector", "Error creating resized bitmap", e)
                return
            }
            
            if (resizedBitmap?.isRecycled != false) {
                android.util.Log.e("Detector", "Resized bitmap is null or recycled")
                return
            }

            // Create tensor image with enhanced error checking
            tensorImage = try {
                TensorImage(DataType.FLOAT32).apply {
                    load(resizedBitmap)
                }
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("Detector", "OOM creating tensor image", e)
                synchronized(detectorLock) { consecutiveOomErrors++ }
                return
            } catch (e: Exception) {
                android.util.Log.e("Detector", "Error creating tensor image", e)
                return
            }

            val processedImage = try {
                imageProcessor.process(tensorImage)
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("Detector", "OOM processing image", e)
                synchronized(detectorLock) { consecutiveOomErrors++ }
                return
            } catch (e: Exception) {
                android.util.Log.e("Detector", "Error processing image", e)
                return
            }

            val imageBuffer = processedImage.buffer

            // Create output tensor with validation
            output = try {
                TensorBuffer.createFixedSize(intArrayOf(1, numChannel, numElements), OUTPUT_IMAGE_TYPE)
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("Detector", "OOM creating output tensor", e)
                synchronized(detectorLock) { consecutiveOomErrors++ }
                return
            } catch (e: Exception) {
                android.util.Log.e("Detector", "Error creating output tensor", e)
                return
            }

            // Run inference with timeout protection
            try {
                synchronized(currentInterpreter) {
                    if (!isInitialized) {
                        android.util.Log.w("Detector", "Detector was cleared during processing")
                        return
                    }
                    
                    output?.let { outputTensor ->
                        currentInterpreter.run(imageBuffer, outputTensor.buffer)
                    } ?: run {
                        android.util.Log.e("Detector", "Output tensor is null")
                        return
                    }
                }
            } catch (e: IllegalArgumentException) {
                android.util.Log.e("Detector", "Invalid input for inference", e)
                return
            } catch (e: Exception) {
                android.util.Log.e("Detector", "Error running inference", e)
                return
            }

            val bestBoxes = try {
                output?.floatArray?.let { array ->
                    if (array.isNotEmpty()) bestBox(array) else null
                }
            } catch (e: Exception) {
                android.util.Log.e("Detector", "Error processing results", e)
                null
            }
            
            inferenceTime = SystemClock.uptimeMillis() - inferenceTime
            success = true

            if (bestBoxes.isNullOrEmpty()) {
                detectorListener.onEmptyDetect()
            } else {
                detectorListener.onDetect(bestBoxes, inferenceTime)
            }
            
            // Reset consecutive OOM errors on success
            synchronized(detectorLock) { consecutiveOomErrors = 0 }
            
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("Detector", "OutOfMemoryError in detect", e)
            synchronized(detectorLock) {
                consecutiveOomErrors++
                failedInferences++
            }
            
            // Force aggressive garbage collection
            System.gc()
            Runtime.getRuntime().runFinalization()
            
            detectorListener.onEmptyDetect()
        } catch (e: Exception) {
            android.util.Log.e("Detector", "Unexpected error in detect", e)
            synchronized(detectorLock) { failedInferences++ }
            detectorListener.onEmptyDetect()
        } finally {
            // Always reset processing flag
            synchronized(detectorLock) { isProcessing = false }
            
            // Clean up all resources with enhanced error handling
            try {
                resizedBitmap?.let { bitmap ->
                    if (bitmap != frame && !bitmap.isRecycled) {
                        try {
                            bitmap.recycle()
                        } catch (e: Exception) {
                            android.util.Log.w("Detector", "Error recycling bitmap", e)
                        }
                    }
                }
                
                // Clear references to help GC
                tensorImage = null
                output = null
                
            } catch (e: Exception) {
                android.util.Log.w("Detector", "Error in finally block", e)
            }
        }
    }

    private fun bestBox(array: FloatArray) : List<BoundingBox>? {
        android.util.Log.d("Detector", "Processing ${array.size} output values")
        
        val boundingBoxes = mutableListOf<BoundingBox>()
        var maxFoundConf = 0f
        var detectionCandidates = 0

        for (c in 0 until numElements) {
            var maxConf = -1.0f
            var maxIdx = -1
            var j = 4
            var arrayIdx = c + numElements * j
            while (j < numChannel){
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx]
                    maxIdx = j - 4
                }
                j++
                arrayIdx += numElements
            }
            
            // Track highest confidence found for debugging
            if (maxConf > maxFoundConf) {
                maxFoundConf = maxConf
            }
            
            // Use a lower threshold for debugging
            if (maxConf > DEBUG_CONFIDENCE_THRESHOLD) {
                detectionCandidates++
                
                if (maxConf > CONFIDENCE_THRESHOLD) {
                    val clsName = if (maxIdx < labels.size) labels[maxIdx] else "unknown"
                    val cx = array[c] // 0
                    val cy = array[c + numElements] // 1
                    val w = array[c + numElements * 2]
                    val h = array[c + numElements * 3]
                    val x1 = cx - (w/2F)
                    val y1 = cy - (h/2F)
                    val x2 = cx + (w/2F)
                    val y2 = cy + (h/2F)
                    
                    // Log the detection for debugging
                    android.util.Log.d("Detector", "Detection candidate: $clsName conf=$maxConf bbox=($x1,$y1,$x2,$y2)")
                    
                    if (x1 >= 0F && x1 <= 1F && y1 >= 0F && y1 <= 1F &&
                        x2 >= 0F && x2 <= 1F && y2 >= 0F && y2 <= 1F) {
                        
                        boundingBoxes.add(
                            BoundingBox(
                                x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                                cx = cx, cy = cy, w = w, h = h,
                                cnf = maxConf, cls = maxIdx, clsName = clsName
                            )
                        )
                    } else {
                        android.util.Log.d("Detector", "Filtered out detection due to invalid coordinates")
                    }
                }
            }
        }

        android.util.Log.d("Detector", "Max confidence found: $maxFoundConf, candidates: $detectionCandidates, valid detections: ${boundingBoxes.size}")

        if (boundingBoxes.isEmpty()) {
            android.util.Log.d("Detector", "No detections above threshold $CONFIDENCE_THRESHOLD")
            return null
        }

        return applyNMS(boundingBoxes)
    }

    private fun applyNMS(boxes: List<BoundingBox>) : MutableList<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while(sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.first()
            selectedBoxes.add(first)
            sortedBoxes.remove(first)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val nextBox = iterator.next()
                val iou = calculateIoU(first, nextBox)
                if (iou >= IOU_THRESHOLD) {
                    iterator.remove()
                }
            }
        }

        return selectedBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)
        val intersectionArea = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val box1Area = box1.w * box1.h
        val box2Area = box2.w * box2.h
        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }

    companion object {
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.25F  // Lowered for better detection
        private const val DEBUG_CONFIDENCE_THRESHOLD = 0.1F  // For debugging purposes
        private const val IOU_THRESHOLD = 0.4F
        
        // Error recovery constants
        private const val MAX_CONSECUTIVE_OOM_ERRORS = 3
    }
}