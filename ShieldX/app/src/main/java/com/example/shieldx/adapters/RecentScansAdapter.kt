package com.example.shieldx.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.shieldx.R
import com.example.shieldx.data.RecentScan
import com.google.android.material.imageview.ShapeableImageView

class RecentScansAdapter(
    private val onItemClick: (scanId: String) -> Unit
) : ListAdapter<RecentScan, RecentScansAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_scan, parent, false)
        return ViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        itemView: View,
        private val onItemClick: (scanId: String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivScanType: ShapeableImageView = itemView.findViewById(R.id.ivScanType)
        private val tvScanName: TextView = itemView.findViewById(R.id.tvScanName)
        private val tvScanTime: TextView = itemView.findViewById(R.id.tvScanTime)
        private val tvScanResult: TextView = itemView.findViewById(R.id.tvScanResult)

        fun bind(scan: RecentScan) {
            tvScanName.text = scan.name
            tvScanTime.text = scan.timestamp
            tvScanResult.text = scan.result

            // Set scan type icon
            ivScanType.setImageResource(
                when (scan.type) {
                    "DEEPFAKE" -> R.drawable.ic_scan_deepfake
                    "DEEPSCAN" -> R.drawable.ic_scan_deep
                    else -> R.drawable.ic_scan_quick
                }
            )

            // Set result color
            tvScanResult.setTextColor(
                itemView.context.getColor(
                    when (scan.result.lowercase()) {
                        "clean" -> R.color.success_color
                        "threat detected" -> R.color.danger_color
                        else -> R.color.gray_500
                    }
                )
            )

            itemView.setOnClickListener {
                onItemClick(scan.id)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RecentScan>() {
            override fun areItemsTheSame(oldItem: RecentScan, newItem: RecentScan): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: RecentScan, newItem: RecentScan): Boolean {
                return oldItem == newItem
            }
        }
    }
}