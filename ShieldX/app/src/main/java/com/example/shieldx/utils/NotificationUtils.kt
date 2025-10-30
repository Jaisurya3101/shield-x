package com.example.shieldx.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.shieldx.R
import com.example.shieldx.activities.DashboardActivity

object NotificationUtils {
    private const val CHANNEL_ID = "deepguard_channel"
    private const val CHANNEL_NAME = "DeepGuard Notifications"
    private const val CHANNEL_DESCRIPTION = "Important alerts and updates from DeepGuard"
    private const val PERMISSION_WARNING_ID = 1001
    private const val AUTO_START_ID = 1002
    
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = System.currentTimeMillis().toInt()
    ) {
        val intent = Intent(context, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
    
    fun showMonitoringNotification(context: Context) {
        showNotification(
            context,
            "DeepGuard Active",
            "Real-time protection is enabled and monitoring for threats"
        )
    }
    
    fun showThreatDetectedNotification(context: Context, threatType: String, confidence: Int) {
        showNotification(
            context,
            "⚠️ Threat Detected",
            "A potential $threatType threat was detected (${confidence}% confidence)"
        )
    }
    
    fun showServiceErrorNotification(context: Context, error: String) {
        showNotification(
            context,
            "Service Error",
            "DeepGuard service error: $error"
        )
    }

    fun showPermissionWarning(context: Context) {
        showNotification(
            context,
            "⚠️ Permission Required",
            "DeepGuard needs notification access permission to protect your device",
            PERMISSION_WARNING_ID
        )
    }

    fun showAutoStartNotification(context: Context) {
        showNotification(
            context,
            "🛡️ DeepGuard Active",
            "Monitoring service has been automatically started",
            AUTO_START_ID
        )
    }

    fun checkBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val packageName = context.packageName
            
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                showNotification(
                    context,
                    "⚡ Battery Optimization",
                    "DeepGuard may be affected by battery optimization. Tap to review settings.",
                    1003
                )
            }
        }
    }
}