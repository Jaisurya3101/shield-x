package com.example.shieldx.data

/**
 * Represents the analytics and insights data returned by the ShieldX backend.
 * Tracks system performance, threat detection trends, and scan statistics.
 */
data class AnalyticsData(
    val totalScans: Int = 0,                            // Total number of scans performed
    val threatsFound: Int = 0,                          // Total number of detected threats
    val safetyScore: Double = 100.0,                    // Overall safety rating (0–100)
    val lastScan: String = "-",                         // Human-readable timestamp of last scan
    val scanActivity: List<ScanActivityData> = emptyList(),  // Trend of scans per day/week
    val threatTypes: Map<String, Int> = emptyMap(),      // Breakdown of detected threats
    val detectionAccuracy: List<AccuracyData> = emptyList(), // AI model accuracy over time
    val recentDetections: List<DetectionData> = emptyList(), // Recent threat detections
    val weeklyChange: Double? = null,                    // % change in threats compared to previous week
    val topThreatCategory: String? = null,               // Most common detected threat type
    val backendStatus: String? = "Online",               // Backend health info for status badges
    val updatedAt: Long = System.currentTimeMillis()     // Local timestamp for cache validation
)

/**
 * Represents scan activity over time (used for line charts / daily trend).
 */
data class ScanActivityData(
    val date: String,    // Example: "2025-10-29"
    val scans: Int
)

/**
 * Represents AI accuracy metrics (used for accuracy charts).
 */
data class AccuracyData(
    val date: String,    // Example: "2025-10-28"
    val accuracy: Double // Model accuracy percentage
)

/**
 * Represents a single detection event shown in the dashboard’s recent activity list.
 */
data class DetectionData(
    val type: String,        // Example: "harassment", "deepfake", "spam"
    val source: String,      // App/package name or message origin
    val time: String,        // Example: "10:45 PM" or "2025-10-28T18:15Z"
    val confidence: Double,  // Detection confidence (0–100)
    val severity: String? = null, // Computed label: "Low", "Medium", "High"
    val details: String? = null   // Optional AI insight text
)
