package com.example.shieldx.viewmodel

import android.app.Application
import android.graphics.Color
import androidx.lifecycle.*
import com.example.shieldx.models.*
import com.example.shieldx.data.AnalyticsData
import com.example.shieldx.data.ScanActivityData
import com.example.shieldx.data.DetectionData
import com.example.shieldx.repository.AnalyticsRepository
import com.example.shieldx.network.ApiClient
import kotlinx.coroutines.launch

/**
 * DeepGuard v3.1 - Analytics ViewModel
 *
 * Central ViewModel managing analytics, dashboard trends, safety scores, and threat insights.
 * Provides LiveData for UI components to observe real-time analytics updates.
 */
class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val analyticsRepository = AnalyticsRepository(application, ApiClient.getApiService())

    // =======================================
    // LiveData & State
    // =======================================
    private val _dashboardState = MutableLiveData<DashboardState>()
    val dashboardState: LiveData<DashboardState> = _dashboardState

    private val _userStats = MutableLiveData<UserStats?>()
    val userStats: LiveData<UserStats?> = _userStats

    private val _dailyStats = MutableLiveData<List<DailyStat>>()
    val dailyStats: LiveData<List<DailyStat>> = _dailyStats

    private val _weeklyTrends = MutableLiveData<List<WeeklyTrend>>()
    val weeklyTrends: LiveData<List<WeeklyTrend>> = _weeklyTrends

    private val _scanSummary = MutableLiveData<ScanSummary?>()
    val scanSummary: LiveData<ScanSummary?> = _scanSummary

    private val _recentScans = MutableLiveData<List<ScanResult>>()
    val recentScans: LiveData<List<ScanResult>> = _recentScans

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    // Compatibility LiveData
    private val _analyticsData = MutableLiveData<AnalyticsData?>()
    val analyticsData: LiveData<AnalyticsData?> = _analyticsData

    private val _recentDetections = MutableLiveData<List<Detection>>()
    val recentDetections: LiveData<List<Detection>> = _recentDetections

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        _dashboardState.value = DashboardState(isLoading = true)
        loadDashboardData()
    }

    // =======================================
    // Dashboard Loading
    // =======================================
    fun loadDashboardData() {
        viewModelScope.launch {
            setLoading(true)
            try {
                val result = analyticsRepository.getDashboardAnalytics(7)

                result.fold(
                    onSuccess = { stats ->
                        _userStats.postValue(stats.userStats)
                        _dailyStats.postValue(stats.dailyStats)
                        _weeklyTrends.postValue(stats.weeklyTrend)
                        _scanSummary.postValue(stats.scanSummary)

                        _dashboardState.postValue(
                            DashboardState(
                                isLoading = false,
                                userStats = stats.userStats,
                                recentScans = _recentScans.value ?: emptyList()
                            )
                        )
                        _errorMessage.postValue("")
                    },
                    onFailure = { e -> handleError("Failed to load dashboard", e) }
                )
            } catch (e: Exception) {
                handleError("Unexpected error loading dashboard", e)
            } finally {
                setLoading(false)
            }
        }
    }

    // =======================================
    // User Statistics
    // =======================================
    fun loadUserStats(period: String = "week") {
        viewModelScope.launch {
            try {
                val result = analyticsRepository.getUserStats(period)
                result.fold(
                    onSuccess = { stats ->
                        _userStats.postValue(stats.userStats)
                        _dailyStats.postValue(stats.dailyStats)
                        _weeklyTrends.postValue(stats.weeklyTrend)
                        _scanSummary.postValue(stats.scanSummary)
                    },
                    onFailure = { e -> handleError("Failed to load user stats", e) }
                )
            } catch (e: Exception) {
                handleError("Error loading user stats", e)
            }
        }
    }

    // =======================================
    // Trend Loading
    // =======================================
    fun loadTrends(period: String = "month") {
        viewModelScope.launch {
            try {
                val result = analyticsRepository.getTrends(period)
                result.fold(
                    onSuccess = { _weeklyTrends.postValue(it) },
                    onFailure = { e -> handleError("Failed to load trends", e) }
                )
            } catch (e: Exception) {
                handleError("Error fetching trends", e)
            }
        }
    }

    // =======================================
    // Scan Summary
    // =======================================
    fun loadScanSummary() {
        viewModelScope.launch {
            try {
                val result = analyticsRepository.getScanSummary()
                result.fold(
                    onSuccess = { _scanSummary.postValue(it) },
                    onFailure = { e -> handleError("Failed to load scan summary", e) }
                )
            } catch (e: Exception) {
                handleError("Error fetching scan summary", e)
            }
        }
    }

    // =======================================
    // Refresh & Utility
    // =======================================
    fun refreshData() = loadDashboardData()
    fun clearError() = _errorMessage.postValue("")

    private fun handleError(prefix: String, e: Throwable) {
        val msg = "$prefix: ${e.message ?: "Unknown error"}"
        _errorMessage.postValue(msg)
        _dashboardState.postValue(
            DashboardState(isLoading = false, error = msg)
        )
    }

    private fun setLoading(state: Boolean) = _isLoading.postValue(state)

    // =======================================
    // Helpers: Risk & Safety Calculations
    // =======================================
    fun getSafetyScoreColor(score: Double): Int = when {
        score >= 80 -> Color.parseColor("#4CAF50") // Green
        score >= 60 -> Color.parseColor("#FFEB3B") // Yellow
        score >= 40 -> Color.parseColor("#FF9800") // Orange
        else -> Color.parseColor("#F44336") // Red
    }

    fun getRiskLevelText(score: Double): String = when {
        score >= 80 -> "Low Risk"
        score >= 60 -> "Medium Risk"
        score >= 40 -> "High Risk"
        else -> "Critical Risk"
    }

    fun getTotalThreats(): Int =
        _weeklyTrends.value?.sumOf { it.totalThreats } ?: 0

    fun getLatestSafetyScore(): Double =
        _weeklyTrends.value?.lastOrNull()?.riskLevel ?: 0.0

    fun hasData(): Boolean = _userStats.value != null

    // =======================================
    // Compatibility Functions
    // =======================================
    fun loadAnalyticsData(timePeriod: String = "week") {
        viewModelScope.launch {
            setLoading(true)
            try {
                val data = AnalyticsData(
                    totalScans = _userStats.value?.totalScans ?: 0,
                    threatsFound = getTotalThreats(),
                    safetyScore = getLatestSafetyScore(),
                    lastScan = "Today",
                    scanActivity = _dailyStats.value?.map { daily ->
                        ScanActivityData(
                            date = daily.date,
                            scans = daily.scansCount
                        )
                    } ?: emptyList(),
                    threatTypes = mapOf("harassment" to 5, "deepfake" to 3, "spam" to 2),
                    detectionAccuracy = emptyList(),
                    recentDetections = _recentDetections.value?.map { detection ->
                        DetectionData(
                            type = detection.type,
                            confidence = detection.confidence.toDouble(),
                            source = detection.source,
                            time = java.text.SimpleDateFormat("HH:mm a").format(detection.timestamp)
                        )
                    } ?: emptyList()
                )
                _analyticsData.postValue(data)
            } catch (e: Exception) {
                _error.postValue(e.message)
            } finally {
                setLoading(false)
            }
        }
    }

    fun loadRecentDetections() {
        viewModelScope.launch {
            try {
                val detections = _recentScans.value?.map { scan ->
                    Detection(
                        id = scan.id,
                        type = when {
                            scan.isHarassment -> "harassment"
                            scan.isDeepfake -> "deepfake"
                            scan.isHarmful -> "threat"
                            else -> "safe"
                        },
                        confidence = (scan.confidenceScore * 100).toInt(),
                        timestamp = System.currentTimeMillis(),
                        source = scan.fileName ?: "System",
                        details = scan.detailedAnalysis
                    )
                } ?: emptyList()
                _recentDetections.postValue(detections)
            } catch (e: Exception) {
                _error.postValue(e.message)
            }
        }
    }
}

/**
 * Represents the current state of the analytics dashboard.
 */
data class DashboardState(
    val isLoading: Boolean = false,
    val userStats: UserStats? = null,
    val recentScans: List<ScanResult> = emptyList(),
    val error: String? = null
)
