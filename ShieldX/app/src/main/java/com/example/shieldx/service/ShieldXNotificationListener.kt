package com.example.shieldx.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.shieldx.MainActivity
import com.example.shieldx.R
import com.example.shieldx.api.ShieldXAPI
import com.example.shieldx.data.AnalysisResponse
import com.example.shieldx.data.NotificationPayload
import kotlinx.coroutines.*
import retrofit2.Response

/**
 * 🛡️ DeepGuard v3.1 - ShieldX Notification Listener
 * Monitors incoming notifications, analyzes text for harassment, and triggers AI-based alerts.
 */
class ShieldXNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = ShieldXAPI()

    companion object {
        private const val TAG = "ShieldXListener"
        private const val ALERT_CHANNEL_ID = "harassment_alerts"
        private const val GROUP_KEY_ALERTS = "com.example.shieldx.ALERT_GROUP"
        private const val ALERT_NOTIFICATION_ID = 1001

        private val MONITORED_APPS = setOf(
            "com.whatsapp", "com.facebook.orca", "com.instagram.android", "org.telegram.messenger",
            "com.android.mms", "com.snapchat.android", "com.discord", "com.twitter.android",
            "com.tiktok.android", "com.google.android.apps.messaging"
        )
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🟢 ShieldX Notification Listener started")
        createNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel("Service destroyed")
        Log.w(TAG, "🔴 ShieldX Notification Listener stopped")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName
            if (!MONITORED_APPS.contains(packageName)) return

            val extras = sbn.notification.extras
            val title = extras.getCharSequence("android.title")?.toString() ?: "Unknown"
            val content = extras.getCharSequence("android.bigText")?.toString()
                ?: extras.getCharSequence("android.text")?.toString()

            if (content.isNullOrBlank()) {
                Log.d(TAG, "Ignoring empty notification from $packageName")
                return
            }

            Log.d(TAG, "🔍 Scanning notification from $packageName (sender=$title): ${content.take(50)}...")

            val payload = NotificationPayload(
                content = content,
                source = packageName,
                sender = title,
                timestamp = System.currentTimeMillis()
            )

            scope.launch { analyzeNotificationWithRetry(payload) }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing notification", e)
        }
    }

    /**
     * Analyze notification content using backend AI with retry logic.
     */
    private suspend fun analyzeNotificationWithRetry(payload: NotificationPayload) {
        var attempt = 0
        val maxRetries = 2
        val delayBase = 2000L

        while (attempt <= maxRetries) {
            try {
                val response: Response<AnalysisResponse> = api.analyzeNotification(payload)

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.harassment?.isHarassment == true) {
                        withContext(Dispatchers.Main) { showHarassmentAlert(payload, result) }
                    } else {
                        Log.i(TAG, "✅ Safe notification: ${payload.source}")
                    }
                    return
                } else {
                    Log.w(TAG, "⚠️ API response failed (${response.code()}): ${response.message()}")
                    if (attempt == maxRetries) fallbackLocalDetection(payload)
                }

            } catch (e: Exception) {
                Log.e(TAG, "⚠️ Attempt ${attempt + 1} failed: ${e.message}")
                if (attempt == maxRetries) fallbackLocalDetection(payload)
            }

            attempt++
            delay(delayBase * attempt)
        }
    }

    /**
     * Display AI-based harassment alert notification.
     */
    private fun showHarassmentAlert(payload: NotificationPayload, analysis: AnalysisResponse) {
        val appName = getAppName(payload.source)
        val confidence = ((analysis.harassment?.confidence ?: 0.0) * 100).toInt()

        val title = "🚨 Threat Detected (${confidence}%)"
        val message = """
            ⚠️ Harassment Detected
            • App: $appName
            • Sender: ${payload.sender}
            • Type: ${analysis.harassment?.type ?: "Unknown"}
            • Severity: ${analysis.harassment?.severity ?: "Medium"}
            
            "${payload.content.take(100)}"
        """.trimIndent()

        Log.w(TAG, "🚨 ALERT from $appName: Risk=${confidence}% Type=${analysis.harassment?.type ?: "Unknown"}")

        showAlertNotification(title, message)
    }

    /**
     * Local fallback keyword-based detection when backend unavailable.
     */
    private fun fallbackLocalDetection(payload: NotificationPayload) {
        if (!containsHarassmentKeywords(payload.content)) return

        val appName = getAppName(payload.source)
        val message = "Potential harassment detected in $appName message from ${payload.sender}."
        Log.w(TAG, "⚠️ Local detection triggered for $appName")

        showAlertNotification("⚠️ Potential Harassment", message)
    }

    /**
     * Build and display alert notification.
     */
    private fun showAlertNotification(title: String, message: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_KEY_ALERTS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ALERT_NOTIFICATION_ID + System.currentTimeMillis().toInt(), builder.build())
    }

    /**
     * Create notification channel for alerts.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Harassment & Threat Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts generated when AI detects harassment or deepfake content"
                enableVibration(true)
                enableLights(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Get readable app name from package.
     */
    private fun getAppName(packageName: String): String = when (packageName) {
        "com.whatsapp" -> "WhatsApp"
        "com.facebook.orca" -> "Messenger"
        "com.instagram.android" -> "Instagram"
        "org.telegram.messenger" -> "Telegram"
        "com.snapchat.android" -> "Snapchat"
        "com.discord" -> "Discord"
        "com.twitter.android" -> "Twitter"
        "com.tiktok.android" -> "TikTok"
        "com.google.android.apps.messaging" -> "Messages"
        else -> packageName.substringAfterLast(".")
    }

    /**
     * Lightweight keyword fallback system.
     */
    private fun containsHarassmentKeywords(content: String): Boolean {
        val keywords = listOf(
            "kill yourself", "kys", "die", "hate you", "worthless", "stupid", "ugly",
            "loser", "pathetic", "disgusting", "awful", "horrible", "useless", "idiot",
            "moron", "trash", "freak", "failure"
        )
        val lower = content.lowercase()
        return keywords.any { lower.contains(it) }
    }
}
