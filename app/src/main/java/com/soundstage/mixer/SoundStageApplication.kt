package com.soundstage.mixer

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SoundStageApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupGlobalExceptionHandler()
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("SoundStageApp", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)

            // 1. Log error to local storage file
            try {
                val logsDir = File(filesDir, "SoundStage/Logs")
                if (!logsDir.exists()) logsDir.mkdirs()
                val logFile = File(logsDir, "crash_logs.txt")
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val entry = "[$timestamp] [CRASH] Thread: ${thread.name} | ${throwable.message}\n" +
                        Log.getStackTraceString(throwable) + "\n\n"
                logFile.appendText(entry)
            } catch (e: Exception) {
                Log.e("SoundStageApp", "Failed to write crash log: ${e.message}")
            }

            // 2. Prevent infinite crash loops on startup by resetting corrupted preferences
            try {
                val prefs = getSharedPreferences("livekeys_mixer_prefs", Context.MODE_PRIVATE)
                val lastCrashTime = prefs.getLong("last_crash_timestamp", 0L)
                val currentTime = System.currentTimeMillis()

                // If crash happened within 10 seconds of previous crash/startup, reset saved state
                if (currentTime - lastCrashTime < 10000L) {
                    Log.w("SoundStageApp", "Repeated startup crash detected. Clearing saved app state...")
                    prefs.edit().clear().apply()
                } else {
                    prefs.edit().putLong("last_crash_timestamp", currentTime).apply()
                }
            } catch (e: Exception) {
                Log.e("SoundStageApp", "Failed to reset corrupt preferences: ${e.message}")
            }

            // 3. Delegate to default handler or terminate process cleanly
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
