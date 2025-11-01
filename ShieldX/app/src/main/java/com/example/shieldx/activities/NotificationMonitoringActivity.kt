package com.example.shieldx.activities

import android.content.*
import android.os.*
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.shieldx.R
import com.example.shieldx.databinding.ActivityNotificationMonitoringBinding
import com.example.shieldx.services.NotificationListenerService
import com.example.shieldx.utils.SharedPref
import kotlinx.coroutines.launch

/**
 * DeepGuard v3.1 - NotificationMonitoringActivity
 * Handles real-time protection configuration and live monitoring updates.
 */
class NotificationMonitoringActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationMonitoringBinding
    private lateinit var sharedPref: SharedPref
    private val updateHandler = Handler(Looper.getMainLooper())
    private var isActivityActive = false

    // Listen for backend/broadcast stats updates
    private val statsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.shieldx.STATS_UPDATED") {
                updateMonitoringStatus()
                updateLiveStats()
            }
        }
    }

    // Periodic UI refresh runnable
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isActivityActive && sharedPref.isMonitoringActive()) {
                updateMonitoringStatus()
                updateLiveStats()
                updateHandler.postDelayed(this, 2000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationMonitoringBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPref = SharedPref.getInstance(this)

        // Register broadcast receiver
        val filter = IntentFilter("com.example.shieldx.STATS_UPDATED")
        registerReceiver(statsUpdateReceiver, filter, RECEIVER_NOT_EXPORTED)

        setupUI()
        loadSettings()
        checkNotificationPermission()
    }

    // ----------------------------------------------------------
    // 🧩 UI SETUP
    // ----------------------------------------------------------
    private fun setupUI() {
        binding.ivBack.setOnClickListener { finish() }

        binding.switchMonitoring.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (NotificationListenerService.isNotificationServiceEnabled(this)) {
                    startMonitoring()
                } else {
                    binding.switchMonitoring.isChecked = false
                    showPermissionDialog()
                }
            } else stopMonitoring()
        }

        // Settings Toggles
        binding.switchHarassmentDetection.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.setHarassmentDetectionEnabled(isChecked)
            updateSettingsDescription()
        }

        binding.switchDeepfakeDetection.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.setDeepfakeDetectionEnabled(isChecked)
            updateSettingsDescription()
        }

        binding.switchAutoBlock.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.setAutoBlockEnabled(isChecked)
            if (isChecked) showAutoBlockWarning()
            updateSettingsDescription()
        }

        binding.switchRealTimeAnalysis.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.setRealTimeModeEnabled(isChecked)
            updateSettingsDescription()
        }

        binding.switchAdvancedAnalysis.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.setAdvancedAnalysis(isChecked)
            updateSettingsDescription()
        }

        binding.switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.setAutoStartMonitoring(isChecked)
        }

        // Sensitivity Threshold
        binding.seekBarThreshold.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvThresholdValue.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val threshold = seekBar?.progress ?: 50
                sharedPref.setNotificationThreshold(threshold)
                updateSettingsDescription()
            }
        })

        // Advanced Configurations
        binding.layoutMonitoredApps.setOnClickListener { showMonitoredAppsDialog() }
        binding.layoutTrustedContacts.setOnClickListener { showTrustedContactsDialog() }
        binding.layoutQuietHours.setOnClickListener { showQuietHoursDialog() }
        binding.layoutStatistics.setOnClickListener { showMonitoringStatistics() }

        // Export / Import
        binding.btnExportSettings.setOnClickListener { exportSettings() }
        binding.btnImportSettings.setOnClickListener { importSettings() }
    }

    // ----------------------------------------------------------
    // ⚙️ SETTINGS MANAGEMENT
    // ----------------------------------------------------------
    private fun loadSettings() {
        binding.switchMonitoring.isChecked = sharedPref.isMonitoringActive()
        binding.switchHarassmentDetection.isChecked = sharedPref.isHarassmentDetectionEnabled()
        binding.switchDeepfakeDetection.isChecked = sharedPref.isDeepfakeDetectionEnabled()
        binding.switchAutoBlock.isChecked = sharedPref.isAutoBlockEnabled()
        binding.switchRealTimeAnalysis.isChecked = sharedPref.isRealTimeModeEnabled()
        binding.switchAdvancedAnalysis.isChecked = sharedPref.getAdvancedAnalysis()
        binding.switchAutoStart.isChecked = sharedPref.isAutoStartMonitoringEnabled()

        val threshold = sharedPref.getNotificationThreshold()
        binding.seekBarThreshold.progress = threshold
        binding.tvThresholdValue.text = "$threshold%"

        updateSettingsDescription()
        updateMonitoringStatus()
    }

    private fun checkNotificationPermission() {
        val hasPermission = NotificationListenerService.isNotificationServiceEnabled(this)
        binding.layoutPermissionWarning.visibility =
            if (hasPermission) View.GONE else View.VISIBLE

        if (hasPermission && !sharedPref.isMonitoringActive()) {
            // Auto-start monitoring when permission is granted
            binding.switchMonitoring.isChecked = true
            startMonitoring()
            // Navigate back to previous screen
            finish()
        }

        binding.btnGrantPermission.setOnClickListener {
            NotificationListenerService.openNotificationAccessSettings(this)
        }
    }

    // ----------------------------------------------------------
    // 🧠 MONITORING CONTROL
    // ----------------------------------------------------------
    private fun startMonitoring() {
        lifecycleScope.launch {
            try {
                val intent = Intent(this@NotificationMonitoringActivity, NotificationListenerService::class.java)
                startForegroundService(intent)
                sharedPref.setMonitoringActive(true)
                sharedPref.setMonitoringStartTime(System.currentTimeMillis())

                updateMonitoringStatus()
                Toast.makeText(this@NotificationMonitoringActivity, "🛡️ Monitoring enabled", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.switchMonitoring.isChecked = false
                Toast.makeText(this@NotificationMonitoringActivity, "Failed to start monitoring", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopMonitoring() {
        val intent = Intent(this, NotificationListenerService::class.java)
        stopService(intent)
        sharedPref.setMonitoringActive(false)
        updateMonitoringStatus()
        Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
    }

    // ----------------------------------------------------------
    // 🧾 STATUS & DESCRIPTION UPDATES
    // ----------------------------------------------------------
    private fun updateMonitoringStatus() {
        if (sharedPref.isMonitoringActive()) {
            binding.tvMonitoringStatus.text = "🟢 Active"
            binding.tvMonitoringStatus.setTextColor(getColor(R.color.success_color))

            val start = sharedPref.getMonitoringStartTime()
            if (start > 0) {
                val duration = System.currentTimeMillis() - start
                val hours = duration / (1000 * 60 * 60)
                val minutes = (duration / (1000 * 60)) % 60
                binding.tvMonitoringDuration.text = "Active for ${hours}h ${minutes}m"
            }
        } else {
            binding.tvMonitoringStatus.text = "🔴 Inactive"
            binding.tvMonitoringStatus.setTextColor(getColor(R.color.danger_color))
            binding.tvMonitoringDuration.text = "Not running"
        }
    }

    private fun updateSettingsDescription() {
        val features = mutableListOf<String>()
        if (sharedPref.isHarassmentDetectionEnabled()) features.add("Harassment Detection")
        if (sharedPref.isDeepfakeDetectionEnabled()) features.add("Deepfake Detection")
        if (sharedPref.isAutoBlockEnabled()) features.add("Auto Block")
        if (sharedPref.isRealTimeModeEnabled()) features.add("Real-time Analysis")
        if (sharedPref.getAdvancedAnalysis()) features.add("Advanced Analysis")

        binding.tvActiveFeatures.text =
            if (features.isNotEmpty()) "Active: ${features.joinToString(", ")}"
            else "No protection features enabled"

        val threshold = binding.seekBarThreshold.progress
        binding.tvThresholdDescription.text = when {
            threshold < 30 -> "Very Sensitive (may have false positives)"
            threshold < 50 -> "Sensitive"
            threshold < 70 -> "Balanced (recommended)"
            threshold < 85 -> "Conservative"
            else -> "Very Conservative (may miss some threats)"
        }
    }

    // ----------------------------------------------------------
    // 📊 LIVE STATS + SETTINGS UTILITIES
    // ----------------------------------------------------------
    private fun updateLiveStats() {
        val scanned = sharedPref.getIntValue("notifications_scanned", 0)
        val detected = sharedPref.getIntValue("threats_detected", 0)
        val blocked = sharedPref.getIntValue("threats_blocked", 0)

        val detectionRate = if (scanned > 0)
            String.format("%.1f", (detected.toFloat() / scanned) * 100)
        else "0.0"

        if (sharedPref.isMonitoringActive()) {
            binding.tvMonitoringDuration.text =
                "Scanned: $scanned | Threats: $detected | Blocked: $blocked | Accuracy: $detectionRate%"
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notification Access Required")
            .setMessage("DeepGuard requires notification access to analyze content safely. Grant permission in the next screen.")
            .setPositiveButton("Grant Access") { _, _ ->
                NotificationListenerService.openNotificationAccessSettings(this)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAutoBlockWarning() {
        AlertDialog.Builder(this)
            .setTitle("Auto Block Warning")
            .setMessage("Auto block hides harmful notifications automatically. Occasionally, legitimate messages might be blocked. Review in Statistics.")
            .setPositiveButton("I Understand", null)
            .show()
    }

    private fun showMonitoredAppsDialog() {
        val apps = arrayOf("WhatsApp", "Messenger", "Instagram", "Telegram", "SMS", "Discord", "Signal", "Snapchat")
        val checked = BooleanArray(apps.size) { true }

        AlertDialog.Builder(this)
            .setTitle("Select Monitored Apps")
            .setMultiChoiceItems(apps, checked) { _, i, c -> checked[i] = c }
            .setPositiveButton("Save") { _, _ ->
                val selected = apps.filterIndexed { idx, _ -> checked[idx] }
                sharedPref.setMonitoredApps(selected.toSet())
                Toast.makeText(this, "Updated monitored apps", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showTrustedContactsDialog() {
        val edit = android.widget.EditText(this)
        edit.hint = "Enter contacts separated by commas"
        edit.text = android.text.SpannableStringBuilder(
            sharedPref.getTrustedContacts().joinToString(", ")
        )

        AlertDialog.Builder(this)
            .setTitle("Trusted Contacts")
            .setMessage("Messages from these contacts have reduced threat sensitivity.")
            .setView(edit)
            .setPositiveButton("Save") { _, _ ->
                val contacts = edit.text.toString()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                sharedPref.setTrustedContacts(contacts)
                Toast.makeText(this, "Trusted contacts updated", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showQuietHoursDialog() {
        val options = arrayOf("Disabled", "22:00–07:00", "12:00–14:00", "18:00–20:00")
        AlertDialog.Builder(this)
            .setTitle("Quiet Hours")
            .setSingleChoiceItems(options, 0) { dialog, idx ->
                when (idx) {
                    0 -> sharedPref.setQuietHoursEnabled(false)
                    1 -> { sharedPref.setQuietHoursEnabled(true); sharedPref.setQuietHoursStart(22); sharedPref.setQuietHoursEnd(7) }
                    2 -> { sharedPref.setQuietHoursEnabled(true); sharedPref.setQuietHoursStart(12); sharedPref.setQuietHoursEnd(14) }
                    3 -> { sharedPref.setQuietHoursEnabled(true); sharedPref.setQuietHoursStart(18); sharedPref.setQuietHoursEnd(20) }
                }
                Toast.makeText(this, "Quiet hours updated", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }

    private fun showMonitoringStatistics() {
        val scanned = sharedPref.getIntValue("notifications_scanned", 0)
        val detected = sharedPref.getIntValue("threats_detected", 0)
        val blocked = sharedPref.getIntValue("threats_blocked", 0)
        val warnings = sharedPref.getIntValue("warnings_sent", 0)

        val stats = """
            📊 DeepGuard Statistics

            Notifications Scanned: $scanned
            Threats Detected: $detected
            Threats Blocked: $blocked
            Warnings Sent: $warnings

            Detection Rate: ${if (scanned > 0) String.format("%.2f", (detected.toFloat() / scanned) * 100) else "0.00"}%
            Block Rate: ${if (detected > 0) String.format("%.2f", (blocked.toFloat() / detected) * 100) else "0.00"}%
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Monitoring Statistics")
            .setMessage(stats)
            .setPositiveButton("OK", null)
            .setNeutralButton("Reset") { _, _ -> resetStatistics() }
            .show()
    }

    private fun resetStatistics() {
        sharedPref.saveIntValue("notifications_scanned", 0)
        sharedPref.saveIntValue("threats_detected", 0)
        sharedPref.saveIntValue("threats_blocked", 0)
        sharedPref.saveIntValue("warnings_sent", 0)
        Toast.makeText(this, "Statistics reset", Toast.LENGTH_SHORT).show()
    }

    private fun exportSettings() {
        val settings = """
            Harassment Detection: ${sharedPref.isHarassmentDetectionEnabled()}
            Deepfake Detection: ${sharedPref.isDeepfakeDetectionEnabled()}
            Auto Block: ${sharedPref.isAutoBlockEnabled()}
            Real-time Analysis: ${sharedPref.isRealTimeModeEnabled()}
            Threshold: ${sharedPref.getNotificationThreshold()}%
        """.trimIndent()

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("DeepGuard Settings", settings))
        Toast.makeText(this, "Settings copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun importSettings() {
        Toast.makeText(this, "Import settings coming soon", Toast.LENGTH_SHORT).show()
    }

    // ----------------------------------------------------------
    // 🔄 LIFECYCLE
    // ----------------------------------------------------------
    override fun onResume() {
        super.onResume()
        isActivityActive = true
        checkNotificationPermission()
        updateMonitoringStatus()
        updateLiveStats()
        if (sharedPref.isMonitoringActive()) updateHandler.postDelayed(updateRunnable, 2000)
    }

    override fun onPause() {
        super.onPause()
        isActivityActive = false
        updateHandler.removeCallbacks(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(statsUpdateReceiver) } catch (_: Exception) {}
        updateHandler.removeCallbacks(updateRunnable)
    }
}
