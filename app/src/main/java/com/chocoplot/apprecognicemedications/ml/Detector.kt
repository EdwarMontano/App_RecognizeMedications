// app/src/main/java/com/chocoplot/apprecognicemedications/ml/Detector.kt
package com.chocoplot.apprecognicemedications.ml

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.chocoplot.apprecognicemedications.ml.model.BoundingBox
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

    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    fun setup() {
        val model = FileUtil.loadMappedFile(context, modelPath)
        val options = Interpreter.Options()
        options.numThreads = 4
        interpreter = Interpreter(model, options)

        val inputShape = interpreter?.getInputTensor(0)?.shape() ?: return
        val outputShape = interpreter?.getOutputTensor(0)?.shape() ?: return

        tensorWidth = inputShape[1]
        tensorHeight = inputShape[2]
        numChannel = outputShape[1]
        numElements = outputShape[2]

        android.util.Log.d("Detector", "Model setup - Input: ${inputShape.contentToString()}, Output: ${outputShape.contentToString()}")
        android.util.Log.d("Detector", "Tensor dimensions: ${tensorWidth}x${tensorHeight}, channels: $numChannel, elements: $numElements")

        try {
            context.assets.open(labelPath).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null && line != "") {
                        labels.add(line)
                        line = reader.readLine()
                    }
                }
            }
            android.util.Log.d("Detector", "Loaded ${labels.size} labels: ${labels.take(5)}")
        } catch (e: IOException) {
            android.util.Log.e("Detector", "Error loading labels", e)
            e.printStackTrace()
        }
    }

    fun clear() {
        interpreter?.close()
        interpreter = null
    }

    fun detect(frame: Bitmap) {
        var resizedBitmap: Bitmap? = null
        try {
            val currentInterpreter = interpreter ?: return
            if (tensorWidth == 0) return
            if (tensorHeight == 0) return
            if (numChannel == 0) return
            if (numElements == 0) return
            if (frame.isRecycled) return

            var inferenceTime = SystemClock.uptimeMillis()

            resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false)
            
            if (resizedBitmap.isRecycled) return

            val tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(resizedBitmap)
            val processedImage = imageProcessor.process(tensorImage)
            val imageBuffer = processedImage.buffer

            val output = TensorBuffer.createFixedSize(intArrayOf(1 , numChannel, numElements), OUTPUT_IMAGE_TYPE)
            currentInterpreter.run(imageBuffer, output.buffer)

            val bestBoxes = bestBox(output.floatArray)
            inferenceTime = SystemClock.uptimeMillis() - inferenceTime

            if (bestBoxes == null) {
                detectorListener.onEmptyDetect()
                return
            }

            detectorListener.onDetect(bestBoxes, inferenceTime)
            
        } catch (e: Exception) {
            e.printStackTrace()
            detectorListener.onEmptyDetect()
        } finally {
            // Clean up bitmap resource
            resizedBitmap?.let { bitmap ->
                if (bitmap != frame && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
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
    }
}