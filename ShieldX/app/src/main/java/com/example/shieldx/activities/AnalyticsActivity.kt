package com.example.shieldx.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import com.example.shieldx.activities.DetectionHistoryActivity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.shieldx.R
import com.example.shieldx.adapters.DetectionAdapter
import com.example.shieldx.data.AnalyticsData
import com.example.shieldx.data.ScanActivityData
import com.example.shieldx.data.AccuracyData
import com.example.shieldx.databinding.ActivityAnalyticsBinding
import com.example.shieldx.databinding.IncludeAnalyticsChartsBinding
import com.example.shieldx.models.Detection
import com.example.shieldx.viewmodel.AnalyticsViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.chip.ChipGroup

class AnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalyticsBinding
    private lateinit var analyticsBinding: IncludeAnalyticsChartsBinding
    private lateinit var analyticsViewModel: AnalyticsViewModel
    private lateinit var detectionAdapter: DetectionAdapter
    private val detections = mutableListOf<Detection>()

    private val mainHandler = Handler(Looper.getMainLooper())

    private var selectedTimePeriod = "7days"

    // refresh control
    private val REFRESH_INTERVAL_MS = 15_000L
    private var lastLoadAt = 0L
    private var refreshScheduled = false

    // BroadcastReceiver to listen for real-time threat detection updates
    private val statsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.shieldx.STATS_UPDATED") {
                // Refresh analytics data when threats are detected (debounced)
                loadAnalyticsDataDebounced()
            }
        }
    }

    // Auto-refresh runnable for periodic updates
    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadAnalyticsDataDebounced()
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        analyticsBinding = IncludeAnalyticsChartsBinding.bind(binding.root.findViewById(R.id.layoutAnalyticsCharts))
        setContentView(binding.root)

        analyticsViewModel = ViewModelProvider(this)[AnalyticsViewModel::class.java]

        setupUI()
        setupCharts()
        setupRecyclerView()
        setupObservers()
        setupBottomNavigation()
    }

    private fun setupUI() {
        binding.ivBack.setOnClickListener { finish() }
        
        binding.ivExport.setOnClickListener { exportAnalytics() }
        
        analyticsBinding.tvViewAll.setOnClickListener { viewAllDetections() }
        
        analyticsBinding.btnRefresh.setOnClickListener { refreshCharts() }        // Setup time period chip selection
        analyticsBinding.chipGroupTimePeriod.setOnCheckedStateChangeListener { _: ChipGroup, checkedIds: List<Int> ->
            if (checkedIds.isNotEmpty()) {
                when (checkedIds[0]) {
                    R.id.chip7Days -> selectedTimePeriod = "7days"
                    R.id.chip30Days -> selectedTimePeriod = "30days"
                    R.id.chip3Months -> selectedTimePeriod = "3months"
                }
                loadAnalyticsDataDebounced()
            }
        }
    }

    private fun setupCharts() {
        setupLineChart()
        setupPieChart()
        setupBarChart()
    }

    private fun setupLineChart() {
        analyticsBinding.chartScanActivity.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setBackgroundColor(Color.TRANSPARENT)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = ContextCompat.getColor(this@AnalyticsActivity, R.color.light_gray)
                setDrawGridLines(false)
                granularity = 1f
            }

            axisLeft.apply {
                textColor = ContextCompat.getColor(this@AnalyticsActivity, R.color.light_gray)
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(this@AnalyticsActivity, R.color.dark_gray)
            }

            axisRight.isEnabled = false
            legend.textColor = ContextCompat.getColor(this@AnalyticsActivity, R.color.light_gray)
        }
    }

    private fun setupPieChart() {
        analyticsBinding.chartThreatTypes.apply {
            setUsePercentValues(true)
            description.isEnabled = false
            setExtraOffsets(5f, 10f, 5f, 5f)
            dragDecelerationFrictionCoef = 0.95f
            setDrawHoleEnabled(true)
            setHoleColor(Color.TRANSPARENT)
            holeRadius = 58f
            transparentCircleRadius = 61f
            setDrawCenterText(true)
            centerText = "Threat\nTypes"
            setCenterTextColor(ContextCompat.getColor(this@AnalyticsActivity, R.color.white))
            setCenterTextSize(16f)
            setRotationAngle(0f)
            isRotationEnabled = true
            isHighlightPerTapEnabled = true
            legend.apply {
                textColor = ContextCompat.getColor(this@AnalyticsActivity, R.color.light_gray)
                textSize = 12f
            }
        }
    }

    private fun setupBarChart() {
        analyticsBinding.chartDetectionAccuracy.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setBackgroundColor(Color.TRANSPARENT)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = ContextCompat.getColor(this@AnalyticsActivity, R.color.light_gray)
                setDrawGridLines(false)
                granularity = 1f
            }

            axisLeft.apply {
                textColor = ContextCompat.getColor(this@AnalyticsActivity, R.color.light_gray)
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(this@AnalyticsActivity, R.color.dark_gray)
                axisMinimum = 0f
                axisMaximum = 100f
            }

            axisRight.isEnabled = false
            legend.textColor = ContextCompat.getColor(this@AnalyticsActivity, R.color.light_gray)
        }
    }

    private fun setupRecyclerView() {
        detectionAdapter = DetectionAdapter(detections) { detection -> showDetectionDetails(detection) }
        analyticsBinding.rvRecentDetections.apply {
            layoutManager = LinearLayoutManager(this@AnalyticsActivity)
            adapter = detectionAdapter
        }
    }

    private fun setupObservers() {
        analyticsViewModel.analyticsData.observe(this) { data ->
            data?.let {
                updateOverviewStats(it)
                updateScanActivityChart(it.scanActivity)
                updateThreatTypesChart(it.threatTypes)
                updateDetectionAccuracyChart(it.detectionAccuracy)
            }
        }

        analyticsViewModel.recentDetections.observe(this) { detectionList ->
            detectionList?.let {
                detections.clear()
                detections.addAll(it)
                detectionAdapter.notifyDataSetChanged()
                analyticsBinding.tvNoDetections.visibility = if (detections.isEmpty()) View.VISIBLE else View.GONE
                analyticsBinding.rvRecentDetections.visibility = if (detections.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        analyticsViewModel.isLoading.observe(this) { isLoading ->
            // Show loading indicator
            analyticsBinding.progressBar.visibility = if (isLoading == true) View.VISIBLE else View.GONE
            analyticsBinding.btnRefresh.isEnabled = !isLoading
        }

        analyticsViewModel.error.observe(this) { error ->
            error?.let { showError(it) }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_analytics
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_deepscan -> {
                    startActivity(Intent(this, DeepScanActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_deepfake -> {
                    startActivity(Intent(this, DeepfakeActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_analytics -> true
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadAnalyticsData() {
        analyticsViewModel.loadAnalyticsData(selectedTimePeriod)
        analyticsViewModel.loadRecentDetections()
    }

    // Debounced loader (prevents too-frequent calls)
    private fun loadAnalyticsDataDebounced() {
        val now = System.currentTimeMillis()
        if (now - lastLoadAt < 2000L) return // short debounce (2s)
        lastLoadAt = now
        loadAnalyticsData()
    }

    private fun updateOverviewStats(data: AnalyticsData) {
        analyticsBinding.tvTotalScans.text = data.totalScans.toString()
        analyticsBinding.tvThreatsFound.text = data.threatsFound.toString()

        val successRate = if (data.totalScans > 0) {
            ((data.totalScans - data.threatsFound) * 100 / data.totalScans)
        } else {
            100
        }
        analyticsBinding.tvSuccessRate.text = "${successRate}%"
    }

    private fun refreshCharts() {
        loadAnalyticsDataDebounced()
        analyticsBinding.progressBar.visibility = View.VISIBLE
        analyticsBinding.btnRefresh.isEnabled = false
        
        // Re-enable refresh after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            analyticsBinding.progressBar.visibility = View.GONE
            analyticsBinding.btnRefresh.isEnabled = true
        }, 1000)
    }

    private fun updateScanActivityChart(scanActivity: List<ScanActivityData>) {
        // Clear existing data first
        analyticsBinding.chartScanActivity.data = null

        val entries = scanActivity.mapIndexed { index, point ->
            Entry(index.toFloat(), point.scans.toFloat())
        }

        val dataSet = LineDataSet(entries, "Daily Scans").apply {
            color = ContextCompat.getColor(this@AnalyticsActivity, R.color.primary_color)
            setCircleColor(ContextCompat.getColor(this@AnalyticsActivity, R.color.primary_color))
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
            valueTextColor = ContextCompat.getColor(this@AnalyticsActivity, R.color.light_gray)
            setDrawValues(false)
        }

        analyticsBinding.chartScanActivity.data = LineData(dataSet)
        analyticsBinding.chartScanActivity.xAxis.valueFormatter = IndexAxisValueFormatter(scanActivity.map { it.date })
        analyticsBinding.chartScanActivity.invalidate()
    }

    private fun updateThreatTypesChart(threatTypes: Map<String, Int>) {
        analyticsBinding.chartThreatTypes.data = null

        val entries = threatTypes.map { (type, count) -> PieEntry(count.toFloat(), type) }
        val dataSet = PieDataSet(entries, "Threat Types").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextColor = Color.WHITE
            valueTextSize = 12f
        }

        analyticsBinding.chartThreatTypes.data = PieData(dataSet)
        analyticsBinding.chartThreatTypes.invalidate()
    }

    private fun updateDetectionAccuracyChart(accuracyData: List<AccuracyData>) {
        analyticsBinding.chartDetectionAccuracy.data = null

        val entries = accuracyData.mapIndexed { index, data -> BarEntry(index.toFloat(), data.accuracy.toFloat()) }
        val dataSet = BarDataSet(entries, "Detection Accuracy").apply {
            color = ContextCompat.getColor(this@AnalyticsActivity, R.color.primary_color)
            valueTextColor = ContextCompat.getColor(this@AnalyticsActivity, R.color.light_gray)
            valueTextSize = 12f
        }

        analyticsBinding.chartDetectionAccuracy.data = BarData(dataSet)
        analyticsBinding.chartDetectionAccuracy.xAxis.valueFormatter = IndexAxisValueFormatter(accuracyData.map { it.date })
        analyticsBinding.chartDetectionAccuracy.invalidate()
    }

    private fun exportAnalytics() {
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "DeepGuard Analytics Report - $selectedTimePeriod")
            putExtra(Intent.EXTRA_SUBJECT, "DeepGuard Analytics Report")
        }
        startActivity(Intent.createChooser(intent, "Export Analytics"))
    }

    private fun viewAllDetections() {
        // Navigate to detailed detections view
        startActivity(Intent(this, DetectionHistoryActivity::class.java))
    }

    private fun showDetectionDetails(detection: Detection) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Detection Details")
            .setMessage("""
                Type: ${detection.type}
                Confidence: ${detection.confidence}%
                Source: ${detection.source}
                Time: ${detection.timestamp}
                ${detection.details ?: ""}
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showError(error: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(error)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Register broadcast receiver (safe)
        try {
            val filter = IntentFilter("com.example.shieldx.STATS_UPDATED")
            // avoid using RECEIVER_NOT_EXPORTED (may not exist on some platforms)
            registerReceiver(statsUpdateReceiver, filter)
        } catch (ex: Exception) {
            // ignore registration issues (log if desired)
        }

        // start periodic refresh
        if (!refreshScheduled) {
            mainHandler.post(refreshRunnable)
            refreshScheduled = true
        }

        // immediate initial load
        loadAnalyticsDataDebounced()
    }

    override fun onPause() {
        super.onPause()
        // Stop periodic refresh
        mainHandler.removeCallbacks(refreshRunnable)
        refreshScheduled = false

        // Unregister receiver safely
        try {
            unregisterReceiver(statsUpdateReceiver)
        } catch (e: Exception) {
            // already unregistered — ignore
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(refreshRunnable)
    }
}
