package com.example.shieldx.api

import com.example.shieldx.data.AnalysisResponse
import com.example.shieldx.data.NotificationPayload
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * ShieldXApiService
 * ------------------------------
 * Defines all DeepGuard backend endpoints used by ShieldX.
 * Supports both authenticated and unauthenticated access where applicable.
 */
interface ShieldXApiService {

    // ✅ Health check - Used by ConnectionTester & API initializer
    @GET("/api/v1/health")
    suspend fun ping(): Response<Unit>

    // 🔹 Analyze Notifications (Text, Chat, etc.)
    @POST("/api/v1/mobile/analyze-notification")
    suspend fun analyzeNotification(
        @Header("Authorization") token: String?,
        @Body payload: NotificationPayload
    ): Response<AnalysisResponse>

    // 🔹 Analyze Notifications (without authentication, fallback)
    @POST("/api/v1/mobile/analyze-notification")
    suspend fun analyzeNotificationWithoutAuth(
        @Body payload: NotificationPayload
    ): Response<AnalysisResponse>

    // 🔹 Analyze Media (Images / Videos / Deepfake Detection)
    @POST("/api/v1/mobile/analyze-media")
    suspend fun analyzeMediaContent(
        @Header("Authorization") token: String?,
        @Body mediaPayload: NotificationPayload
    ): Response<AnalysisResponse>

    // 🔹 Analyze Media (without authentication, fallback)
    @POST("/api/v1/mobile/analyze-media")
    suspend fun analyzeMediaContentWithoutAuth(
        @Body mediaPayload: NotificationPayload
    ): Response<AnalysisResponse>
}
