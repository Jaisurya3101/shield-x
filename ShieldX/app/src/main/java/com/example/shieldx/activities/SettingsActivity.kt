package com.example.shieldx.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.shieldx.R
import com.example.shieldx.databinding.ActivitySettingsBinding
import com.example.shieldx.utils.SharedPref
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DeepGuard v3.0 – Settings Screen
 * Includes backend sync, auto refresh, and privacy-safe preferences
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPref: SharedPref
    private var autoRefreshActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPref = SharedPref.getInstance(this)

        setupUI()
        loadSettings()
        setupBottomNavigation()
        startAutoRefresh()
    }

    private fun setupUI() {
        binding.ivBack.setOnClickListener { finish() }

        // Account
        binding.layoutProfile.setOnClickListener { showComingSoon("Profile editing") }
        binding.layoutChangePassword.setOnClickListener { showChangePasswordDialog() }

        // Protection
        binding.switchRealTimeProtection.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.setRealTimeModeEnabled(isChecked)
            syncSettingToBackend("real_time_protection", isChecked)
            Toast.makeText(this,
                if (isChecked) "Real-time protection enabled"
                else "Real-time protection disabled",
                Toast.LENGTH_SHORT).show()
        }

        binding.switchAutoBlock.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.setAutoBlockEnabled(isChecked)
            syncSettingToBackend("auto_block", isChecked)
            if (isChecked) showAutoBlockWarning()
        }

        // Notifications
        binding.switchPushNotifications.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.setPushNotificationsEnabled(isChecked)
            syncSettingToBackend("push_notifications", isChecked)
        }

        binding.switchSoundAlerts.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.setSoundAlertsEnabled(isChecked)
            syncSettingToBackend("sound_alerts", isChecked)
        }

        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.setVibrationEnabled(isChecked)
            syncSettingToBackend("vibration_alerts", isChecked)
        }

        // Privacy
        binding.switchAnalytics.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.setAnalyticsEnabled(isChecked)
            syncSettingToBackend("analytics_enabled", isChecked)
            if (isChecked) showAnalyticsInfo()
        }

        // Monitoring
        binding.layoutNotificationMonitoring.setOnClickListener {
            startActivity(Intent(this, NotificationMonitoringActivity::class.java))
        }

        // About + Logout
        binding.layoutAbout.setOnClickListener { showAboutDialog() }
        binding.layoutLogout.setOnClickListener { showLogoutConfirmation() }
    }

    private fun loadSettings() {
        binding.switchRealTimeProtection.isChecked = sharedPref.isRealTimeModeEnabled()
        binding.switchAutoBlock.isChecked = sharedPref.isAutoBlockEnabled()
        binding.switchPushNotifications.isChecked = sharedPref.isPushNotificationsEnabled()
        binding.switchSoundAlerts.isChecked = sharedPref.isSoundAlertsEnabled()
        binding.switchVibration.isChecked = sharedPref.isVibrationEnabled()
        binding.switchAnalytics.isChecked = sharedPref.isAnalyticsEnabled()
    }

    /**
     * Automatically reloads settings every 3 seconds for live sync with backend
     */
    private fun startAutoRefresh() {
        lifecycleScope.launch {
            while (autoRefreshActive) {
                delay(3000)
                try {
                    reloadFromBackend()
                } catch (e: Exception) {
                    // Silently ignore network errors
                }
            }
        }
    }

    /**
     * Reload settings from backend (pseudo endpoint)
     */
    private suspend fun reloadFromBackend() {
        // Example: you can use Retrofit or OkHttp here to hit `/settings/status`
        // Simulated backend sync
        val latest = mapOf(
            "real_time_protection" to sharedPref.isRealTimeModeEnabled(),
            "auto_block" to sharedPref.isAutoBlockEnabled(),
            "analytics_enabled" to sharedPref.isAnalyticsEnabled()
        )

        // Apply updates live (mock simulation)
        runOnUiThread {
            binding.switchRealTimeProtection.isChecked = latest["real_time_protection"] ?: false
            binding.switchAutoBlock.isChecked = latest["auto_block"] ?: false
            binding.switchAnalytics.isChecked = latest["analytics_enabled"] ?: false
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_settings
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish(); true
                }
                R.id.nav_deepscan -> {
                    startActivity(Intent(this, DeepScanActivity::class.java))
                    finish(); true
                }
                R.id.nav_deepfake -> {
                    startActivity(Intent(this, DeepfakeActivity::class.java))
                    finish(); true
                }
                R.id.nav_analytics -> {
                    startActivity(Intent(this, AnalyticsActivity::class.java))
                    finish(); true
                }
                R.id.nav_settings -> true
                else -> false
            }
        }
    }

    private fun syncSettingToBackend(key: String, value: Boolean) {
        lifecycleScope.launch {
            try {
                // Call your backend via Retrofit here, e.g.:
                // ApiClient.service.updateSetting(key, value)
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "⚠️ Failed to sync: $key", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                Toast.makeText(this, "Password change coming soon", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAutoBlockWarning() {
        AlertDialog.Builder(this)
            .setTitle("Auto Block Enabled")
            .setMessage("This may hide legitimate notifications. Review blocked content under Analytics.")
            .setPositiveButton("I Understand", null)
            .show()
    }

    private fun showAnalyticsInfo() {
        AlertDialog.Builder(this)
            .setTitle("Anonymous Analytics")
            .setMessage("We only collect usage stats — not personal or message data.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About DeepGuard")
            .setMessage(
                """
                DeepGuard v3.0
                AI-Based Cyber Harassment & Deepfake Detection

                - Real-time protection
                - Deepfake and harassment scanning
                - Privacy-focused AI design

                Developed with ❤️ for your safety.
                """.trimIndent()
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ -> performLogout() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        sharedPref.logout()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        autoRefreshActive = false
    }

    override fun onResume() {
        super.onResume()
        autoRefreshActive = true
        startAutoRefresh()
    }

    private fun showComingSoon(feature: String) {
        Toast.makeText(this, "$feature coming soon!", Toast.LENGTH_SHORT).show()
    }
}
