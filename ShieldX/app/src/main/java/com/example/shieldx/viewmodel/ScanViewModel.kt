package com.example.shieldx.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.shieldx.models.*
import com.example.shieldx.network.ApiClient
import com.example.shieldx.repository.ScanRepository
import com.example.shieldx.utils.SharedPref
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DeepGuard v3.1 - Scan ViewModel
 * Handles file, text, and deepfake scanning operations with unified state handling.
 */
class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val scanRepository = ScanRepository(application, ApiClient.getApiService())
    private val sharedPref = SharedPref.getInstance(application)

    // =======================
    // LiveData Observables
    // =======================
    private val _scanState = MutableLiveData(ScanState())
    val scanState: LiveData<ScanState> = _scanState

    private val _scanResults = MutableLiveData<List<ScanResult>>(emptyList())
    val scanResults: LiveData<List<ScanResult>> = _scanResults

    private val _currentScanResult = MutableLiveData<ScanResult?>()
    val currentScanResult: LiveData<ScanResult?> = _currentScanResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _uploadProgress = MutableLiveData(0)
    val uploadProgress: LiveData<Int> = _uploadProgress

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _monitoringStats = MutableLiveData<MonitoringStats>()
    val monitoringStats: LiveData<MonitoringStats> = _monitoringStats

    private val _recentAlerts = MutableLiveData<List<Alert>>(emptyList())
    val recentAlerts: LiveData<List<Alert>> = _recentAlerts

    init {
        loadUserScans()
        loadMonitoringStats()
    }

    // =======================
    // TEXT SCAN
    // =======================
    fun scanText(text: String) {
        if (text.isBlank()) {
            _errorMessage.value = "Please enter text to scan"
            return
        }

        viewModelScope.launch {
            startScan(progress = 25)
            try {
                val result = scanRepository.scanText(text)
                handleScanResult(result, "Text scan failed")
            } catch (e: Exception) {
                handleError("Unexpected error during text scan", e)
            } finally {
                finishScan()
            }
        }
    }

    // =======================
    // MEDIA SCAN
    // =======================
    fun scanMedia(fileBytes: ByteArray, fileName: String, mimeType: String) {
        viewModelScope.launch {
            startScan()
            try {
                updateProgress(25)
                val result = scanRepository.scanMedia(fileBytes, fileName, mimeType)
                updateProgress(80)
                handleScanResult(result, "Media scan failed")
            } catch (e: Exception) {
                handleError("Unexpected error during media scan", e)
            } finally {
                finishScan()
            }
        }
    }

    // =======================
    // DEEPFAKE SCAN
    // =======================
    fun scanDeepfake(fileBytes: ByteArray, fileName: String, mimeType: String) {
        viewModelScope.launch {
            startScan()
            try {
                updateProgress(30)
                val result = scanRepository.scanDeepfake(fileBytes, fileName, mimeType)
                updateProgress(90)
                handleScanResult(result, "Deepfake scan failed")
            } catch (e: Exception) {
                handleError("Unexpected error during deepfake scan", e)
            } finally {
                finishScan()
            }
        }
    }

    // =======================
    // DEEP SCAN SIMULATION
    // =======================
    fun startDeepScan() {
        viewModelScope.launch {
            startScan()
            try {
                for (i in 1..10) {
                    updateProgress(i * 10)
                    delay(400)
                }
                val result = scanRepository.startDeepScan()
                handleScanResult(result, "Deep scan failed")
            } catch (e: Exception) {
                handleError("Unexpected error during deep scan", e)
            } finally {
                finishScan()
            }
        }
    }

    // =======================
    // FILE SCAN
    // =======================
    fun scanFile(uri: Uri, context: Context) {
        viewModelScope.launch {
            startScan()
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileBytes = inputStream?.readBytes()
                inputStream?.close()

                if (fileBytes == null) throw Exception("Failed to read file")

                val fileName = uri.lastPathSegment ?: "unknown_file"
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                updateProgress(25)
                val result = scanRepository.scanDeepfake(fileBytes, fileName, mimeType)
                handleScanResult(result, "File scan failed")
            } catch (e: Exception) {
                handleError("File scan error", e)
            } finally {
                finishScan()
            }
        }
    }

    // =======================
    // SCAN HISTORY
    // =======================
    fun loadUserScans(limit: Int = 20, offset: Int = 0) {
        viewModelScope.launch {
            try {
                val result = scanRepository.getUserScans(limit, offset)
                result.fold(
                    onSuccess = { _scanResults.value = it },
                    onFailure = { e ->
                        _errorMessage.value = e.message ?: "Failed to load scan history"
                    }
                )
            } catch (e: Exception) {
                handleError("Failed to load scan history", e)
            }
        }
    }

    fun deleteScan(scanId: String) {
        viewModelScope.launch {
            try {
                val result = scanRepository.deleteScan(scanId)
                result.fold(
                    onSuccess = { loadUserScans() },
                    onFailure = { e ->
                        _errorMessage.value = e.message ?: "Failed to delete scan"
                    }
                )
            } catch (e: Exception) {
                handleError("Failed to delete scan", e)
            }
        }
    }

    // =======================
    // ALERTS & STATS
    // =======================
    fun loadRecentAlerts() {
        viewModelScope.launch {
            try {
                _recentAlerts.value = listOf(
                    Alert(
                        id = "1",
                        title = "Harassment Detected",
                        message = "Harassment detected in WhatsApp chat",
                        appName = "WhatsApp",
                        threatType = "harassment",
                        confidence = 85,
                        timestamp = System.currentTimeMillis(),
                        isBlocked = true
                    ),
                    Alert(
                        id = "2",
                        title = "Deepfake Alert",
                        message = "Possible deepfake detected on Instagram",
                        appName = "Instagram",
                        threatType = "deepfake",
                        confidence = 78,
                        timestamp = System.currentTimeMillis() - 3_600_000,
                        isBlocked = false
                    )
                )
            } catch (e: Exception) {
                handleError("Failed to load recent alerts", e)
            }
        }
    }

    fun loadMonitoringStats() {
        viewModelScope.launch {
            try {
                val stats = MonitoringStats(
                    notificationsScanned = sharedPref.getIntValue("notifications_scanned", 0),
                    threatsBlocked = sharedPref.getIntValue("threats_blocked", 0),
                    warningsSent = sharedPref.getIntValue("warnings_sent", 0)
                )
                _monitoringStats.value = stats
            } catch (e: Exception) {
                handleError("Failed to load monitoring stats", e)
            }
        }
    }

    // =======================
    // STATE HELPERS
    // =======================
    private fun startScan(progress: Int = 0) {
        _isLoading.value = true
        _scanState.value = ScanState(isScanning = true, progress = progress)
        _uploadProgress.value = progress
    }

    private fun updateProgress(value: Int) {
        _uploadProgress.value = value
        _scanState.value = _scanState.value?.copy(progress = value)
    }

    private fun finishScan() {
        _isLoading.value = false
    }

    private fun handleScanResult(result: Result<ScanResult>, defaultError: String) {
        result.fold(
            onSuccess = { scanResult ->
                updateProgress(100)
                _currentScanResult.value = scanResult
                _scanState.value = ScanState(isScanning = false, progress = 100, result = scanResult, isComplete = true)
                _errorMessage.value = ""
                loadUserScans()
            },
            onFailure = { e ->
                _scanState.value = ScanState(isScanning = false, isError = true, error = e.message ?: defaultError)
                _errorMessage.value = e.message ?: defaultError
            }
        )
    }

    private fun handleError(prefix: String, e: Exception) {
        val msg = "$prefix: ${e.message ?: "Unknown error"}"
        _errorMessage.value = msg
        _scanState.value = ScanState(isError = true, error = msg)
    }

    fun clearCurrentScanResult() {
        _currentScanResult.value = null
        _scanState.value = ScanState()
    }

    fun clearError() {
        _errorMessage.value = ""
    }

    fun resetScanState() {
        _scanState.value = ScanState()
        _uploadProgress.value = 0
    }
}

/**
 * Unified scan state model for all scan operations.
 */
data class ScanState(
    val isScanning: Boolean = false,
    val progress: Int = 0,
    val result: ScanResult? = null,
    val isComplete: Boolean = false,
    val isError: Boolean = false,
    val error: String = ""
)
