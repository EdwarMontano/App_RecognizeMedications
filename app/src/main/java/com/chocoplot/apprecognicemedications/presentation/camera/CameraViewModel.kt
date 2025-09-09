// app/src/main/java/com/chocoplot/apprecognicemedications/presentation/camera/CameraViewModel.kt
package com.chocoplot.apprecognicemedications.presentation.camera

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.chocoplot.apprecognicemedications.core.Constants
import com.chocoplot.apprecognicemedications.ml.Detector
import com.chocoplot.apprecognicemedications.ml.model.BoundingBox
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor() : ViewModel() {

    private val _results: MutableLiveData<List<BoundingBox>> = MutableLiveData(emptyList())
    val results: LiveData<List<BoundingBox>> = _results

    private val _inferenceTime: MutableLiveData<Long> = MutableLiveData(0L)
    val inferenceTime: LiveData<Long> = _inferenceTime

    fun createDetector(ctx: Context, listener: Detector.DetectorListener): Detector =
        Detector(ctx, Constants.MODEL_PATH, Constants.LABELS_PATH, listener).apply { setup() }

    fun updateResults(boxes: List<BoundingBox>, timeMs: Long) {
        _results.postValue(boxes)
        _inferenceTime.postValue(timeMs)
    }

    fun captureSingleFrame() {
        // Si quisieras disparar una captura puntual, puedes exponer un Event.
    }
}
