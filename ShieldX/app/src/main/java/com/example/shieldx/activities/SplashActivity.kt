package com.example.shieldx.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.example.shieldx.R
import com.example.shieldx.utils.SharedPref
import android.widget.Toast
import kotlinx.coroutines.*

/**
 * DeepGuard v3.0 - Splash Activity (Enhanced)
 * Animated startup screen with backend readiness check.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var sharedPref: SharedPref
    private val splashDuration = 2500L // 2.5 seconds animation
    private var isBackendReady = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        sharedPref = SharedPref.getInstance(this)

        // 🎞️ Start logo animation
        val animationView = findViewById<LottieAnimationView>(R.id.lottie_animation)
        animationView.setAnimation(R.raw.deepguard_logo_animation)
        animationView.playAnimation()

        // 🔄 Check backend connection in parallel
        checkBackendConnection()

        // ⏳ Continue after animation
        mainHandler.postDelayed({
            navigateToNextScreen()
        }, splashDuration)
    }

    /**
     * Simulate backend availability check
     * (In production, ping your FastAPI /health endpoint here)
     */
    private fun checkBackendConnection() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Example: ping backend
                delay(1000) // simulate network latency
                isBackendReady = true
            } catch (e: Exception) {
                isBackendReady = false
            }
        }
    }

    /**
     * Decide where to go next after splash screen
     */
    private fun navigateToNextScreen() {
        if (!isBackendReady) {
            Toast.makeText(this, "⚠️ Backend not reachable — offline mode", Toast.LENGTH_SHORT).show()
        }

        val nextIntent = when {
            sharedPref.isLoggedIn() -> {
                // ✅ Logged in → go to Dashboard
                Intent(this, DashboardActivity::class.java)
            }
            else -> {
                // 🚪 Not logged in → go to Login
                Intent(this, LoginActivity::class.java)
            }
        }

        startActivity(nextIntent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
