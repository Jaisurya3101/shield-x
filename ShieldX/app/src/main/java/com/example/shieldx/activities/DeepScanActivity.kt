package com.example.shieldx.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.shieldx.R
import com.example.shieldx.adapters.AlertAdapter
import com.example.shieldx.databinding.ActivityDeepscanBinding
import com.example.shieldx.models.Alert
import com.example.shieldx.services.NotificationListenerService
import com.example.shieldx.utils.SharedPref
import com.example.shieldx.viewmodel.ScanViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * DeepGuard v3.1 - DeepScanActivity
 * Real-time notification & harassment monitoring using AI backend.
 */
class DeepScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeepscanBinding
    private lateinit var scanViewModel: ScanViewModel
    private lateinit var sharedPref: SharedPref
    private lateinit var alertAdapter: AlertAdapter
    private val alerts = mutableListOf<Alert>()

    private var isMonitoring = false
    private val monitoringHandler = Handler(Looper.getMainLooper())
    private var monitoringStartTime = 0L

    // 🧩 BroadcastReceiver to sync stats when backend detects new threats
    private val statsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.shieldx.STATS_UPDATED") {
                val scanned = intent.getIntExtra("notifications_scanned", 0)
                val blocked = intent.getIntExtra("threats_blocked", 0)
                val warnings = intent.getIntExtra("warnings_sent", 0)

                binding.tvNotificationsScanned.text = scanned.toString()
                binding.tvThreatsBlocked.text = blocked.toString()
                binding.tvWarningsSent.text = warnings.toString()

                // Refresh UI from backend
                scanViewModel.loadRecentAlerts()
                scanViewModel.loadMonitoringStats()
            }
        }
    }

    // 🔁 Runnable for periodic updates
    private val monitoringRunnable = object : Runnable {
        override fun run() {
            updateMonitoringDuration()
            if (isMonitoring) {
                scanViewModel.loadMonitoringStats()
                scanViewModel.loadRecentAlerts()
                monitoringHandler.postDelayed(this, 7000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeepscanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scanViewModel = ViewModelProvider(this)[ScanViewModel::class.java]
        sharedPref = SharedPref.getInstance(this)

        registerReceiver(statsUpdateReceiver, IntentFilter("com.example.shieldx.STATS_UPDATED"), RECEIVER_NOT_EXPORTED)

        setupUI()
        setupRecyclerView()
        setupObservers()
        setupBottomNavigation()
        loadUserPreferences()
    }

    // ------------------------------------------------------
    // 🧠 UI Setup
    // ------------------------------------------------------
    private fun setupUI() {
        binding.ivBack.setOnClickListener { finish() }

        binding.switchNotificationMonitoring.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startMonitoring() else stopMonitoring()
        }

        binding.switchRealTimeMode.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.setRealTimeModeEnabled(isChecked)
            if (isChecked) showRealTimeInfoDialog()
        }

        binding.switchHarassmentDetection.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.setHarassmentDetectionEnabled(isChecked)
        }

        binding.switchDeepfakeDetection.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.setDeepfakeDetectionEnabled(isChecked)
        }

        binding.switchAutoBlock.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.setAutoBlockEnabled(isChecked)
        }

        binding.btnStartMonitoring.setOnClickListener { startMonitoring() }
        binding.btnStopMonitoring.setOnClickListener { stopMonitoring() }
    }

    // ------------------------------------------------------
    // 🧾 RecyclerView Setup
    // ------------------------------------------------------
    private fun setupRecyclerView() {
        alertAdapter = AlertAdapter(alerts) { alert -> showAlertDetails(alert) }
        binding.rvRecentAlerts.apply {
            layoutManager = LinearLayoutManager(this@DeepScanActivity)
            adapter = alertAdapter
        }
    }

    // ------------------------------------------------------
    // 📡 ViewModel Observers
    // ------------------------------------------------------
    private fun setupObservers() {
        scanViewModel.monitoringStats.observe(this) { stats ->
            stats?.let {
                binding.tvNotificationsScanned.text = it.notificationsScanned.toString()
                binding.tvThreatsBlocked.text = it.threatsBlocked.toString()
                binding.tvWarningsSent.text = it.warningsSent.toString()
            }
        }

        scanViewModel.recentAlerts.observe(this) { alertList ->
            alertList?.let {
                alerts.clear()
                alerts.addAll(it)
                alertAdapter.notifyDataSetChanged()

                binding.tvNoAlerts.visibility = if (alerts.isEmpty()) View.VISIBLE else View.GONE
                binding.rvRecentAlerts.visibility = if (alerts.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        scanViewModel.errorMessage.observe(this) { errorMessage ->
            errorMessage?.let {
                showErrorDialog("Error", it)
            }
        }
    }

    private fun showErrorDialog(title: String, message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // ------------------------------------------------------
    // 🔽 Bottom Navigation
    // ------------------------------------------------------
    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_deepscan
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_deepscan -> true
                R.id.nav_deepfake -> {
                    startActivity(Intent(this, DeepfakeActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_analytics -> {
                    startActivity(Intent(this, AnalyticsActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    // ------------------------------------------------------
    // ⚙️ Load Saved Preferences
    // ------------------------------------------------------
    private fun loadUserPreferences() {
        binding.switchRealTimeMode.isChecked = sharedPref.isRealTimeModeEnabled()
        binding.switchHarassmentDetection.isChecked = sharedPref.isHarassmentDetectionEnabled()
        binding.switchDeepfakeDetection.isChecked = sharedPref.isDeepfakeDetectionEnabled()
        binding.switchAutoBlock.isChecked = sharedPref.isAutoBlockEnabled()

        isMonitoring = sharedPref.isMonitoringActive()
        binding.switchNotificationMonitoring.isChecked = isMonitoring

        if (isMonitoring) {
            showMonitoringUI(true)
            monitoringStartTime = sharedPref.getMonitoringStartTime()
            startMonitoringTimer()
        }
    }

    // ------------------------------------------------------
    // 🚀 Monitoring Controls
    // ------------------------------------------------------
    private fun startMonitoring() {
        if (!NotificationListenerService.isNotificationServiceEnabled(this)) {
            showNotificationPermissionDialog()
            return
        }

        isMonitoring = true
        monitoringStartTime = System.currentTimeMillis()
        sharedPref.setMonitoringActive(true)
        sharedPref.setMonitoringStartTime(monitoringStartTime)

        showMonitoringUI(true)
        startMonitoringTimer()

        val intent = Intent(this, NotificationListenerService::class.java)
        startService(intent)

        scanViewModel.loadMonitoringStats()
        scanViewModel.loadRecentAlerts()
    }

    private fun stopMonitoring() {
        isMonitoring = false
        sharedPref.setMonitoringActive(false)
        showMonitoringUI(false)
        stopMonitoringTimer()

        val intent = Intent(this, NotificationListenerService::class.java)
        stopService(intent)
    }

    private fun showMonitoringUI(active: Boolean) {
        if (active) {
            binding.cardMonitoringStatus.visibility = View.VISIBLE
            binding.btnStartMonitoring.visibility = View.GONE
            binding.btnStopMonitoring.visibility = View.VISIBLE
            binding.statusIndicator.backgroundTintList = getColorStateList(R.color.success_color)
        } else {
            binding.cardMonitoringStatus.visibility = View.GONE
            binding.btnStartMonitoring.visibility = View.VISIBLE
            binding.btnStopMonitoring.visibility = View.GONE
            binding.statusIndicator.backgroundTintList = getColorStateList(R.color.gray)
        }
    }

    private fun startMonitoringTimer() {
        monitoringHandler.post(monitoringRunnable)
    }

    private fun stopMonitoringTimer() {
        monitoringHandler.removeCallbacks(monitoringRunnable)
    }

    private fun updateMonitoringDuration() {
        if (!isMonitoring) return
        val duration = System.currentTimeMillis() - monitoringStartTime
        val hours = (duration / (1000 * 60 * 60)) % 24
        val minutes = (duration / (1000 * 60)) % 60
        val seconds = (duration / 1000) % 60
        binding.tvMonitoringDuration.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    // ------------------------------------------------------
    // ⚠️ Alert & Dialog Helpers
    // ------------------------------------------------------
    private fun showNotificationPermissionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Notification Access Required")
            .setMessage(
                "DeepGuard needs notification access to analyze messages for threats.\n" +
                "Grant this permission in the next screen to enable real-time protection."
            )
            .setPositiveButton("Grant Access") { _, _ ->
                NotificationListenerService.openNotificationAccessSettings(this)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAlertDetails(alert: Alert) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Threat Detected")
            .setMessage(
                """
                App: ${alert.appName}
                Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(alert.timestamp))}
                Type: ${alert.threatType}
                Confidence: ${alert.confidence}%
                Content: ${alert.content}
            """.trimIndent()
            )
            .setPositiveButton("OK", null)
            .setNeutralButton("Block Sender") { _, _ ->
                // TODO: Implement sender blocking
            }
            .show()
    }

    private fun showRealTimeInfoDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Real-Time Protection")
            .setMessage(
                """
                Real-time protection continuously monitors messages and content 
                using AI models for harassment and deepfake detection.
                
                Note: May slightly increase battery usage.
                """.trimIndent()
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // ------------------------------------------------------
    // 🔄 Lifecycle
    // ------------------------------------------------------
    override fun onResume() {
        super.onResume()
        if (isMonitoring) {
            scanViewModel.loadMonitoringStats()
            scanViewModel.loadRecentAlerts()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoringTimer()
        try {
            unregisterReceiver(statsUpdateReceiver)
        } catch (_: Exception) {
        }
    }
}
