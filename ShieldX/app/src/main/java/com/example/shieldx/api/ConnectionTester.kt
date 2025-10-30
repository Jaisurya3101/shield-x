package com.example.shieldx.api

import android.util.Log
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

/**
 * DeepGuard / ShieldX Connection Tester
 * ------------------------------------
 * Verifies connectivity with the backend, tests multiple fallback endpoints,
 * and logs detailed connection diagnostics for debugging.
 *
 * Usage:
 *   ConnectionTester.runAllTests()
 */
object ConnectionTester {
    private const val TAG = "ConnectionTester"

    private val testEndpoints = listOf(
        "https://deepguard-api.onrender.com",   // ✅ Production API (Render)
        "http://10.0.2.2:8002",                 // Android Emulator
        "http://192.168.0.22:8002",             // Local Wi-Fi (Primary)
        "http://192.168.0.22:8001",             // Local Wi-Fi (Alt)
        "http://192.168.56.1:8002",             // VirtualBox Host
        "http://192.168.137.1:8002",            // Hotspot
        "http://localhost:8002",                // Localhost (Direct)
        "http://localhost:8001"                 // Localhost (Backup)
    )

    /**
     * Entry point for the full test sequence.
     */
    fun runAllTests() {
        CoroutineScope(Dispatchers.IO).launch {
            Log.i(TAG, "🔍 Running full ShieldX backend connection diagnostics...")
            testApiConnectivity()
            testAllEndpoints()
        }
    }

    /**
     * Test basic backend connection using ShieldXAPI.
     */
    private suspend fun testApiConnectivity() = withContext(Dispatchers.IO) {
        Log.i(TAG, "=== 🧠 DeepGuard API Connectivity Check ===")

        val api = ShieldXAPI()
        val isConnected = api.testConnection()

        if (isConnected) {
            Log.i(TAG, "✅ Connection successful!")
            Log.i(TAG, "🌐 Active Endpoint: ${api.getCurrentBaseUrl()}")
        } else {
            Log.e(TAG, "❌ Connection failed — cannot reach backend API.")
            logTroubleshootingTips()
        }
    }

    /**
     * Sequentially test all known endpoints for reachability.
     */
    private suspend fun testAllEndpoints() = withContext(Dispatchers.IO) {
        Log.i(TAG, "\n=== 🌐 Testing All Candidate Endpoints ===")

        val results = mutableListOf<String>()

        for (url in testEndpoints) {
            try {
                val latency = measureConnectionLatency(url)
                if (latency >= 0) {
                    results.add("✅ [$url] Responded in ${latency}ms")
                } else {
                    results.add("❌ [$url] Unreachable")
                }
                delay(150) // Short delay between requests
            } catch (e: Exception) {
                results.add("❌ [$url] Error: ${e.localizedMessage}")
            }
        }

        Log.i(TAG, "\n=== 🧾 Connection Test Results ===")
        results.forEach { Log.i(TAG, it) }

        val reachable = results.count { it.contains("✅") }
        Log.i(TAG, "\nSummary: $reachable / ${results.size} endpoints reachable ✅")
    }

    /**
     * Measure connection latency to an endpoint.
     */
    private fun measureConnectionLatency(baseUrl: String): Long {
        return try {
            val startTime = System.currentTimeMillis()
            val url = URL(baseUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                requestMethod = "GET"
            }
            connection.connect()
            val code = connection.responseCode
            connection.disconnect()

            if (code in 200..399) {
                System.currentTimeMillis() - startTime
            } else {
                -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * Logs helpful troubleshooting guidance for developers.
     */
    private fun logTroubleshootingTips() {
        Log.e(TAG, """
            🔍 Troubleshooting Steps:
            1️⃣ Ensure the DeepGuard FastAPI backend is running on port 8000 or 8002.
            2️⃣ Verify that your machine's local IP matches the one in `ShieldXAPI`.
            3️⃣ If using an emulator, use `10.0.2.2` instead of `localhost`.
            4️⃣ Disable VPN or proxy temporarily.
            5️⃣ Check if your backend CORS settings allow mobile requests.
            6️⃣ Ensure both devices are on the same Wi-Fi network.
            7️⃣ If hosted on Render or cloud, verify deployment is active.
        """.trimIndent())
    }
}
