package com.example.shieldx.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * DeepGuard v3.2 Utility — ConnectionTester
 * ----------------------------------------
 * 🔹 Tests backend connectivity (health check)
 * 🔹 Diagnoses local IP/port issues
 * 🔹 Includes retry, ping fallback & summary report
 */
object ConnectionTester {

    private const val TAG = "ConnectionTester"
    private const val TIMEOUT_SECONDS = 5L
    private const val RETRY_ATTEMPTS = 2

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val testResults = mutableMapOf<String, Boolean>()

    /**
     * Test API /health endpoint
     */
    suspend fun testConnection(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        repeat(RETRY_ATTEMPTS) { attempt ->
            try {
                val fullUrl = "${baseUrl.trimEnd('/')}/api/v1/health"
                Log.d(TAG, "🌐 Testing: $fullUrl (Attempt ${attempt + 1})")

                val response = client.newCall(
                    Request.Builder().url(fullUrl).build()
                ).execute()

                if (response.isSuccessful) {
                    Log.i(TAG, "✅ Connection success → $baseUrl (HTTP ${response.code})")
                    testResults[baseUrl] = true
                    return@withContext true
                } else {
                    Log.w(TAG, "⚠️ $baseUrl responded with HTTP ${response.code}")
                }
            } catch (e: ConnectException) {
                Log.e(TAG, "❌ Connection refused: $baseUrl")
            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "⏱️ Timeout connecting to $baseUrl")
            } catch (e: IOException) {
                Log.e(TAG, "📡 Network error: ${e.localizedMessage}")
            }

            if (attempt < RETRY_ATTEMPTS - 1) delay(1000L)
        }

        testResults[baseUrl] = false
        false
    }

    /**
     * Test all local endpoints for connectivity
     */
    suspend fun testAllEndpoints() {
        Log.i(TAG, "🔍 === DeepGuard Backend Endpoint Scan ===")

        val ips = listOf(
            "192.168.0.22",
            "192.168.1.10",
            "192.168.56.1",
            "192.168.137.1",
            "10.0.2.2" // Emulator
        )

        val ports = listOf(8000, 8001)
        var anyConnected = false

        for (ip in ips) {
            for (port in ports) {
                val url = "http://$ip:$port/"
                val success = testConnection(url)
                if (success) anyConnected = true
            }
        }

        if (!anyConnected) {
            Log.w(TAG, "❌ No reachable endpoints detected.")
            performPingTest()
            printTroubleshootingInfo()
        } else {
            Log.i(TAG, "✅ Connection established to at least one backend.")
        }

        printSummary()
    }

    /**
     * Optional: Ping check for physical network connectivity
     */
    private fun performPingTest() {
        try {
            val reachable = InetAddress.getByName("8.8.8.8").isReachable(2000)
            if (reachable) {
                Log.i(TAG, "🌎 Internet connection is active.")
            } else {
                Log.e(TAG, "🚫 No internet connectivity detected (Ping failed).")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ping test failed: ${e.message}")
        }
    }

    /**
     * Display helpful instructions for debugging
     */
    fun printTroubleshootingInfo() {
        Log.e(TAG, "🧠 Troubleshooting Steps:")
        Log.e(TAG, "   1️⃣  Ensure DeepGuard backend is running on port 8001 (FastAPI).")
        Log.e(TAG, "   2️⃣  Verify PC and Android device are on the same Wi-Fi.")
        Log.e(TAG, "   3️⃣  Find your local IP via 'ipconfig' or 'ifconfig'.")
        Log.e(TAG, "   4️⃣  Update the API_BASE_URL in SharedPref or constants.")
        Log.e(TAG, "   5️⃣  Disable VPN or Firewall if blocking requests.")
    }

    /**
     * Print summary of test results
     */
    private fun printSummary() {
        Log.i(TAG, "========== 🔧 DeepGuard Endpoint Summary ==========")
        testResults.forEach { (url, success) ->
            if (success)
                Log.i(TAG, "🟢 $url → Reachable")
            else
                Log.e(TAG, "🔴 $url → Unreachable")
        }
        Log.i(TAG, "===================================================")
    }
}
