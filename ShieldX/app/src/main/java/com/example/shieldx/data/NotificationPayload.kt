package com.example.shieldx.data

/**
 * Payload sent from ShieldX Android app to the DeepGuard backend
 * for analysis of text or media content.
 */
data class NotificationPayload(
    val source: String,              // Source app (e.g., "WhatsApp", "Instagram", "System")
    val sender: String,              // Sender name or ID
    val content: String,             // Text or message body
    val timestamp: Long,             // Message timestamp in ms
    val packageName: String? = null, // Android package name (optional)
    val mediaUrl: String? = null,    // Optional URL to image/video for deepfake analysis
    val type: String? = "text",      // "text", "image", "video" — helps backend routing
    val deviceId: String? = null,    // Optional for multi-device analytics
    val language: String? = "en"     // Used for language detection / translation
)
