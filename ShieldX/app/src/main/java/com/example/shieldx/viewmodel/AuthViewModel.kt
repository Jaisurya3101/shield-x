package com.example.shieldx.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.shieldx.models.User
import com.example.shieldx.network.ApiClient
import com.example.shieldx.repository.AuthRepository
import kotlinx.coroutines.launch

/**
 * DeepGuard v3.1 - Authentication ViewModel
 * Handles secure user authentication, session management, and user data refresh.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository(application, ApiClient.getApiService())

    // ==============================
    // LiveData for UI Observers
    // ==============================
    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    private val _signupState = MutableLiveData<SignupState>()
    val signupState: LiveData<SignupState> = _signupState

    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    init {
        if (authRepository.isLoggedIn()) {
            _currentUser.value = authRepository.getCachedUser()
            refreshUserData()
        }
    }

    // ===================================
    // LOGIN
    // ===================================
    fun login(username: String, password: String) {
        viewModelScope.launch {
            setLoading(true)
            _loginState.value = LoginState(isLoading = true)

            try {
                val result = authRepository.login(username, password)
                result.fold(
                    onSuccess = { response ->
                        _currentUser.value = response.user
                        _loginState.value = LoginState(
                            isSuccess = true,
                            user = response.user,
                            message = "Login successful"
                        )
                        _errorMessage.value = ""
                    },
                    onFailure = { e ->
                        handleError("Login failed", e, isLogin = true)
                    }
                )
            } catch (e: Exception) {
                handleError("Unexpected error during login", e, isLogin = true)
            } finally {
                setLoading(false)
            }
        }
    }

    // ===================================
    // SIGNUP
    // ===================================
    fun signup(username: String, email: String, password: String, fullName: String) {
        viewModelScope.launch {
            setLoading(true)
            _signupState.value = SignupState(isLoading = true)

            try {
                val result = authRepository.signup(username, email, password, fullName)
                result.fold(
                    onSuccess = { response ->
                        _currentUser.value = response.user
                        _signupState.value = SignupState(
                            isSuccess = true,
                            user = response.user,
                            message = "Account created successfully"
                        )
                        _errorMessage.value = ""
                    },
                    onFailure = { e ->
                        handleError("Signup failed", e, isSignup = true)
                    }
                )
            } catch (e: Exception) {
                handleError("Unexpected error during signup", e, isSignup = true)
            } finally {
                setLoading(false)
            }
        }
    }

    // ===================================
    // REFRESH USER DATA
    // ===================================
    fun refreshUserData() {
        viewModelScope.launch {
            try {
                val result = authRepository.getCurrentUser()
                result.fold(
                    onSuccess = { _currentUser.value = it },
                    onFailure = { refreshToken() } // Try refresh if expired
                )
            } catch (_: Exception) { /* Silent refresh */ }
        }
    }

    // ===================================
    // REFRESH TOKEN
    // ===================================
    private fun refreshToken() {
        viewModelScope.launch {
            try {
                val result = authRepository.refreshToken()
                result.fold(
                    onSuccess = { _currentUser.value = it.user },
                    onFailure = { logout() } // Logout if token refresh fails
                )
            } catch (_: Exception) {
                logout()
            }
        }
    }

    // ===================================
    // UPDATE PROFILE
    // ===================================
    fun updateProfile(user: User) {
        viewModelScope.launch {
            setLoading(true)
            try {
                val result = authRepository.updateProfile(user)
                result.fold(
                    onSuccess = {
                        _currentUser.value = it
                        _errorMessage.value = ""
                    },
                    onFailure = { e ->
                        _errorMessage.value = e.message ?: "Failed to update profile"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unexpected error updating profile"
            } finally {
                setLoading(false)
            }
        }
    }

    // ===================================
    // LOGOUT
    // ===================================
    fun logout() {
        authRepository.logout()
        _currentUser.value = null
        _loginState.value = LoginState()
        _signupState.value = SignupState()
        _errorMessage.value = ""
    }

    // ===================================
    // UTILITIES
    // ===================================
    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    fun clearError() {
        _errorMessage.value = ""
    }

    fun clearLoginState() {
        _loginState.value = LoginState()
    }

    fun clearSignupState() {
        _signupState.value = SignupState()
    }

    private fun setLoading(state: Boolean) = _isLoading.postValue(state)

    private fun handleError(
        prefix: String,
        e: Throwable,
        isLogin: Boolean = false,
        isSignup: Boolean = false
    ) {
        val msg = "$prefix: ${e.message ?: "Unknown error"}"
        _errorMessage.postValue(msg)
        if (isLogin) _loginState.postValue(LoginState(isError = true, error = msg))
        if (isSignup) _signupState.postValue(SignupState(isError = true, error = msg))
    }
}

/**
 * Represents login UI state.
 */
data class LoginState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val isError: Boolean = false,
    val user: User? = null,
    val message: String = "",
    val error: String = ""
)

/**
 * Represents signup UI state.
 */
data class SignupState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val isError: Boolean = false,
    val user: User? = null,
    val message: String = "",
    val error: String = ""
)
