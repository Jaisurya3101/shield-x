package com.example.shieldx.models

/**
 * Represents a single detection event in ShieldX.
 * Can include harassment, deepfake, or other AI-based detections.
 */
data class Detection(
    val id: String,                       // Unique detection ID
    val type: String,                     // Type: "harassment", "deepfake", "spam", etc.
    val source: String,                   // Source app or file analyzed
    val confidence: Int,                  // Confidence score (0–100)
    val timestamp: Long,                  // Detection time (epoch ms)
    val details: String? = null,          // Additional description or model output
    val severityLevel: String? = null,    // e.g., "low", "medium", "high"
    val alertLinked: Boolean = false,     // True if this detection generated an alert
    val mediaUrl: String? = null,         // Optional media path for image/video detections
    val modelVersion: String? = null      // For tracking which AI model was used
)
