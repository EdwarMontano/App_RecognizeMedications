package com.chocoplot.apprecognicemedications.presentation.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.databinding.ObservableField
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    // Float values for internal logic and observation
    private val _confidenceThreshold = MutableLiveData<Float>(0.5f)
    val confidenceThreshold: LiveData<Float> = _confidenceThreshold

    private val _iouThreshold = MutableLiveData<Float>(0.5f)
    val iouThreshold: LiveData<Float> = _iouThreshold
    
    // Boolean value for display elements visibility
    private val _displayElementsVisible = MutableLiveData<Boolean>(true)
    val displayElementsVisible: LiveData<Boolean> = _displayElementsVisible

    // Observable string fields for two-way data binding with EditText
    val confidenceThresholdText = ObservableField<String>("0.5")
    val iouThresholdText = ObservableField<String>("0.5")
    
    // Observable boolean field for two-way data binding with Switch/CheckBox
    val displayElementsVisibleField = ObservableField<Boolean>(true)

    fun setConfidenceThreshold(value: Float) {
        _confidenceThreshold.value = value
        confidenceThresholdText.set(value.toString())
    }

    fun setIouThreshold(value: Float) {
        _iouThreshold.value = value
        iouThresholdText.set(value.toString())
    }
    
    fun setDisplayElementsVisible(value: Boolean) {
        _displayElementsVisible.value = value
        displayElementsVisibleField.set(value)
    }

    fun updateConfidenceFromText(text: String) {
        try {
            val value = text.toFloat()
            if (value >= 0.0f && value <= 1.0f) {
                _confidenceThreshold.value = value
            }
        } catch (e: NumberFormatException) {
            // Invalid input, ignore
        }
    }

    fun updateIouFromText(text: String) {
        try {
            val value = text.toFloat()
            if (value >= 0.0f && value <= 1.0f) {
                _iouThreshold.value = value
            }
        } catch (e: NumberFormatException) {
            // Invalid input, ignore
        }
    }

    fun getConfidenceThresholdValue(): Float = _confidenceThreshold.value ?: 0.5f
    fun getIouThresholdValue(): Float = _iouThreshold.value ?: 0.5f
    fun getDisplayElementsVisibleValue(): Boolean = _displayElementsVisible.value ?: true
}