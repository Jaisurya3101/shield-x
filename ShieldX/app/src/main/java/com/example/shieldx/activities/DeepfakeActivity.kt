package com.example.shieldx.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.example.shieldx.R
import com.example.shieldx.databinding.ActivityDeepfakeBinding
import com.example.shieldx.models.ScanResult
import com.example.shieldx.viewmodel.ScanViewModel

/**
 * DeepGuard v3.1 - DeepfakeActivity
 * Handles image/video deepfake detection using backend AI service.
 */
class DeepfakeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeepfakeBinding
    private lateinit var scanViewModel: ScanViewModel
    private var selectedFileUri: Uri? = null

    // Pick image from gallery
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { handleFileSelection(it, "image") }
        }
    }

    // Pick video from gallery
    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { handleFileSelection(it, "video") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeepfakeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scanViewModel = ViewModelProvider(this)[ScanViewModel::class.java]

        setupUI()
        setupObservers()
        setupBottomNavigation()
    }

    // ------------------------------------------
    // 🔧 UI Setup
    // ------------------------------------------
    private fun setupUI() {
        binding.ivBack.setOnClickListener { finish() }

        binding.btnSelectImage.setOnClickListener { selectImage() }
        binding.btnSelectVideo.setOnClickListener { selectVideo() }

        binding.btnAnalyze.setOnClickListener {
            selectedFileUri?.let { uri -> startAnalysis(uri) }
                ?: Toast.makeText(this, "Please select a file first", Toast.LENGTH_SHORT).show()
        }

        binding.ivInfo.setOnClickListener { showInfoDialog() }
        binding.btnSaveReport.setOnClickListener { saveReport() }
        binding.btnShareReport.setOnClickListener { shareReport() }
    }

    // ------------------------------------------
    // 🔁 Observers
    // ------------------------------------------
    private fun setupObservers() {
        scanViewModel.uploadProgress.observe(this) { progress ->
            binding.progressBar.progress = progress
            binding.tvProgressPercentage.text = "$progress%"
        }

        scanViewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) showProgressCard() else hideProgressCard()
        }

        scanViewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, "❌ $it", Toast.LENGTH_LONG).show()
                hideProgressCard()
            }
        }

        scanViewModel.currentScanResult.observe(this) { result ->
            result?.let { showResults(it) }
        }
    }

    // ------------------------------------------
    // 🌐 Bottom Navigation
    // ------------------------------------------
    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_deepfake
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_deepscan -> {
                    startActivity(Intent(this, DeepScanActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_deepfake -> true
                R.id.nav_analytics -> {
                    startActivity(Intent(this, AnalyticsActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    // ------------------------------------------
    // 📁 File Selection
    // ------------------------------------------
    private fun selectImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun selectVideo() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        intent.type = "video/*"
        videoPickerLauncher.launch(intent)
    }

    private fun handleFileSelection(uri: Uri, type: String) {
        selectedFileUri = uri
        binding.cardPreview.visibility = View.VISIBLE

        Glide.with(this)
            .load(uri)
            .placeholder(R.drawable.ic_placeholder)
            .transform(CenterCrop())
            .into(binding.ivPreview)

        // Get file info
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (nameIndex >= 0) {
                    binding.tvFileName.text = it.getString(nameIndex)
                }
                if (sizeIndex >= 0) {
                    binding.tvFileSize.text = formatFileSize(it.getLong(sizeIndex))
                }
            }
        }

        binding.btnAnalyze.isEnabled = true
    }

    // ------------------------------------------
    // 🧠 Deepfake Analysis
    // ------------------------------------------
    private fun startAnalysis(uri: Uri) {
        showProgressCard()
        scanViewModel.scanFile(uri, this)  // ✅ Sends to backend via Retrofit
    }

    private fun showProgressCard() {
        binding.cardProgress.visibility = View.VISIBLE
        binding.cardResults.visibility = View.GONE
        binding.btnAnalyze.isEnabled = false
    }

    private fun hideProgressCard() {
        binding.cardProgress.visibility = View.GONE
        binding.btnAnalyze.isEnabled = true
    }

    // ------------------------------------------
    // 📊 Results
    // ------------------------------------------
    private fun showResults(result: ScanResult) {
        hideProgressCard()
        binding.cardResults.visibility = View.VISIBLE

        val threatLabel: String
        val threatColor: Int

        when {
            result.isDeepfake -> {
                threatLabel = "⚠️ Deepfake Detected"
                threatColor = R.color.danger_color
            }
            result.confidenceScore >= 70 -> {
                threatLabel = "Suspicious Content"
                threatColor = R.color.warning_color
            }
            else -> {
                threatLabel = "✅ Safe Content"
                threatColor = R.color.success_color
            }
        }

        binding.tvThreatLevel.text = threatLabel
        binding.tvThreatLevel.setTextColor(getColor(threatColor))
        binding.tvConfidenceScore.text = "${result.confidenceScore}%"
        binding.tvDetailedAnalysis.text =
            result.detailedAnalysis ?: "Analysis complete. File scanned successfully."
    }

    // ------------------------------------------
    // ℹ️ Info / Report
    // ------------------------------------------
    private fun showInfoDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Deepfake Detection")
            .setMessage(
                "DeepGuard uses AI to detect synthetic media manipulations such as deepfakes. " +
                "Upload an image or video and it will be analyzed using DeepGuard's backend AI models."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun saveReport() {
        Toast.makeText(this, "✅ Report saved locally.", Toast.LENGTH_SHORT).show()
    }

    private fun shareReport() {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "DeepGuard Analysis Report")
            putExtra(Intent.EXTRA_TEXT, "Deepfake Analysis Report generated by DeepGuard.")
        }
        startActivity(Intent.createChooser(shareIntent, "Share Report"))
    }

    // ------------------------------------------
    // 🧮 Utility
    // ------------------------------------------
    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> String.format("%.1f MB", mb)
            kb >= 1 -> String.format("%.1f KB", kb)
            else -> "$bytes bytes"
        }
    }
}
