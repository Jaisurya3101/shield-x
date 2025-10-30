package com.example.shieldx.repository

import android.content.Context
import android.util.Log
import com.example.shieldx.models.*
import com.example.shieldx.network.ApiService
import com.example.shieldx.network.NetworkUtils
import com.example.shieldx.utils.SharedPref
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

/**
 * DeepGuard v3.1 - Scan Repository
 * Handles all content analysis, harassment detection, and deepfake scanning.
 */
class ScanRepository(
    private val context: Context,
    private val apiService: ApiService
) {
    private val sharedPref = SharedPref.getInstance(context)
    private val tag = "ScanRepository"

    /** ---------------------- TEXT SCAN ---------------------- */
    suspend fun scanText(text: String): Result<ScanResult> = safeApiCall("scanText") {
        val token = getAuthToken()
        val response = apiService.scanText(NetworkUtils.createAuthHeader(token), ScanRequest(text = text, scanType = "text"))
        response.handleResponse()
    }

    /** ---------------------- MEDIA SCAN ---------------------- */
    suspend fun scanMedia(fileBytes: ByteArray, fileName: String, mimeType: String): Result<ScanResult> = safeApiCall("scanMedia") {
        val token = getAuthToken()
        val filePart = NetworkUtils.createMultipartFile(fileBytes, fileName, mimeType)
        val scanTypePart = NetworkUtils.createRequestBody("media")
        val response = apiService.scanMedia(NetworkUtils.createAuthHeader(token), filePart, scanTypePart)
        response.handleResponse()
    }

    /** ---------------------- DEEPFAKE SCAN ---------------------- */
    suspend fun scanDeepfake(fileBytes: ByteArray, fileName: String, mimeType: String): Result<ScanResult> = safeApiCall("scanDeepfake") {
        val token = getAuthToken()
        val filePart = NetworkUtils.createMultipartFile(fileBytes, fileName, mimeType)
        val response = apiService.scanDeepfake(NetworkUtils.createAuthHeader(token), filePart)
        response.handleResponse()
    }

    /** ---------------------- DEEP SCAN ---------------------- */
    suspend fun startDeepScan(): Result<ScanResult> = safeApiCall("startDeepScan") {
        val token = getAuthToken()
        val response = apiService.startDeepScan(NetworkUtils.createAuthHeader(token), ScanRequest(scanType = "deepscan"))
        response.handleResponse()
    }

    /** ---------------------- SCAN HISTORY ---------------------- */
    suspend fun getUserScans(limit: Int = 20, offset: Int = 0): Result<List<ScanResult>> = safeApiCall("getUserScans") {
        val token = getAuthToken()
        val response = apiService.getUserScans(NetworkUtils.createAuthHeader(token), limit, offset)
        response.handleResponse()
    }

    /** ---------------------- DELETE SCAN ---------------------- */
    suspend fun deleteScan(scanId: String): Result<String> = safeApiCall("deleteScan") {
        val token = getAuthToken()
        val response = apiService.deleteScan(NetworkUtils.createAuthHeader(token), scanId)
        response.handleResponse { "Scan deleted successfully" }
    }

    /** ---------------------- HELPERS ---------------------- */
    private fun getAuthToken(): String {
        return sharedPref.getAccessToken() ?: run {
            Log.w(tag, "⚠️ No auth token found — using bypass-login-token")
            "bypass-login-token"
        }
    }

    private suspend fun <T> safeApiCall(operation: String, block: suspend () -> Result<T>): Result<T> {
        return withContext(Dispatchers.IO) {
            try {
                block()
            } catch (e: Exception) {
                Log.e(tag, "❌ Error during $operation", e)
                Result.failure(e)
            }
        }
    }
}

/** ---------------------- RESPONSE HANDLER EXTENSION ---------------------- */
private fun <T> Response<ApiResponse<T>>.handleResponse(onSuccess: ((T) -> Unit)? = null): Result<T> {
    val body = this.body()
    return if (this.isSuccessful && body?.success == true && body.data != null) {
        onSuccess?.invoke(body.data)
        Result.success(body.data)
    } else {
        val errorMsg = body?.error ?: "Unexpected error (HTTP ${this.code()})"
        Log.e("ShieldX", "❌ API failure: $errorMsg")
        Result.failure(Exception(errorMsg))
    }
}