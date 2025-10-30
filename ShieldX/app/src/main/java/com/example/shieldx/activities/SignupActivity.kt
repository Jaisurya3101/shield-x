package com.example.shieldx.activities

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.shieldx.R
import com.example.shieldx.utils.SharedPref
import com.example.shieldx.viewmodel.AuthViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.widget.ProgressBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DeepGuard v3.0 - Signup Activity (Updated)
 * Secure user registration with backend auto-sync and strong validation.
 */
class SignupActivity : AppCompatActivity() {

    private lateinit var authViewModel: AuthViewModel
    private lateinit var sharedPref: SharedPref

    // UI
    private lateinit var fullNameInputLayout: TextInputLayout
    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var passwordInputLayout: TextInputLayout
    private lateinit var confirmPasswordInputLayout: TextInputLayout

    private lateinit var fullNameEditText: TextInputEditText
    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var confirmPasswordEditText: TextInputEditText

    private lateinit var signupButton: MaterialButton
    private lateinit var loginNavText: TextView
    private lateinit var progressIndicator: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPref = SharedPref.getInstance(this)

        // ✅ Temporary bypass (for testing phase)
        if (true) {
            sharedPref.setLoggedIn(true)
            Toast.makeText(this, "🛡️ Signup bypassed - Launching ShieldX", Toast.LENGTH_SHORT).show()
            navigateToDashboard()
            return
        }

        // ✅ Normal flow
        setContentView(R.layout.activity_signup)
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        initializeViews()
        setupClickListeners()
        observeViewModel()
    }

    private fun initializeViews() {
        fullNameInputLayout = findViewById(R.id.tilFullName)
        emailInputLayout = findViewById(R.id.tilEmail)
        passwordInputLayout = findViewById(R.id.tilPassword)
        confirmPasswordInputLayout = findViewById(R.id.tilConfirmPassword)

        fullNameEditText = findViewById(R.id.etFullName)
        emailEditText = findViewById(R.id.etEmail)
        passwordEditText = findViewById(R.id.etPassword)
        confirmPasswordEditText = findViewById(R.id.etConfirmPassword)

        signupButton = findViewById(R.id.btnSignUp)
        loginNavText = findViewById(R.id.tvAlreadyHaveAccount)
        progressIndicator = findViewById(R.id.progressBar)
    }

    private fun setupClickListeners() {
        signupButton.setOnClickListener { attemptSignup() }

        loginNavText.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // Real-time password strength indicator (optional)
        passwordEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                s?.toString()?.let { text ->
                    val strength = getPasswordStrength(text)
                    passwordInputLayout.helperText = "Strength: $strength"
                }
            }
        })
    }

    private fun observeViewModel() {
        authViewModel.signupState.observe(this) { signupState ->
            when {
                signupState.isLoading -> showLoading(true)
                signupState.isSuccess -> {
                    showLoading(false)
                    Toast.makeText(this, signupState.message, Toast.LENGTH_SHORT).show()
                    sharedPref.setLoggedIn(true)
                    navigateToDashboard()
                }
                signupState.isError -> {
                    showLoading(false)
                    showError(signupState.error)
                }
            }
        }

        authViewModel.isLoading.observe(this) { showLoading(it) }

        authViewModel.errorMessage.observe(this) { error ->
            if (error.isNotEmpty()) showError(error)
        }
    }

    private fun attemptSignup() {
        clearErrors()
        authViewModel.clearError()

        val fullName = fullNameEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        val confirmPassword = confirmPasswordEditText.text.toString().trim()

        if (!validateInput(fullName, email, password, confirmPassword)) return

        // 🔄 Connect to backend
        lifecycleScope.launch {
            showLoading(true)
            try {
                authViewModel.signup(email, email, password, fullName)
                // Optionally delay to simulate backend confirmation
                delay(1000)
            } catch (e: Exception) {
                showError("Signup failed: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun validateInput(
        fullName: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        var valid = true

        if (fullName.isBlank()) {
            fullNameInputLayout.error = "Full name is required"
            valid = false
        } else if (fullName.length < 2) {
            fullNameInputLayout.error = "Full name too short"
            valid = false
        }

        if (email.isBlank()) {
            emailInputLayout.error = "Email is required"
            valid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.error = "Invalid email format"
            valid = false
        }

        if (password.isBlank()) {
            passwordInputLayout.error = "Password required"
            valid = false
        } else if (!isPasswordStrong(password)) {
            passwordInputLayout.error = "Must contain letters and numbers"
            valid = false
        }

        if (confirmPassword.isBlank()) {
            confirmPasswordInputLayout.error = "Confirm password"
            valid = false
        } else if (password != confirmPassword) {
            confirmPasswordInputLayout.error = "Passwords do not match"
            valid = false
        }

        return valid
    }

    private fun isPasswordStrong(password: String): Boolean {
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        return hasLetter && hasDigit
    }

    private fun getPasswordStrength(password: String): String {
        return when {
            password.length < 6 -> "Weak"
            password.length < 10 -> "Medium"
            password.any { it.isUpperCase() } && password.any { !it.isLetterOrDigit() } -> "Strong"
            else -> "Good"
        }
    }

    private fun clearErrors() {
        fullNameInputLayout.error = null
        emailInputLayout.error = null
        passwordInputLayout.error = null
        confirmPasswordInputLayout.error = null
    }

    private fun showLoading(isLoading: Boolean) {
        progressIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        signupButton.isEnabled = !isLoading
        signupButton.text = if (isLoading) "Creating Account..." else "Create Account"
    }

    private fun showError(error: String) {
        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
