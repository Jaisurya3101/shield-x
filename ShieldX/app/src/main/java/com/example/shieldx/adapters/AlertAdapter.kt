package com.example.shieldx.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.shieldx.R
import com.example.shieldx.databinding.ItemAlertBinding
import com.example.shieldx.models.Alert
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class AlertAdapter(
    private val alerts: List<Alert>,
    private val onItemClick: (Alert) -> Unit
) : RecyclerView.Adapter<AlertAdapter.AlertViewHolder>() {

    inner class AlertViewHolder(private val binding: ItemAlertBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(alert: Alert) {
            binding.apply {
                tvAlertTitle.text = alert.title.ifBlank { "Threat Detected" }
                tvAlertMessage.text = alert.message.ifBlank {
                    "Suspicious content detected in ${alert.appName}."
                }

                // 🕒 Display both time and "x mins ago"
                val date = Date(alert.timestamp)
                val formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
                val relativeTime = getRelativeTime(alert.timestamp)
                tvAlertTime.text = "$formattedTime • $relativeTime"

                tvAppName.text = alert.appName
                tvThreatType.text = alert.threatType
                tvConfidence.text = "${alert.confidence}%"

                // 🎨 Dynamic color for confidence level
                val confidenceColor = when {
                    alert.confidence >= 80 -> R.color.danger_color
                    alert.confidence >= 60 -> R.color.warning_color
                    else -> R.color.success_color
                }
                tvConfidence.setTextColor(ContextCompat.getColor(root.context, confidenceColor))

                // ⚠️ Optional: icon color/emoji for threat type
                val iconEmoji = when {
                    alert.threatType.contains("Deepfake", true) -> "🎭"
                    alert.threatType.contains("Harassment", true) -> "🚨"
                    alert.threatType.contains("Phishing", true) -> "⚠️"
                    else -> "🛡️"
                }
                tvThreatType.text = "$iconEmoji ${alert.threatType}"

                // Add soft entry animation
                root.alpha = 0f
                root.animate().alpha(1f).setDuration(400).start()

                root.setOnClickListener { onItemClick(alert) }
            }
        }

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemAlertBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(alerts[position])
    }

    override fun getItemCount() = alerts.size
}
