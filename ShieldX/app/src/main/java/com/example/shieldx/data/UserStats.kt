package com.example.shieldx.data

/**
 * Data class representing the user's dashboard statistics
 */
/**
 * Data class representing the user's dashboard UI statistics.
 * This is different from the API UserStats model in Models.kt.
 */
data class DashboardUserStats(
    val totalScans: Int,
    val threatsDetected: Int,
    val isProtectionActive: Boolean,
    val recentScans: List<RecentScan>
)