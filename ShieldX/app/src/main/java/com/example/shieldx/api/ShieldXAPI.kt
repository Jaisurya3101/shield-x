package com.example.shieldx.api

import com.example.shieldx.data.AnalysisResponse
import com.example.shieldx.data.NotificationPayload
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * ShieldXAPI
 * ------------------------------
 * Handles backend communication with the DeepGuard (FastAPI) backend.
 * Includes automatic base URL fallback, request logging, and token-based authentication.
 */
class ShieldXAPI {

    companion object {
        private const val TAG = "ShieldXAPI"

        // ✅ Ordered by priority: Production first, then local development
        private val BASE_URLS = listOf(
            "https://deepguard-api.onrender.com",  // Cloud (Render) ✅
            "http://10.0.2.2:8002",                // Android emulator (localhost)
            "http://192.168.0.22:8002",            // Local Wi-Fi primary
            "http://192.168.137.1:8002",           // Mobile hotspot
            "http://192.168.56.1:8002",            // VirtualBox Host
            "http://192.168.0.22:8001",            // Local fallback
            "http://192.168.56.1:8001",            // VirtualBox fallback
            "http://localhost:8002",               // Localhost direct
            "http://localhost:8001"                // Localhost backup
        )

        // Default token placeholder (to be replaced dynamically if needed)
        private var AUTH_TOKEN = "Bearer your-jwt-token-here"

        /**
         * Optionally allow runtime updates for token
         */
        fun updateAuthToken(token: String) {
            AUTH_TOKEN = "Bearer $token"
            Log.i(TAG, "🔑 Auth token updated successfully")
        }
    }

    private var currentApiService: ShieldXApiService? = null
    private var workingBaseUrl: String? = null
    private val apiScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        apiScope.launch {
            initializeApiService()
        }
    }

    /**
     * Initialize Retrofit with OkHttp logging and timeout configuration.
     * Automatically tests each base URL and selects the first working one.
     */
    private suspend fun initializeApiService() {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d(TAG, "HTTP → $message")
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS) // For Render startup latency
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        for (baseUrl in BASE_URLS) {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                val service = retrofit.create(ShieldXApiService::class.java)

                // Quick ping test to ensure server is live
                if (pingServer(service, baseUrl)) {
                    currentApiService = service
                    workingBaseUrl = baseUrl
                    Log.i(TAG, "✅ Connected to backend: $baseUrl")
                    break
                } else {
                    Log.w(TAG, "⚠️ $baseUrl not responding.")
                }

            } catch (e: Exception) {
                Log.w(TAG, "❌ Failed to initialize $baseUrl: ${e.message}")
            }
        }

        if (currentApiService == null) {
            Log.e(TAG, "🚨 No active backend connection available. Check backend status.")
        }
    }

    /**
     * Attempts a lightweight ping request to validate connection.
     */
    private suspend fun pingServer(service: ShieldXApiService, baseUrl: String): Boolean {
        return try {
            Log.d(TAG, "🌐 Pinging $baseUrl/api/v1/health ...")
            val response = service.ping()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Analyze a notification payload using DeepGuard backend.
     */
    suspend fun analyzeNotification(payload: NotificationPayload): Response<AnalysisResponse> {
        val apiService = currentApiService
            ?: throw IllegalStateException("❌ API not initialized. Check backend availability.")

        return try {
            Log.d(TAG, "🧠 Analyzing text: ${payload.content.take(60)}...")
            Log.d(TAG, "📡 Using: $workingBaseUrl/api/v1/mobile/analyze-notification")

            try {
                val response = apiService.analyzeNotification(AUTH_TOKEN, payload)
                Log.d(TAG, "✅ Authenticated Response: ${response.code()}")
                response
            } catch (authError: Exception) {
                Log.w(TAG, "⚠️ Auth failed, retrying without token: ${authError.message}")
                val response = apiService.analyzeNotificationWithoutAuth(payload)
                Log.d(TAG, "🟡 Fallback Response: ${response.code()}")
                response
            }

        } catch (e: Exception) {
            Log.e(TAG, "🚨 API call failed: ${e.localizedMessage}", e)
            throw e
        }
    }

    /**
     * Return the working base URL used for communication.
     */
    fun getCurrentBaseUrl(): String? = workingBaseUrl

    /**
     * Test backend connectivity.
     */
    suspend fun testConnection(): Boolean {
        val apiService = currentApiService ?: return false
        return try {
            val testPayload = NotificationPayload(
                content = "Connection test message",
                source = "shieldx.test",
                sender = "System",
                timestamp = System.currentTimeMillis()
            )

            val response = apiService.analyzeNotification(AUTH_TOKEN, testPayload)
            val reachable = response.isSuccessful || response.code() in 400..499

            Log.i(TAG, "🌍 Test Connection → success=$reachable (HTTP ${response.code()})")
            reachable
        } catch (e: Exception) {
            Log.e(TAG, "❌ Backend test failed: ${e.localizedMessage}", e)
            false
        }
    }
}
