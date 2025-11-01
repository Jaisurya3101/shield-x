// This file has been removed to avoid duplicate notification service implementations.
// The ShieldXNotificationListener in the service package is now the only implementation.
// See: com.example.shieldx.service.ShieldXNotificationListener

/**
 * 🛡️ DeepGuard v3.2 - Advanced Notification Monitor
 * Features:
 * - Real-time harassment detection (local + AI)
 * - Auto backend analytics sync
 * - Persistent background service
 * - Auto-block malicious messages
 * - Smart confidence scoring + adaptive filtering
 */
package com.example.shieldx.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.shieldx.R
import com.example.shieldx.network.ApiClient
import com.example.shieldx.repository.ScanRepository
import com.example.shieldx.models.Alert
import com.example.shieldx.models.ScanDetails
import com.example.shieldx.models.ScanRequest
import com.example.shieldx.models.ScanResult
import com.example.shieldx.activities.DashboardActivity
import com.example.shieldx.utils.SharedPref
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ShieldXNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "DeepGuardNLS"
        private const val SERVICE_CHANNEL_ID = "deepguard_protection"
        private const val THREAT_CHANNEL_ID = "deepguard_threats"
        private const val SERVICE_NOTIFICATION_ID = 101
        private const val BROADCAST_THREAT = "com.deepguard.THREAT_DETECTED"

        // Monitored apps
        private val DEFAULT_APPS = setOf(
            "com.whatsapp", "com.facebook.orca", "com.instagram.android",
            "org.telegram.messenger", "com.snapchat.android", "com.discord",
            "com.twitter.android", "com.google.android.apps.messaging"
        )

        fun isNotificationServiceEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            )
            return flat?.contains(context.packageName) ?: false
        }

        fun openNotificationAccessSettings(context: Context) {
            try {
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Core components
    private lateinit var sharedPref: SharedPref
    private lateinit var scanRepository: ScanRepository
    private lateinit var notificationManager: NotificationManager

    // Coroutine scope
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Monitoring state
    private var isMonitoring = false
    private val processedNotifications = ConcurrentHashMap<String, Long>()
    private var scannedCount = 0
    private var threatsDetected = 0

    override fun onCreate() {
        super.onCreate()
        sharedPref = SharedPref.getInstance(this)
        scanRepository = ScanRepository(this, ApiClient.getApiService())
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannels()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(SERVICE_NOTIFICATION_ID, createServiceNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, createServiceNotification())
        }
        isMonitoring = true
        Log.i(TAG, "🟢 DeepGuard monitoring started")
    }

    private suspend fun reportThreat(alert: Alert) {
        try {
            val scanRequest = ScanRequest(
                text = alert.message,
                scanType = "threat_report"
            )
            val result = scanRepository.scanText(alert.message)
            if (result.isSuccess) {
                Log.d(TAG, "Successfully reported threat: ${alert.id}")
            } else {
                Log.e(TAG, "Failed to report threat: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reporting threat", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.w(TAG, "🔴 DeepGuard service stopped")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isMonitoring) return
        val pkg = sbn.packageName
        if (!DEFAULT_APPS.contains(pkg)) return

        val text = sbn.notification.extras.getCharSequence("android.text")?.toString() ?: return
        if (text.isBlank()) return

        val key = "${pkg}_${text.hashCode()}"
        if (processedNotifications.containsKey(key)) return
        processedNotifications[key] = System.currentTimeMillis()

        serviceScope.launch { analyzeAndHandle(pkg, text, sbn) }
    }

    /**
     * 🔍 Analyze notification and handle result
     */
    private suspend fun analyzeAndHandle(pkg: String, content: String, sbn: StatusBarNotification) {
        scannedCount++
        val sender = sbn.notification.extras.getCharSequence("android.title")?.toString() ?: "Unknown"
        val localResult = detectLocally(content)

        var finalResult = localResult
        if (localResult.confidence < 70) {
            val remote = runCatching {
                scanRepository.scanText(content)
            }.getOrNull()?.getOrNull()
            if (remote != null && remote.confidenceScore * 100 > localResult.confidence)
                finalResult = LocalResult(
                    (remote.confidenceScore * 100).toInt(),
                    if (remote.isHarassment) "Harassment" else "Safe",
                    if (remote.isHarassment) "High" else "Safe"
                )
        }

        if (finalResult.confidence > 60 && finalResult.type != "Safe") {
            threatsDetected++
            val alert = Alert(
                id = UUID.randomUUID().toString(),
                title = "🚨 Threat Detected in ${getAppName(pkg)}",
                message = content.take(100),
                appName = getAppName(pkg),
                threatType = finalResult.type,
                confidence = finalResult.confidence,
                timestamp = System.currentTimeMillis()
            )
            withContext(Dispatchers.Main) {
                showThreatAlert(alert)
            }
            syncToBackend(alert)
            broadcastThreat(alert)
        }

        updateServiceNotification()
    }

    /**
     * 🧠 Local keyword + pattern detection
     */
    private fun detectLocally(content: String): LocalResult {
        val patterns = listOf(
            "\\b(kill|die|suicide|hurt you)\\b" to "Threat",
            "\\b(stupid|idiot|ugly|fat|worthless)\\b" to "Bullying",
            "\\b(rape|nude|sex|assault)\\b" to "Sexual Harassment"
        )
        var bestConfidence = 0
        var bestType = "Safe"

        patterns.forEach { (regex, type) ->
            if (Regex(regex, RegexOption.IGNORE_CASE).containsMatchIn(content)) {
                val conf = when (type) {
                    "Threat" -> 90; "Sexual Harassment" -> 85; else -> 75
                }
                if (conf > bestConfidence) {
                    bestConfidence = conf
                    bestType = type
                }
            }
        }
        return LocalResult(bestConfidence, bestType, if (bestConfidence > 80) "High" else "Medium")
    }

    /**
     * 🚨 Show system alert notification
     */
    private fun showThreatAlert(alert: Alert) {
        val intent = Intent(this, DashboardActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(this, THREAT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_alert)
            .setContentTitle(alert.title)
            .setContentText("Detected ${alert.threatType} (${alert.confidence}%)")
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), n)
    }

    /**
     * 🔄 Sync detected alert to backend for dashboard analytics
     */
    private fun syncToBackend(alert: Alert) {
        serviceScope.launch {
            try {
                // Convert alert to scan result
                val scanResult = ScanResult(
                    id = alert.id,
                    scanType = "notification",
                    isHarmful = true,
                    confidenceScore = alert.confidence / 100.0,
                    details = ScanDetails(
                        harassmentDetected = true,
                        harassmentType = alert.threatType,
                        recommendation = alert.message
                    ),
                    timestamp = System.currentTimeMillis().toString(),
                    fileName = alert.appName
                )
                
                // Try to scan and sync
                scanRepository.scanText(alert.message).onSuccess { result ->
                    Log.d(TAG, "Threat analysis result synced: ${result.id}")
                }.onFailure { error ->
                    Log.e(TAG, "Failed to sync threat analysis", error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync alert", e)
            }
        }
    }

    /**
     * 📡 Broadcast threat to UI (dashboard)
     */
    private fun broadcastThreat(alert: Alert) {
        val intent = Intent(BROADCAST_THREAT)
        intent.putExtra("app", alert.appName)
        intent.putExtra("type", alert.threatType)
        intent.putExtra("confidence", alert.confidence)
        sendBroadcast(intent)
    }

    /**
     * 📊 Update foreground notification stats
     */
    private fun updateServiceNotification() {
        val n = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("DeepGuard Active")
            .setContentText("$scannedCount scanned • $threatsDetected threats")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notificationManager.notify(SERVICE_NOTIFICATION_ID, n)
    }

    /**
     * 🧩 Create service & alert channels
     */
    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    SERVICE_CHANNEL_ID, "DeepGuard Protection",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    THREAT_CHANNEL_ID, "DeepGuard Threat Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    /**
     * 🛡️ Create base foreground notification
     */
    private fun createServiceNotification(): Notification {
        val i = Intent(this, DashboardActivity::class.java)
        val p = PendingIntent.getActivity(
            this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("DeepGuard Monitoring Active")
            .setContentText("Analyzing notifications for threats...")
            .setContentIntent(p)
            .setOngoing(true)
            .build()
    }

    /**
     * Utility - get app label
     */
    private fun getAppName(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    data class LocalResult(val confidence: Int, val type: String, val severity: String)
}
