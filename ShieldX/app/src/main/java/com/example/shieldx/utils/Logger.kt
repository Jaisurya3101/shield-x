package com.example.shieldx.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * DeepGuard v3.2 - Unified Logger
 * --------------------------------
 * ✅ Thread-safe, timestamped, structured logging for all modules
 * ✅ Centralized log format for Debug Console, Analytics, and File Persistence
 * ✅ Supports:
 *    - Standard logs (D/I/W/E)
 *    - API performance tracking
 *    - Harassment detection trace logs
 */
object Logger {

    private const val TAG = "ShieldX"
    private val lock = ReentrantLock()

    // Thread-safe date formatter
    private val dateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    }

    // Toggle for enabling/disabling logs globally
    var isLoggingEnabled: Boolean = true

    // Optional file logging (future use for offline logs)
    private var logToFile: Boolean = false

    /**
     * Enable persistent file logging (optional)
     */
    fun enableFileLogging(enable: Boolean) {
        logToFile = enable
        i("Logger", "File logging ${if (enable) "enabled" else "disabled"}")
    }

    // ==========================
    // 🔹 Standard Logging Methods
    // ==========================

    fun d(tag: String, message: String) {
        if (isLoggingEnabled) log("DEBUG", tag, message)
    }

    fun i(tag: String, message: String) {
        if (isLoggingEnabled) log("INFO", tag, message)
    }

    fun w(tag: String, message: String) {
        if (isLoggingEnabled) log("WARN", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (isLoggingEnabled) log("ERROR", tag, message, throwable)
    }

    private fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.get().format(Date())
        val formatted = "[$timestamp][$level][$TAG:$tag] $message"

        when (level) {
            "DEBUG" -> Log.d("$TAG:$tag", formatted)
            "INFO" -> Log.i("$TAG:$tag", formatted)
            "WARN" -> Log.w("$TAG:$tag", formatted)
            "ERROR" -> Log.e("$TAG:$tag", formatted, throwable)
        }

        if (logToFile) writeToFile(formatted)
    }

    // ==========================
    // 🔹 Specialized Logs
    // ==========================

    /**
     * Log detailed harassment detection analysis
     */
    fun logHarassmentDetection(
        source: String,
        content: String,
        isHarassment: Boolean,
        confidence: Double,
        type: String?
    ) {
        val truncatedContent = content.take(120)
        val confidencePercent = (confidence * 100).toInt()
        val emoji = if (isHarassment) "🚨" else "🟢"

        val logMessage = """
            $emoji HARASSMENT DETECTION
            ───────────────────────────
            📱 Source: $source
            💬 Content: "$truncatedContent${if (content.length > 120) "..." else ""}"
            ⚖️ Harassment: $isHarassment
            📊 Confidence: ${confidencePercent}%
            🧠 Type: ${type ?: "Unknown"}
        """.trimIndent()

        if (isHarassment) w("Detection", logMessage) else d("Detection", logMessage)
    }

    /**
     * Log API call performance data
     */
    fun logApiCall(endpoint: String, success: Boolean, responseCode: Int, duration: Long) {
        val emoji = if (success) "✅" else "❌"
        val message = """
            $emoji API CALL
            ───────────────
            🌐 Endpoint: $endpoint
            🕒 Duration: ${duration}ms
            📦 Response Code: $responseCode
            ✅ Success: $success
        """.trimIndent()

        if (success) i("API", message) else w("API", message)
    }

    /**
     * Quick summary logs for system events
     */
    fun logSystemEvent(event: String, detail: String) {
        i("System", "⚙️ Event: $event | Detail: $detail")
    }

    // ==========================
    // 🔹 File Logging (Optional)
    // ==========================

    private fun writeToFile(message: String) {
        lock.withLock {
            try {
                val logFile = FileManager.getAppLogFile()
                logFile.appendText(message + "\n")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write log to file", e)
            }
        }
    }
}

/**
 * Optional helper for log file management
 */
object FileManager {
    private const val LOG_FILE_NAME = "shieldx_logs.txt"

    fun getAppLogFile(): java.io.File {
        val dir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "ShieldXLogs")
        if (!dir.exists()) dir.mkdirs()
        val file = java.io.File(dir, LOG_FILE_NAME)
        if (!file.exists()) file.createNewFile()
        return file
    }
}
