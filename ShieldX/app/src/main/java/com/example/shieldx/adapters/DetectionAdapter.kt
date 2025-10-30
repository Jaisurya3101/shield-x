package com.example.shieldx.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.shieldx.R
import com.example.shieldx.databinding.ItemDetectionBinding
import com.example.shieldx.models.Detection
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class DetectionAdapter(
    private val detections: MutableList<Detection>,
    private val onItemClick: (Detection) -> Unit
) : RecyclerView.Adapter<DetectionAdapter.DetectionViewHolder>() {

    fun updateDetections(newDetections: List<Detection>) {
        detections.clear()
        detections.addAll(newDetections)
        notifyDataSetChanged()
    }

    inner class DetectionViewHolder(private val binding: ItemDetectionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(detection: Detection) {
            binding.apply {
                tvDetectionType.text = detection.type.ifBlank { "Unknown Detection" }
                tvDetectionSource.text = detection.source.ifBlank { "Unknown Source" }

                // 🕒 Show both formatted and relative time
                val date = Date(detection.timestamp)
                val formattedTime = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(date)
                val relativeTime = getRelativeTime(detection.timestamp)
                tvDetectionTime.text = "$formattedTime • $relativeTime"

                tvDetectionConfidence.text = "${detection.confidence}%"

                // 🎨 Dynamic color for confidence
                val confidenceColor = when {
                    detection.confidence >= 80 -> R.color.danger_color
                    detection.confidence >= 60 -> R.color.warning_color
                    else -> R.color.success_color
                }
                tvDetectionConfidence.setTextColor(
                    ContextCompat.getColor(root.context, confidenceColor)
                )

                // 🧠 Dynamic icon + emoji
                val (iconRes, emoji) = when (detection.type.lowercase()) {
                    "harassment" -> R.drawable.ic_warning to "🚨"
                    "deepfake" -> R.drawable.ic_image to "🎭"
                    "spam" -> R.drawable.ic_spam to "📩"
                    "phishing" -> R.drawable.ic_shield to "⚠️"
                    else -> R.drawable.ic_shield to "🛡️"
                }

                ivDetectionIcon.setImageResource(iconRes)
                tvDetectionType.text = "$emoji ${detection.type}"

                // 💫 Fade-in animation for smooth appearance
                root.alpha = 0f
                root.animate().alpha(1f).setDuration(400).start()

                root.setOnClickListener { onItemClick(detection) }
            }
        }

        // Function to compute relative time
        private fun getRelativeTime(timestamp: Long): String {
            val diff = abs(System.currentTimeMillis() - timestamp)
            val minutes = diff / 60000
            val hours = minutes / 60
            val days = hours / 24

            return when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "$minutes min${if (minutes > 1) "s" else ""} ago"
                hours < 24 -> "$hours hr${if (hours > 1) "s" else ""} ago"
                else -> "$days day${if (days > 1) "s" else ""} ago"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetectionViewHolder {
        val binding = ItemDetectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DetectionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DetectionViewHolder, position: Int) {
        holder.bind(detections[position])
    }

    override fun getItemCount() = detections.size
}
