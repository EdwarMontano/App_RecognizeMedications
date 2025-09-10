// app/src/main/java/com/chocoplot/apprecognicemedications/core/CrashRecoveryManager.kt
package com.chocoplot.apprecognicemedications.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages crash recovery and system stability for the camera and ML detection system
 */
object CrashRecoveryManager {
    
    private const val TAG = "CrashRecoveryManager"
    private const val PREFS_NAME = "crash_recovery_prefs"
    
    // Crash tracking
    private const val KEY_CRASH_COUNT = "crash_count"
    private const val KEY_LAST_CRASH_TIME = "last_crash_time"
    private const val KEY_OOM_COUNT = "oom_count"
    private const val KEY_CAMERA_ERROR_COUNT = "camera_error_count"
    
    // Recovery thresholds
    private const val MAX_CRASHES_PER_SESSION = 3
    private const val CRASH_RESET_INTERVAL = 24 * 60 * 60 * 1000L // 24 hours
    private const val MEMORY_PRESSURE_THRESHOLD = 0.8f // 80% memory usage
    
    // Runtime tracking
    private val sessionCrashCount = AtomicInteger(0)
    private val sessionOomCount = AtomicInteger(0)
    private val sessionCameraErrorCount = AtomicInteger(0)
    private val lastMemoryCheck = AtomicLong(0)
    
    private var prefs: SharedPreferences? = null
    private var isRecoveryMode = false
    
    fun initialize(context: Context) {
        try {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // Check if we need to reset crash counters
            val lastCrashTime = prefs?.getLong(KEY_LAST_CRASH_TIME, 0) ?: 0
            val currentTime = System.currentTimeMillis()
            
            if (currentTime - lastCrashTime > CRASH_RESET_INTERVAL) {
                resetCrashCounters()
            }
            
            // Check if we should start in recovery mode
            val totalCrashes = getCrashCount()
            isRecoveryMode = totalCrashes >= MAX_CRASHES_PER_SESSION
            
            if (isRecoveryMode) {
                Log.w(TAG, "Starting in recovery mode due to $totalCrashes previous crashes")
            }
            
            Log.d(TAG, "CrashRecoveryManager initialized. Recovery mode: $isRecoveryMode")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing CrashRecoveryManager", e)
        }
    }
    
    fun recordCrash(crashType: CrashType, details: String = "") {
        try {
            sessionCrashCount.incrementAndGet()
            
            when (crashType) {
                CrashType.OUT_OF_MEMORY -> {
                    sessionOomCount.incrementAndGet()
                    incrementCounter(KEY_OOM_COUNT)
                }
                CrashType.CAMERA_ERROR -> {
                    sessionCameraErrorCount.incrementAndGet()
                    incrementCounter(KEY_CAMERA_ERROR_COUNT)
                }
                CrashType.GENERAL -> {
                    incrementCounter(KEY_CRASH_COUNT)
                }
            }
            
            // Update last crash time
            prefs?.edit()?.putLong(KEY_LAST_CRASH_TIME, System.currentTimeMillis())?.apply()
            
            Log.w(TAG, "Recorded crash: $crashType - $details")
            
            // Check if we should enter recovery mode
            if (sessionCrashCount.get() >= MAX_CRASHES_PER_SESSION) {
                enableRecoveryMode()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error recording crash", e)
        }
    }
    
    fun checkMemoryPressure(): MemoryStatus {
        try {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastMemoryCheck.get() < 1000) {
                // Don't check too frequently
                return MemoryStatus.NORMAL
            }
            lastMemoryCheck.set(currentTime)
            
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryUsageRatio = usedMemory.toFloat() / maxMemory.toFloat()
            
            return when {
                memoryUsageRatio > 0.9f -> {
                    Log.w(TAG, "Critical memory pressure: ${(memoryUsageRatio * 100).toInt()}%")
                    MemoryStatus.CRITICAL
                }
                memoryUsageRatio > MEMORY_PRESSURE_THRESHOLD -> {
                    Log.w(TAG, "High memory pressure: ${(memoryUsageRatio * 100).toInt()}%")
                    MemoryStatus.HIGH
                }
                else -> MemoryStatus.NORMAL
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking memory pressure", e)
            return MemoryStatus.NORMAL
        }
    }
    
    fun isInRecoveryMode(): Boolean = isRecoveryMode
    
    fun getRecoveryRecommendations(): List<RecoveryAction> {
        val actions = mutableListOf<RecoveryAction>()
        
        try {
            val memoryStatus = checkMemoryPressure()
            
            if (memoryStatus == MemoryStatus.CRITICAL) {
                actions.add(RecoveryAction.FORCE_GARBAGE_COLLECTION)
                actions.add(RecoveryAction.REDUCE_CAMERA_RESOLUTION)
                actions.add(RecoveryAction.SKIP_ML_PROCESSING)
            } else if (memoryStatus == MemoryStatus.HIGH) {
                actions.add(RecoveryAction.FORCE_GARBAGE_COLLECTION)
                actions.add(RecoveryAction.REDUCE_FRAME_RATE)
            }
            
            if (sessionOomCount.get() > 2) {
                actions.add(RecoveryAction.REDUCE_CAMERA_RESOLUTION)
                actions.add(RecoveryAction.SKIP_ML_PROCESSING)
            }
            
            if (sessionCameraErrorCount.get() > 1) {
                actions.add(RecoveryAction.RESTART_CAMERA)
            }
            
            if (isRecoveryMode) {
                actions.add(RecoveryAction.SAFE_MODE)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recovery recommendations", e)
        }
        
        return actions
    }
    
    fun performRecoveryAction(action: RecoveryAction): Boolean {
        return try {
            when (action) {
                RecoveryAction.FORCE_GARBAGE_COLLECTION -> {
                    System.gc()
                    Runtime.getRuntime().runFinalization()
                    Log.d(TAG, "Performed garbage collection")
                    true
                }
                RecoveryAction.REDUCE_CAMERA_RESOLUTION -> {
                    Log.d(TAG, "Recommendation: Reduce camera resolution")
                    true
                }
                RecoveryAction.REDUCE_FRAME_RATE -> {
                    Log.d(TAG, "Recommendation: Reduce frame rate")
                    true
                }
                RecoveryAction.SKIP_ML_PROCESSING -> {
                    Log.d(TAG, "Recommendation: Skip ML processing")
                    true
                }
                RecoveryAction.RESTART_CAMERA -> {
                    Log.d(TAG, "Recommendation: Restart camera")
                    true
                }
                RecoveryAction.SAFE_MODE -> {
                    Log.d(TAG, "Recommendation: Enter safe mode")
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing recovery action: $action", e)
            false
        }
    }
    
    fun getSessionStats(): SessionStats {
        return SessionStats(
            crashes = sessionCrashCount.get(),
            oomErrors = sessionOomCount.get(),
            cameraErrors = sessionCameraErrorCount.get(),
            isRecoveryMode = isRecoveryMode
        )
    }
    
    private fun enableRecoveryMode() {
        isRecoveryMode = true
        Log.w(TAG, "Recovery mode enabled due to excessive crashes")
    }
    
    private fun getCrashCount(): Int {
        return prefs?.getInt(KEY_CRASH_COUNT, 0) ?: 0
    }
    
    private fun incrementCounter(key: String) {
        prefs?.let { p ->
            val currentCount = p.getInt(key, 0)
            p.edit().putInt(key, currentCount + 1).apply()
        }
    }
    
    private fun resetCrashCounters() {
        prefs?.edit()?.apply {
            putInt(KEY_CRASH_COUNT, 0)
            putInt(KEY_OOM_COUNT, 0)
            putInt(KEY_CAMERA_ERROR_COUNT, 0)
            putLong(KEY_LAST_CRASH_TIME, 0)
            apply()
        }
        Log.d(TAG, "Reset crash counters")
    }
    
    enum class CrashType {
        OUT_OF_MEMORY,
        CAMERA_ERROR,
        GENERAL
    }
    
    enum class MemoryStatus {
        NORMAL,
        HIGH,
        CRITICAL
    }
    
    enum class RecoveryAction {
        FORCE_GARBAGE_COLLECTION,
        REDUCE_CAMERA_RESOLUTION,
        REDUCE_FRAME_RATE,
        SKIP_ML_PROCESSING,
        RESTART_CAMERA,
        SAFE_MODE
    }
    
    data class SessionStats(
        val crashes: Int,
        val oomErrors: Int,
        val cameraErrors: Int,
        val isRecoveryMode: Boolean
    )
}