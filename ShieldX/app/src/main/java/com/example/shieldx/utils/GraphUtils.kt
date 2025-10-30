package com.example.shieldx.utils

import android.graphics.Color
import androidx.annotation.ColorInt

/**
 * DeepGuard v3.2 - Graph Utilities
 * --------------------------------
 * 🔹 Provides dynamic color & text mapping for charts and dashboards.
 * 🔹 Includes dark mode variants, gradients, and smart number formatting.
 * 🔹 Used in analytics and real-time dashboard visualization.
 */
object GraphUtils {

    // ============================
    // 🔹 Safety & Risk Calculations
    // ============================

    /**
     * Get color based on safety score (0–100)
     */
    @ColorInt
    fun getSafetyScoreColor(score: Double, darkMode: Boolean = false): Int {
        return when {
            score >= 80 -> if (darkMode) Color.parseColor("#81C784") else Color.parseColor("#4CAF50") // Green
            score >= 60 -> if (darkMode) Color.parseColor("#FFD54F") else Color.parseColor("#FFC107") // Yellow
            score >= 40 -> if (darkMode) Color.parseColor("#FFB74D") else Color.parseColor("#FF9800") // Orange
            else -> if (darkMode) Color.parseColor("#E57373") else Color.parseColor("#F44336")        // Red
        }
    }

    /**
     * Get textual description of risk level
     */
    fun getRiskLevelText(score: Double): String {
        return when {
            score >= 80 -> "Low Risk"
            score >= 60 -> "Medium Risk"
            score >= 40 -> "High Risk"
            else -> "Critical Risk"
        }
    }

    /**
     * Combine percentage string with matching color
     */
    fun formatPercentageWithColor(value: Double, darkMode: Boolean = false): Pair<String, Int> {
        val percentage = String.format("%.0f%%", value)
        val color = getSafetyScoreColor(value, darkMode)
        return Pair(percentage, color)
    }

    // ============================
    // 🔹 Chart & Visualization Helpers
    // ============================

    /**
     * Get consistent chart color palette
     */
    fun getChartColors(darkMode: Boolean = false): IntArray {
        return if (darkMode) {
            intArrayOf(
                Color.parseColor("#90CAF9"), // Light Blue
                Color.parseColor("#A5D6A7"), // Light Green
                Color.parseColor("#FFCC80"), // Light Orange
                Color.parseColor("#EF9A9A"), // Light Red
                Color.parseColor("#CE93D8"), // Light Purple
                Color.parseColor("#80DEEA"), // Light Cyan
                Color.parseColor("#FFF59D"), // Light Yellow
                Color.parseColor("#BCAAA4")  // Light Brown
            )
        } else {
            intArrayOf(
                Color.parseColor("#2196F3"), // Blue
                Color.parseColor("#4CAF50"), // Green
                Color.parseColor("#FF9800"), // Orange
                Color.parseColor("#F44336"), // Red
                Color.parseColor("#9C27B0"), // Purple
                Color.parseColor("#00BCD4"), // Cyan
                Color.parseColor("#FFEB3B"), // Yellow
                Color.parseColor("#795548")  // Brown
            )
        }
    }

    /**
     * Gradient colors for score-based UI components
     */
    fun getSafetyGradientColors(score: Double, darkMode: Boolean = false): IntArray {
        return when {
            score >= 80 -> intArrayOf(
                if (darkMode) Color.parseColor("#66BB6A") else Color.parseColor("#4CAF50"),
                if (darkMode) Color.parseColor("#A5D6A7") else Color.parseColor("#8BC34A")
            )
            score >= 60 -> intArrayOf(
                if (darkMode) Color.parseColor("#FFCA28") else Color.parseColor("#FFC107"),
                if (darkMode) Color.parseColor("#FFF176") else Color.parseColor("#FFEB3B")
            )
            score >= 40 -> intArrayOf(
                if (darkMode) Color.parseColor("#FB8C00") else Color.parseColor("#FF9800"),
                if (darkMode) Color.parseColor("#FFB74D") else Color.parseColor("#FFB74D")
            )
            else -> intArrayOf(
                if (darkMode) Color.parseColor("#E53935") else Color.parseColor("#F44336"),
                if (darkMode) Color.parseColor("#EF9A9A") else Color.parseColor("#EF5350")
            )
        }
    }

    // ============================
    // 🔹 Numeric Formatting
    // ============================

    /**
     * Format large numbers with K/M suffix for compact chart labels
     */
    fun formatNumber(number: Int): String {
        return when {
            number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000.0)
            number >= 1_000 -> String.format("%.1fK", number / 1_000.0)
            else -> number.toString()
        }
    }

    /**
     * Convert double value to percentage text (e.g., 0.85 → "85%")
     */
    fun toPercentText(value: Double): String {
        return String.format("%.0f%%", value * 100)
    }

    /**
     * Map risk level to a short indicator for UI chips (used in dashboards)
     */
    fun getRiskLevelIndicator(score: Double): String {
        return when {
            score >= 80 -> "🟢"
            score >= 60 -> "🟡"
            score >= 40 -> "🟠"
            else -> "🔴"
        }
    }
}
