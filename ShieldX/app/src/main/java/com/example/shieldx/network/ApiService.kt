package com.example.shieldx.network

import android.content.Context
import android.util.Log
import com.example.shieldx.models.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.http.*
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 🔰 DeepGuard v3.0 - Unified API Service Interface
 * Complete REST API integration for Android ↔ FastAPI backend
 */
interface ApiService {

    // ================================
    // 🔐 AUTHENTICATION
    // ================================

    @POST("api/v1/auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<ApiResponse<LoginResponse>>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginResponse>>

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Header("Authorization") token: String): Response<ApiResponse<LoginResponse>>

    @GET("api/v1/auth/me")
    suspend fun getCurrentUser(@Header("Authorization") token: String): Response<ApiResponse<User>>

    @PUT("api/v1/auth/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body user: User
    ): Response<ApiResponse<User>>


    // ================================
    // 🧠 SCANNING
    // ================================

    @POST("api/v1/scan_text")
    suspend fun scanText(
        @Header("Authorization") token: String,
        @Body request: ScanRequest
    ): Response<ApiResponse<ScanResult>>

    @Multipart
    @POST("api/v1/scan_media")
    suspend fun scanMedia(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part,
        @Part("scan_type") scanType: RequestBody
    ): Response<ApiResponse<ScanResult>>

    @Multipart
    @POST("api/v1/scan_deepfake")
    suspend fun scanDeepfake(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<ScanResult>>

    @POST("api/v1/deepscan")
    suspend fun startDeepScan(
        @Header("Authorization") token: String,
        @Body scanRequest: ScanRequest
    ): Response<ApiResponse<ScanResult>>


    // ================================
    // 👤 USER MANAGEMENT
    // ================================

    @GET("api/v1/user/scans")
    suspend fun getUserScans(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<ApiResponse<List<ScanResult>>>

    @GET("api/v1/user/stats")
    suspend fun getUserStats(
        @Header("Authorization") token: String,
        @Query("period") period: String = "week"
    ): Response<ApiResponse<StatsResponse>>

    @DELETE("api/v1/user/scan/{scanId}")
    suspend fun deleteScan(
        @Header("Authorization") token: String,
        @Path("scanId") scanId: String
    ): Response<ApiResponse<String>>


    // ================================
    // 📂 FILE MANAGEMENT
    // ================================

    @Multipart
    @POST("api/v1/upload")
    suspend fun uploadFile(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part,
        @Part("file_type") fileType: RequestBody
    ): Response<ApiResponse<FileUploadResponse>>

    @GET("api/v1/download/{fileId}")
    suspend fun downloadFile(
        @Header("Authorization") token: String,
        @Path("fileId") fileId: String
    ): Response<ResponseBody>

    @DELETE("api/v1/file/{fileId}")
    suspend fun deleteFile(
        @Header("Authorization") token: String,
        @Path("fileId") fileId: String
    ): Response<ApiResponse<String>>


    // ================================
    // 📊 ANALYTICS
    // ================================

    @GET("api/v1/analytics/dashboard")
    suspend fun getDashboardAnalytics(
        @Header("Authorization") token: String,
        @Query("days") days: Int = 7
    ): Response<ApiResponse<StatsResponse>>

    @GET("api/v1/analytics/trends")
    suspend fun getTrends(
        @Header("Authorization") token: String,
        @Query("period") period: String = "month"
    ): Response<ApiResponse<List<WeeklyTrend>>>

    @GET("api/v1/analytics/summary")
    suspend fun getScanSummary(
        @Header("Authorization") token: String
    ): Response<ApiResponse<ScanSummary>>


    // ================================
    // ⚙️ SYSTEM HEALTH
    // ================================

    @GET("health")
    suspend fun healthCheck(): Response<ApiResponse<String>>

    @GET("ping")
    suspend fun ping(): Response<ApiResponse<String>>


    // ================================
    // 🛠️ ADMIN ENDPOINTS
    // ================================

    @GET("api/v1/admin/users")
    suspend fun getAllUsers(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<List<User>>>

    @GET("api/v1/admin/scans")
    suspend fun getAllScans(
        @Header("Authorization") token: String,
        @Query("date") date: String? = null
    ): Response<ApiResponse<List<ScanResult>>>

    @GET("api/v1/admin/stats")
    suspend fun getSystemStats(
        @Header("Authorization") token: String
    ): Response<ApiResponse<Any>>


    // ================================
    // 🔔 NOTIFICATIONS
    // ================================

    @POST("api/v1/notifications/register")
    suspend fun registerForNotifications(
        @Header("Authorization") token: String,
        @Body fcmToken: Map<String, String>
    ): Response<ApiResponse<String>>

    @PUT("api/v1/notifications/settings")
    suspend fun updateNotificationSettings(
        @Header("Authorization") token: String,
        @Body settings: NotificationSettings
    ): Response<ApiResponse<NotificationSettings>>

    @GET("api/v1/notifications/settings")
    suspend fun getNotificationSettings(
        @Header("Authorization") token: String
    ): Response<ApiResponse<NotificationSettings>>
}


/**
 * 🌐 API Client Factory - Smart base URL detection + retries + logging
 */
object ApiClient {

    private fun resolveBaseUrl(): String {
        val urls = listOf(
            "https://deepguard-api.onrender.com/",
            "http://10.0.2.2:8002/",
            "http://192.168.0.22:8002/",
            "http://192.168.137.1:8002/"
        )
        for (url in urls) {
            try {
                java.net.URL(url).openConnection().apply {
                    connectTimeout = 1500
                    connect()
                    return url
                }
            } catch (_: Exception) { }
        }
        return urls.first()
    }

    private val BASE_URL = resolveBaseUrl()
    private var retrofit: retrofit2.Retrofit? = null

    fun getRetrofit(): retrofit2.Retrofit {
        if (retrofit == null) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val retryInterceptor = Interceptor { chain ->
                var attempt = 0
                var lastResponse: okhttp3.Response? = null
                var lastException: IOException? = null
                
                while (attempt < 2) {
                    try {
                        // Close the previous response before making a new request
                        lastResponse?.close()
                        
                        val response = chain.proceed(chain.request())
                        if (response.isSuccessful) {
                            return@Interceptor response
                        }
                        // Store the response so we can close it on the next iteration
                        lastResponse = response
                    } catch (e: IOException) {
                        lastException = e
                    }
                    attempt++
                }
                // Make sure to close the last response before throwing
                lastResponse?.close()
                throw lastException ?: IOException("Network failed after retries")
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(logging.apply {
                    level = HttpLoggingInterceptor.Level.BASIC  // Reduce logging to prevent response body consumption
                })
                .addInterceptor(retryInterceptor)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            retrofit = retrofit2.Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }

    fun getApiService(): ApiService = getRetrofit().create(ApiService::class.java)

    /** ✅ Initialize API on app start */
    fun initialize(context: Context) {
        getRetrofit()
        Log.i("ApiClient", "✅ ShieldX API initialized with base URL: $BASE_URL")
    }

    /** 🌐 Ping backend server to verify connection */
    suspend fun pingServer(): Boolean {
        return try {
            val response = getApiService().ping()
            val success = response.isSuccessful && response.body()?.success == true
            Log.i("ApiClient", "🌐 Ping server: ${if (success) "OK ✅" else "Failed ❌"}")
            success
        } catch (e: Exception) {
            Log.e("ApiClient", "❌ Ping failed: ${e.message}")
            false
        }
    }
}


/**
 * 🔧 Network Utilities & Helpers
 */
object NetworkUtils {

    fun createAuthHeader(token: String) = "Bearer $token"

    fun createMultipartFile(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String? = null
    ): MultipartBody.Part {
        val type = mimeType ?: "application/octet-stream"
        val requestFile = fileBytes.toRequestBody(type.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", fileName, requestFile)
    }

    fun createRequestBody(value: String): RequestBody =
        value.toRequestBody("text/plain".toMediaTypeOrNull())
}


/**
 * ✅ Safer API response checker
 */
fun <T> Response<ApiResponse<T>>.isSuccessfulResponse(): Boolean {
    return this.isSuccessful && this.body()?.success == true
}
