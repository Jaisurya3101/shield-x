package com.example.shieldx.models

import com.google.gson.annotations.SerializedName

/**
 * DeepGuard v3.0 - Unified Data Models for ShieldX Android
 * Handles user authentication, scanning, analytics, and system monitoring
 */

// ------------------ AUTH MODELS ------------------

data class User(
    @SerializedName("id") val id: String? = null,
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String? = null,
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("profile_image") val profileImage: String? = null
)

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class SignupRequest(
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("full_name") val fullName: String
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("user") val user: User
)

// ------------------ SCAN MODELS ------------------

data class ScanRequest(
    @SerializedName("text") val text: String? = null,
    @SerializedName("file_path") val filePath: String? = null,
    @SerializedName("scan_type") val scanType: String = "text" // "text", "media", "deepfake"
)

data class ScanResult(
    @SerializedName("id") val id: String,
    @SerializedName("scan_type") val scanType: String,
    @SerializedName("is_harmful") val isHarmful: Boolean,
    @SerializedName("confidence_score") val confidenceScore: Double,
    @SerializedName("details") val details: ScanDetails,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("file_name") val fileName: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("model_version") val modelVersion: String? = null // NEW: Track model version
) {
    val isDeepfake: Boolean get() = details.deepfakeDetected
    val isHarassment: Boolean get() = details.harassmentDetected
    val detailedAnalysis: String?
        get() = details.recommendation ?: when {
            details.deepfakeDetected -> "Deepfake detected with ${(details.deepfakeConfidence * 100).toInt()}% confidence"
            details.harassmentDetected -> "Harassment detected: ${details.harassmentType ?: "general"}"
            else -> "Content appears safe"
        }
}

data class ScanDetails(
    @SerializedName("harassment_detected") val harassmentDetected: Boolean = false,
    @SerializedName("harassment_type") val harassmentType: String? = null,
    @SerializedName("harassment_keywords") val harassmentKeywords: List<String> = emptyList(),
    @SerializedName("harassment_confidence") val harassmentConfidence: Double = 0.0,
    @SerializedName("deepfake_detected") val deepfakeDetected: Boolean = false,
    @SerializedName("deepfake_confidence") val deepfakeConfidence: Double = 0.0,
    @SerializedName("manipulation_type") val manipulationType: String? = null,
    @SerializedName("ai_generated") val aiGenerated: Boolean = false,
    @SerializedName("recommendation") val recommendation: String? = null
)

// ------------------ ANALYTICS MODELS ------------------

data class StatsResponse(
    @SerializedName("user_stats") val userStats: UserStats,
    @SerializedName("daily_stats") val dailyStats: List<DailyStat>,
    @SerializedName("weekly_trend") val weeklyTrend: List<WeeklyTrend>,
    @SerializedName("scan_summary") val scanSummary: ScanSummary
)

data class UserStats(
    @SerializedName("total_scans") val totalScans: Int,
    @SerializedName("harassment_detected") val harassmentDetected: Int,
    @SerializedName("deepfakes_detected") val deepfakesDetected: Int,
    @SerializedName("safety_score") val safetyScore: Double,
    @SerializedName("last_scan") val lastScan: String?
)

data class DailyStat(
    @SerializedName("date") val date: String,
    @SerializedName("scans_count") val scansCount: Int,
    @SerializedName("harassment_count") val harassmentCount: Int,
    @SerializedName("deepfake_count") val deepfakeCount: Int
)

data class WeeklyTrend(
    @SerializedName("week") val week: String,
    @SerializedName("risk_level") val riskLevel: Double,
    @SerializedName("total_threats") val totalThreats: Int
)

data class ScanSummary(
    @SerializedName("safe_files") val safeFiles: Int,
    @SerializedName("ai_generated") val aiGenerated: Int,
    @SerializedName("harmful_media") val harmfulMedia: Int,
    @SerializedName("last_updated") val lastUpdated: String
)

// ------------------ ALERT & SETTINGS ------------------

data class HarassmentAlert(
    val id: String,
    val messageText: String,
    val senderInfo: String,
    val timestamp: Long,
    val confidenceScore: Double,
    val harassmentType: String,
    val isRead: Boolean = false
)

data class UserSettings(
    val detectionSensitivity: Float = 0.7f,
    val enableCloudUpload: Boolean = true,
    val enableNotifications: Boolean = true,
    val darkMode: Boolean = false,
    val excludedContacts: List<String> = emptyList(),
    val autoDeleteFiles: Boolean = true,
    val notificationSound: Boolean = true
)

// ------------------ LOCAL STORAGE MODELS ------------------

data class LocalScanResult(
    val id: String,
    val scanType: String,
    val isHarmful: Boolean,
    val confidenceScore: Double,
    val fileName: String? = null,
    val timestamp: Long,
    val details: String, // JSON of ScanDetails
    val synced: Boolean = false
)

// UI state models moved to respective ViewModels

// ------------------ RESPONSE WRAPPERS ------------------

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: T?,
    @SerializedName("message") val message: String?,
    @SerializedName("error") val error: String?
)

data class ErrorResponse(
    @SerializedName("detail") val detail: String,
    @SerializedName("status_code") val statusCode: Int
)

// ------------------ NOTIFICATION SETTINGS ------------------

data class NotificationSettings(
    val enabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val showPreview: Boolean = true,
    val priority: String = "HIGH"
)

// ------------------ MONITORING & ANALYTICS ------------------

data class ScanActivityPoint(
    val date: String,
    val scans: Int
)

data class MonitoringStats(
    val notificationsScanned: Int,
    val threatsBlocked: Int,
    val warningsSent: Int
)
