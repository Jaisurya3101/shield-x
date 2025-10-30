package com.example.shieldx.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * DeepGuard v3.2 - Preferences Manager
 * ------------------------------------
 * Centralized preferences storage for ShieldX app.
 * Handles configuration, detection settings, analytics, and session data.
 */
class PreferencesManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "shieldx_prefs"

        // Preference keys
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_API_ENDPOINT = "api_endpoint"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_DETECTION_SENSITIVITY = "detection_sensitivity"
        private const val KEY_LAST_BACKEND_TEST = "last_backend_test"
        private const val KEY_TOTAL_NOTIFICATIONS_PROCESSED = "total_notifications_processed"
        private const val KEY_TOTAL_HARASSMENT_DETECTED = "total_harassment_detected"
        private const val KEY_AUTO_START_MONITORING = "auto_start_monitoring"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"

        // Singleton instance
        @Volatile
        private var INSTANCE: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // App Launch State
    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()

    // Notification Settings
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply()

    var autoStartMonitoring: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START_MONITORING, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START_MONITORING, value).apply()

    // API & Authentication
    var apiEndpoint: String?
        get() = prefs.getString(KEY_API_ENDPOINT, "http://10.0.2.2:8001/")
        set(value) = prefs.edit().putString(KEY_API_ENDPOINT, value).apply()

    var authToken: String?
        get() = prefs.getString(KEY_AUTH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()

    // Detection Settings
    var detectionSensitivity: Float
        get() = prefs.getFloat(KEY_DETECTION_SENSITIVITY, 0.7f)
        set(value) = prefs.edit().putFloat(KEY_DETECTION_SENSITIVITY, value.coerceIn(0.1f, 1.0f)).apply()

    fun getDetectionThreshold(): Double = detectionSensitivity.toDouble()

    // Backend test info
    var lastBackendTest: Long
        get() = prefs.getLong(KEY_LAST_BACKEND_TEST, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKEND_TEST, value).apply()

    // Analytics counters
    var totalNotificationsProcessed: Int
        get() = prefs.getInt(KEY_TOTAL_NOTIFICATIONS_PROCESSED, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_NOTIFICATIONS_PROCESSED, value).apply()

    var totalHarassmentDetected: Int
        get() = prefs.getInt(KEY_TOTAL_HARASSMENT_DETECTED, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_HARASSMENT_DETECTED, value).apply()

    // Synchronization info
    var lastSyncTime: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TIME, value).apply()

    // ------------------------
    // Helper Methods
    // ------------------------

    fun incrementNotificationsProcessed() {
        totalNotificationsProcessed++
        Log.d("PreferencesManager", "Total notifications processed: $totalNotificationsProcessed")
    }

    fun incrementHarassmentDetected() {
        totalHarassmentDetected++
        Log.w("PreferencesManager", "Total harassment cases: $totalHarassmentDetected")
    }

    fun resetStats() {
        totalNotificationsProcessed = 0
        totalHarassmentDetected = 0
        Log.i("PreferencesManager", "All analytics counters reset")
    }

    fun clearAllData() {
        prefs.edit().clear().apply()
        Log.w("PreferencesManager", "All preferences cleared!")
    }

    fun printSummary() {
        Log.i("PreferencesManager", """
            --- ShieldX Preferences Summary ---
            First Launch: $isFirstLaunch
            Notifications: $notificationsEnabled
            Auto Start: $autoStartMonitoring
            API Endpoint: $apiEndpoint
            Detection Sensitivity: $detectionSensitivity
            Total Notifications: $totalNotificationsProcessed
            Harassment Detected: $totalHarassmentDetected
            Last Backend Test: $lastBackendTest
        """.trimIndent())
    }
}
