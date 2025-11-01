package com.example.shieldx.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.shieldx.services.ShieldXNotificationListenerService
import com.example.shieldx.utils.SharedPref
import com.example.shieldx.utils.NotificationUtils

/**
 * 🛡️ DeepGuard Boot Receiver v3.0
 * Automatically restarts the monitoring service (NotificationListener)
 * after device boot or app update — respecting user preferences & permissions.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DeepGuardBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "BootReceiver triggered with action: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                // Delay execution slightly to let the system stabilize
                Handler(Looper.getMainLooper()).postDelayed({
                    handleBootOrUpdate(context)
                }, 4000)
            }

            else -> Log.w(TAG, "BootReceiver received unsupported action: $action")
        }
    }

    /**
     * Handles post-boot or app-update logic safely.
     */
    private fun handleBootOrUpdate(context: Context) {
        val sharedPref = SharedPref.getInstance(context)
        val wasMonitoringActive = sharedPref.isMonitoringActive()
        val autoStartEnabled = sharedPref.getBooleanValue("auto_start_monitoring", false)

        Log.i(
            TAG, "Boot/Update detected. MonitoringActive=$wasMonitoringActive, AutoStart=$autoStartEnabled"
        )

        // ✅ Only restart if user explicitly enabled it
        if (!wasMonitoringActive && !autoStartEnabled) {
            Log.i(TAG, "Monitoring was not active, skipping auto-start.")
            return
        }

        // ✅ Check Notification Listener permission
        if (!ShieldXNotificationListenerService.isNotificationServiceEnabled(context)) {
            Log.w(TAG, "Notification listener permission missing — cannot auto-start service.")
            sharedPref.setMonitoringActive(false)
            NotificationUtils.showPermissionWarning(context)
            return
        }

        // ✅ Respect battery optimizations (on Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.i(TAG, "Checking for battery optimization restrictions.")
            NotificationUtils.checkBatteryOptimization(context)
        }

        try {
            val intent = Intent(context, ShieldXNotificationListenerService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.i(TAG, "Starting NotificationListenerService as foreground service.")
                context.startForegroundService(intent)
            } else {
                Log.i(TAG, "Starting NotificationListenerService as background service.")
                context.startService(intent)
            }

            sharedPref.setMonitoringActive(true)
            NotificationUtils.showAutoStartNotification(context)
            Log.i(TAG, "✅ DeepGuard monitoring auto-started successfully after boot/update.")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start NotificationListenerService after boot/update", e)
        }
    }
}
