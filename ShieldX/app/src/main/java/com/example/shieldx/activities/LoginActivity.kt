package com.example.shieldx.activities

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.shieldx.R
import com.example.shieldx.utils.SharedPref
import com.example.shieldx.viewmodel.AuthViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * DeepGuard v3.1 - LoginActivity
 * Handles user authentication securely and connects to backend FastAPI service.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var authViewModel: AuthViewModel
    private lateinit var sharedPref: SharedPref

    // UI components
    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var passwordInputLayout: TextInputLayout
    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var signupButton: MaterialButton
    private lateinit var forgotPasswordButton: MaterialButton
    private lateinit var progressIndicator: CircularProgressIndicator

    // 🔧 Toggle for development bypass
    private val bypassLoginForDebug = false // Set to true only during testing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sharedPref = SharedPref.getInstance(this)
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        initializeViews()
        setupClickListeners()
        observeViewModel()

        // If user already logged in, go to dashboard
        if (sharedPref.isLoggedIn() || authViewModel.isLoggedIn()) {
            navigateToDashboard()
            return
        }

        // 🧩 Dev bypass (useful during local testing)
        if (bypassLoginForDebug) {
            sharedPref.setLoggedIn(true)
            Toast.makeText(this, "🛡️ Login bypassed (Dev Mode)", Toast.LENGTH_SHORT).show()
            navigateToDashboard()
        }
    }

    // ------------------------------------------------------
    // 🧠 Initialize UI
    // ------------------------------------------------------
    private fun initializeViews() {
        emailInputLayout = findViewById(R.id.email_input_layout)
        passwordInputLayout = findViewById(R.id.password_input_layout)
        emailEditText = findViewById(R.id.email_edit_text)
        passwordEditText = findViewById(R.id.password_edit_text)
        loginButton = findViewById(R.id.login_button)
        signupButton = findViewById(R.id.signup_button)
        forgotPasswordButton = findViewById(R.id.forgot_password_button)
        progressIndicator = findViewById(R.id.progress_indicator)
    }

    private fun setupClickListeners() {
        loginButton.setOnClickListener { attemptLogin() }

        signupButton.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        forgotPasswordButton.setOnClickListener {
            Toast.makeText(this, "Forgot password feature coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------
    // 🔄 Observe ViewModel (Backend Connectivity)
    // ------------------------------------------------------
    private fun observeViewModel() {
        authViewModel.loginState.observe(this) { loginState ->
            when {
                loginState.isLoading -> showLoading(true)
                loginState.isSuccess -> {
                    showLoading(false)
                    sharedPref.setLoggedIn(true)
                    sharedPref.setUserEmail(emailEditText.text.toString().trim())
                    Toast.makeText(this, loginState.message ?: "Login successful!", Toast.LENGTH_SHORT).show()
                    navigateToDashboard()
                }
                loginState.isError -> {
                    showLoading(false)
                    showError(loginState.error ?: "Login failed")
                }
            }
        }

        authViewModel.isLoading.observe(this) { isLoading ->
            showLoading(isLoading)
        }

        authViewModel.errorMessage.observe(this) { error ->
            if (error.isNotEmpty()) showError(error)
        }
    }

    // ------------------------------------------------------
    // 🔐 Attempt Login (with Validation)
    // ------------------------------------------------------
    private fun attemptLogin() {
        emailInputLayout.error = null
        passwordInputLayout.error = null
        authViewModel.clearError()

        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (!validateInput(email, password)) return

        showLoading(true)
        authViewModel.login(email, password)
    }

    private fun validateInput(email: String, password: String): Boolean {
        var isValid = true

        if (TextUtils.isEmpty(email)) {
            emailInputLayout.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.error = "Enter a valid email"
            isValid = false
        }

        if (TextUtils.isEmpty(password)) {
            passwordInputLayout.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            passwordInputLayout.error = "Password must be at least 6 characters"
            isValid = false
        }

        return isValid
    }

    // ------------------------------------------------------
    // ⏳ Loading + Error UI
    // ------------------------------------------------------
    private fun showLoading(isLoading: Boolean) {
        progressIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !isLoading
        loginButton.text = if (isLoading) "Signing In..." else "Sign In"
    }

    private fun showError(error: String) {
        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
    }

    // ------------------------------------------------------
    // 🚀 Navigation
    // ------------------------------------------------------
    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }
}
