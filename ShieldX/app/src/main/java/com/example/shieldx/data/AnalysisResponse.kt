package com.example.shieldx.data

/**
 * Represents the AI analysis response from the ShieldX backend.
 * Supports both text-based harassment detection and deepfake analysis.
 */
data class AnalysisResponse(
    val success: Boolean = true,                  // ✅ Backend success flag
    val message: String? = null,                  // 📝 Server message or debug info
    val threatType: String? = null,               // ⚠️ "harassment", "deepfake", or "mixed"
    val confidence: Double? = null,               // 🔢 Overall confidence score (0–100)
    val severity: String? = null,                 // 🔥 Computed threat severity: LOW/MEDIUM/HIGH
    val harassment: HarassmentResult? = null,     // 💬 Text analysis results
    val deepfake: DeepfakeResult? = null,         // 🎭 Media analysis results
    val source: String? = null,                   // 📱 Source app/package name
    val analyzedAt: Long = System.currentTimeMillis() // 🕒 Analysis timestamp
)

/**
 * Represents the AI output for harassment detection in text messages or notifications.
 */
data class HarassmentResult(
    val isHarassment: Boolean,
    val confidence: Double,
    val category: String? = null,      // Example: "Cyberbullying", "Toxicity", etc.
    val type: String? = null,          // Example: "Message", "Comment", "DM"
    val severity: String? = null,      // Computed from confidence (LOW/MEDIUM/HIGH)
    val details: String? = null        // Optional detailed reasoning from AI model
)

/**
 * Represents AI results for deepfake or manipulated media detection.
 */
data class DeepfakeResult(
    val isDeepfake: Boolean,
    val confidence: Double,
    val mediaType: String? = null,     // "image", "video", or "audio"
    val severity: String? = null,      // Computed severity level
    val details: String? = null        // Optional backend explanation
)
