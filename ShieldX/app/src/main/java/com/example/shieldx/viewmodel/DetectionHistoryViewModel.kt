package com.example.shieldx.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shieldx.models.Detection
import kotlinx.coroutines.launch

class DetectionHistoryViewModel : ViewModel() {
    private val _detections = MutableLiveData<List<Detection>>()
    val detections: LiveData<List<Detection>> = _detections

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadDetections() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                // TODO: Load detections from repository
                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }
}