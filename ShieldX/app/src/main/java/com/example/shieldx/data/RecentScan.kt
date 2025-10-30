package com.example.shieldx.data

/**
 * Data class representing a recent scan item in the dashboard
 */
data class RecentScan(
    val id: String,
    val name: String,
    val type: String,
    val timestamp: String,
    val result: String
)