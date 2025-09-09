package com.chocoplot.apprecognicemedications.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    fun getConfidenceThreshold(): Float {
        return sharedPreferences.getFloat(KEY_CONFIDENCE_THRESHOLD, DEFAULT_CONFIDENCE_THRESHOLD)
    }
    
    fun setConfidenceThreshold(value: Float) {
        sharedPreferences.edit()
            .putFloat(KEY_CONFIDENCE_THRESHOLD, value)
            .apply()
    }
    
    fun getIouThreshold(): Float {
        return sharedPreferences.getFloat(KEY_IOU_THRESHOLD, DEFAULT_IOU_THRESHOLD)
    }
    
    fun setIouThreshold(value: Float) {
        sharedPreferences.edit()
            .putFloat(KEY_IOU_THRESHOLD, value)
            .apply()
    }
    
    fun saveSettings(confidenceThreshold: Float, iouThreshold: Float) {
        sharedPreferences.edit()
            .putFloat(KEY_CONFIDENCE_THRESHOLD, confidenceThreshold)
            .putFloat(KEY_IOU_THRESHOLD, iouThreshold)
            .apply()
    }
    
    companion object {
        private const val PREFS_NAME = "detector_settings"
        private const val KEY_CONFIDENCE_THRESHOLD = "confidence_threshold"
        private const val KEY_IOU_THRESHOLD = "iou_threshold"
        
        // Default values from Detector.kt
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.3F
        const val DEFAULT_IOU_THRESHOLD = 0.4F
    }
}