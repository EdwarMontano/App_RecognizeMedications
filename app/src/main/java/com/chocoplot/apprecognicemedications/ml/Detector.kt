// app/src/main/java/com/chocoplot/apprecognicemedications/ml/Detector.kt
package com.chocoplot.apprecognicemedications.ml

import android.content.Context
import android.graphics.Bitmap
import com.chocoplot.apprecognicemedications.ml.model.BoundingBox

interface DetectorListener {
    fun onEmptyDetect()
    fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
}

class Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelsPath: String,
    private val listener: DetectorListener
) {
    fun setup() { /* carga tflite, labels */ }
    fun clear() { /* libera recursos */ }

    fun detect(bitmap: Bitmap) {
        // Ejecuta inferencia (placeholder)
        val start = System.currentTimeMillis()
        val results: List<BoundingBox> = emptyList()
        val elapsed = System.currentTimeMillis() - start
        if (results.isEmpty()) listener.onEmptyDetect() else listener.onDetect(results, elapsed)
    }
}
