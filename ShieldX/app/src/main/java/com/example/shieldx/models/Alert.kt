package com.example.shieldx.models

/**
 * Represents a detected alert or threat notification in ShieldX.
 * Supports both harassment and deepfake detections.
 */
data class Alert(
    val id: String,                       // Unique alert ID
    val title: String,                    // Alert title or short summary
    val message: String,                  // Detailed message or explanation
    val appName: String,                  // App or source (e.g., WhatsApp, Instagram)
    val threatType: String,               // Type: "harassment", "deepfake", "spam", etc.
    val confidence: Int,                  // Detection confidence (0–100)
    val timestamp: Long,                  // When the threat was detected
    val content: String? = null,          // Raw content or snippet (optional)
    val isBlocked: Boolean = false,       // Whether the threat was auto-blocked
    val userAction: String? = null,       // e.g., "ignored", "deleted", "reported"
    val severityLevel: String? = null,    // e.g., "low", "medium", "high"
    val sourceUrl: String? = null,        // Optional URL (for media/deepfake)
    val reviewed: Boolean = false         // Whether the user reviewed the alert
)
