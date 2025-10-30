package com.example.shieldx.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DividerItemDecoration
import com.example.shieldx.R
import com.example.shieldx.adapters.RecentScansAdapter
import com.example.shieldx.data.RecentScan
import com.example.shieldx.data.DashboardUserStats
import com.example.shieldx.models.UserStats
import com.example.shieldx.databinding.ActivityDashboardBinding
import com.example.shieldx.viewmodel.AuthViewModel
import com.example.shieldx.viewmodel.AnalyticsViewModel
import com.example.shieldx.utils.GraphUtils
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * DeepGuard v3.1 - Dashboard Activity (Optimized)
 * Displays real-time analytics and user stats with auto-refresh
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var authViewModel: AuthViewModel
    private lateinit var analyticsViewModel: AnalyticsViewModel
    private lateinit var recentScansAdapter: RecentScansAdapter

    private val handler = Handler(Looper.getMainLooper())
    private var refreshScheduled = false
    private var lastUpdateTime = 0L
    private val REFRESH_INTERVAL_MS = 10_000L // every 10 seconds

    // Receiver for live updates from background scans
    private val statsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.shieldx.STATS_UPDATED") {
                refreshDashboardDebounced()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        analyticsViewModel = ViewModelProvider(this)[AnalyticsViewModel::class.java]

        setupRecentScansRecyclerView()
        setupBottomNavigation()
        setupObservers()
        setupClickListeners()

        loadDashboardData()
    }

    private fun setupRecentScansRecyclerView() {
        recentScansAdapter = RecentScansAdapter { scanId ->
            // TODO: Show scan details
            Toast.makeText(this, "Scan details coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.rvRecentScans.apply {
            adapter = recentScansAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
            addItemDecoration(androidx.recyclerview.widget.DividerItemDecoration(
                context,
                androidx.recyclerview.widget.LinearLayoutManager.VERTICAL
            ))
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    refreshDashboardDebounced()
                    true
                }
                R.id.nav_deepscan -> {
                    startActivity(Intent(this, DeepScanActivity::class.java))
                    true
                }
                R.id.nav_deepfake -> {
                    startActivity(Intent(this, DeepfakeActivity::class.java))
                    true
                }
                R.id.nav_analytics -> {
                    startActivity(Intent(this, AnalyticsActivity::class.java))
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupClickListeners() {
        // Notifications click listener
        binding.ivNotifications.setOnClickListener {
            // TODO: Show notifications screen
            Toast.makeText(this, "Notifications coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupObservers() {
        // Observe current user
        authViewModel.currentUser.observe(this) { user ->
            user?.let {
                binding.tvWelcome.text = "Welcome, ${it.fullName ?: it.username}"
            }
        }

        // Observe dashboard stats from backend
        analyticsViewModel.dashboardState.observe(this) { state ->
            when {
                state.isLoading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                state.error != null -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, state.error, Toast.LENGTH_LONG).show()
                }
                state.userStats != null -> {
                    binding.progressBar.visibility = View.GONE
                    updateDashboardUI(state.userStats)
                }
            }
        }

        analyticsViewModel.errorMessage.observe(this) { msg ->
            if (msg.isNotEmpty()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateDashboardUI(userStats: UserStats) {
        try {
            // Convert API model to UI model
            val dashboardStats = DashboardUserStats(
                totalScans = userStats.totalScans,
                threatsDetected = userStats.harassmentDetected + userStats.deepfakesDetected,
                isProtectionActive = userStats.safetyScore >= 70.0,
                recentScans = createRecentScansList(userStats)
            )

            // Update stats counters
            binding.tvTotalScans.text = dashboardStats.totalScans.toString()
            binding.tvThreatsDetected.text = dashboardStats.threatsDetected.toString()

            // Update protection status
            binding.tvProtectionStatus.text = if (dashboardStats.isProtectionActive) "Active" else "Inactive"
            binding.tvProtectionStatus.setBackgroundResource(
                if (dashboardStats.isProtectionActive)
                    R.drawable.status_background_active
                else
                    R.drawable.status_background_inactive
            )

            // Update recent scans list
            if (dashboardStats.recentScans.isEmpty()) {
                binding.rvRecentScans.visibility = View.GONE
                binding.tvNoRecentActivity.visibility = View.VISIBLE
            } else {
                binding.rvRecentScans.visibility = View.VISIBLE
                binding.tvNoRecentActivity.visibility = View.GONE
                recentScansAdapter.submitList(dashboardStats.recentScans)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error updating dashboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createRecentScansList(userStats: UserStats): List<RecentScan> {
        // Create a dummy recent scan list since the API model doesn't have this info
        // TODO: Replace with actual recent scans from the API
        return listOf(
            RecentScan(
                id = "1",
                name = "Recent Scan",
                type = "DEEPFAKE",
                timestamp = userStats.lastScan ?: "Just now",
                result = if (userStats.deepfakesDetected > 0) "Threat Detected" else "Clean"
            )
        )
    }

    private fun loadDashboardData() {
        if (authViewModel.currentUser.value == null) {
            authViewModel.refreshUserData()
        }
        analyticsViewModel.loadDashboardData()
    }

    private fun refreshDashboardDebounced() {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < 2000L) return // prevent overlapping calls
        lastUpdateTime = now
        analyticsViewModel.refreshData()
    }



    override fun onResume() {
        super.onResume()
        // Safe broadcast receiver registration
        try {
            registerReceiver(statsUpdateReceiver, IntentFilter("com.example.shieldx.STATS_UPDATED"))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Start periodic refresh
        if (!refreshScheduled) {
            handler.post(refreshRunnable)
            refreshScheduled = true
        }

        refreshDashboardDebounced()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
        refreshScheduled = false

        // Safe unregister
        try {
            unregisterReceiver(statsUpdateReceiver)
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshRunnable)
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshDashboardDebounced()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onBackPressed() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Exit DeepGuard")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Exit") { _, _ -> finishAffinity() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
