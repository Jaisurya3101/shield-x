package com.example.shieldx.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.shieldx.BuildConfig
import java.util.concurrent.atomic.AtomicLong

/**
 * DeepGuard v3.1 - Toast Manager
 *
 * Prevents toast spam by debouncing and queueing toast messages.
 *
 * Features:
 * - Prevents multiple toasts from showing simultaneously
 * - Debounces identical messages
 * - Queues messages with cooldown period
 * - Supports priority-based queueing
 * - Safe context handling & long message trimming
 * - Debug toast mode for internal testing
 */
object ToastManager {
    private const val TAG = "ToastManager"
    private const val MIN_TOAST_INTERVAL = 1000L // 1 second minimum between toasts
    private const val DUPLICATE_MESSAGE_INTERVAL = 3000L // 3 seconds for duplicate messages
    private const val MAX_MESSAGE_LENGTH = 200 // truncate long toasts

    private var currentToast: Toast? = null
    private val lastToastTime = AtomicLong(0)
    private val lastMessages = mutableMapOf<String, Long>()
    private val handler = Handler(Looper.getMainLooper())
    private val toastQueue = mutableListOf<ToastMessage>()
    private var isProcessing = false

    data class ToastMessage(
        val context: Context,
        val message: String,
        val duration: Int,
        val priority: Int = 0
    )

    // -----------------------------
    // Public Toast Methods
    // -----------------------------

    /** Show a short toast */
    @JvmStatic
    fun showShort(context: Context, message: String) {
        show(context, message, Toast.LENGTH_SHORT)
    }

    /** Show a long toast */
    @JvmStatic
    fun showLong(context: Context, message: String) {
        show(context, message, Toast.LENGTH_LONG)
    }

    /** Show a toast with custom duration and priority */
    @JvmStatic
    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT, priority: Int = 0) {
        if (message.isBlank()) return

        val trimmedMessage = trimMessage(message)
        val now = System.currentTimeMillis()
        val lastShown = lastMessages[trimmedMessage] ?: 0

        // Skip duplicate messages shown too recently
        if (now - lastShown < DUPLICATE_MESSAGE_INTERVAL) return

        synchronized(toastQueue) {
            // Remove duplicate queued messages
            toastQueue.removeAll { it.message == trimmedMessage }

            val toastMessage = ToastMessage(context.applicationContext, trimmedMessage, duration, priority)
            if (priority > 0) {
                toastQueue.add(0, toastMessage) // High-priority → front
            } else {
                toastQueue.add(toastMessage)
            }
        }

        processQueue()
    }

    /** Show a high-priority important toast */
    @JvmStatic
    fun showImportant(context: Context, message: String, duration: Int = Toast.LENGTH_LONG) {
        show(context, message, duration, priority = 10)
    }

    /** Show a toast only in DEBUG builds */
    @JvmStatic
    fun showDebug(context: Context, message: String) {
        if (BuildConfig.DEBUG) show(context, "DEBUG: $message", Toast.LENGTH_SHORT)
    }

    // -----------------------------
    // Internal Queue Logic
    // -----------------------------

    private fun processQueue() {
        if (isProcessing) return

        synchronized(toastQueue) {
            if (toastQueue.isEmpty()) return

            isProcessing = true
            val now = System.currentTimeMillis()
            val timeSinceLastToast = now - lastToastTime.get()

            if (timeSinceLastToast < MIN_TOAST_INTERVAL) {
                handler.postDelayed({
                    isProcessing = false
                    processQueue()
                }, MIN_TOAST_INTERVAL - timeSinceLastToast)
                return
            }

            val toastMessage = toastQueue.removeAt(0)
            currentToast?.cancel() // cancel previous

            handler.post {
                try {
                    currentToast = Toast.makeText(
                        toastMessage.context,
                        toastMessage.message,
                        toastMessage.duration
                    )
                    currentToast?.show()

                    lastToastTime.set(System.currentTimeMillis())
                    lastMessages[toastMessage.message] = System.currentTimeMillis()

                    Log.d(TAG, "Toast shown: ${toastMessage.message}")

                    handler.postDelayed({
                        isProcessing = false
                        processQueue()
                    }, if (toastMessage.duration == Toast.LENGTH_LONG) 3500L else 2000L)

                } catch (e: Exception) {
                    Log.e(TAG, "Error showing toast", e)
                    isProcessing = false
                }
            }
        }
    }

    // -----------------------------
    // Utility Methods
    // -----------------------------

    /** Prevent overly long toast text from breaking layout */
    private fun trimMessage(message: String): String {
        return if (message.length > MAX_MESSAGE_LENGTH) {
            message.take(MAX_MESSAGE_LENGTH - 3) + "..."
        } else message
    }

    /** Clear all pending toasts and reset state */
    @JvmStatic
    fun clear() {
        synchronized(toastQueue) { toastQueue.clear() }
        currentToast?.cancel()
        currentToast = null
        isProcessing = false
        lastMessages.clear()
        lastToastTime.set(0)
    }

    /** Cancel currently showing toast */
    @JvmStatic
    fun cancel() {
        currentToast?.cancel()
        currentToast = null
    }
}
