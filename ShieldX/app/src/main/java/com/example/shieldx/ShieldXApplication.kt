package com.example.shieldx

import android.app.Application
import android.util.Log
import androidx.multidex.MultiDexApplication
import com.example.shieldx.network.ApiClient
import com.example.shieldx.utils.ToastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * DeepGuard v3.0 - ShieldX Application
 * Initializes global services and verifies backend connection
 */
class ShieldXApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        try {
            initializeApp()
        } catch (e: Exception) {
            Log.e("ShieldXApplication", "Initialization failed: ${e.message}", e)
        }
    }

    /**
     * Initialize core services, analytics, and API client
     */
    private fun initializeApp() {
        Log.i("ShieldXApplication", "🚀 Initializing ShieldX Application")

        // Initialize Toast Manager (optional prewarm)
        ToastManager.clear()

        // Initialize API client
        ApiClient.initialize(this)

        // Test backend connection asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            pingServer()
        }
    }

    /**
     * Ping backend server to verify connectivity
     */
    private suspend fun pingServer() {
        try {
            val api = ApiClient.getApiService()
            val response = api.ping() // <-- this requires a simple /ping endpoint in your backend

            if (response.isSuccessful) {
                Log.i("ShieldXApplication", "✅ Backend connected: ${response.body()}")
            } else {
                Log.w("ShieldXApplication", "⚠️ Backend responded with error: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("ShieldXApplication", "❌ Unable to reach backend: ${e.message}")
        }
    }
}
