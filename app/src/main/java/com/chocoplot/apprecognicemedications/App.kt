package com.chocoplot.apprecognicemedications

import android.app.Application
import com.chocoplot.apprecognicemedications.core.CrashRecoveryManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize crash recovery system
        CrashRecoveryManager.initialize(this)
        
        // Set up global exception handler for additional safety
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("App", "Uncaught exception in thread ${thread.name}", throwable)
            CrashRecoveryManager.recordCrash(
                CrashRecoveryManager.CrashType.GENERAL,
                "Uncaught exception: ${throwable.message}"
            )
        }
    }
}
