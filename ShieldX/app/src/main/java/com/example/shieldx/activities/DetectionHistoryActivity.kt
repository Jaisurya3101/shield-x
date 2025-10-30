package com.example.shieldx.activities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.shieldx.adapters.DetectionAdapter
import com.example.shieldx.databinding.ActivityDetectionHistoryBinding
import com.example.shieldx.models.Detection
import com.example.shieldx.viewmodel.DetectionHistoryViewModel

class DetectionHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetectionHistoryBinding
    private lateinit var viewModel: DetectionHistoryViewModel
    private lateinit var adapter: DetectionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetectionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupRecyclerView()
        setupUI()
        loadDetections()
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[DetectionHistoryViewModel::class.java]
    }

    private fun setupRecyclerView() {
        adapter = DetectionAdapter(mutableListOf()) { detection ->
            // Show detection details dialog
            showDetectionDetails(detection)
        }

        binding.rvDetections.apply {
            layoutManager = LinearLayoutManager(this@DetectionHistoryActivity)
            adapter = this@DetectionHistoryActivity.adapter
        }
    }

    private fun setupUI() {
        binding.ivBack.setOnClickListener { finish() }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.detections.observe(this) { detections ->
            adapter.updateDetections(detections)
            binding.tvNoDetections.visibility = if (detections.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let { showError(it) }
        }
    }

    private fun loadDetections() {
        viewModel.loadDetections()
    }

    private fun showDetectionDetails(detection: Detection) {
        AlertDialog.Builder(this)
            .setTitle("Detection Details")
            .setMessage("""
                Type: ${detection.type}
                Confidence: ${detection.confidence}%
                Source: ${detection.source}
                Time: ${detection.timestamp}
                ${detection.details ?: ""}
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showError(error: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(error)
            .setPositiveButton("OK", null)
            .show()
    }
}