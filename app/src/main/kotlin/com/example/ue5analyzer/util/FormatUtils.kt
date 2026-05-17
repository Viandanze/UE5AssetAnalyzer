package com.example.ue5analyzer.util

import java.text.SimpleDateFormat
import java.util.*

/**
 * Formatting Utilities
 * Centralized formatting functions used throughout the project
 */
object FormatUtils {
    
    /**
     * Format File Size
     * @param bytes File size in bytes
     * @return Formatted size string, e.g. "1.5 MB"
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
    
    /**
     * Format Timestamp
     * @param timestamp Timestamp in milliseconds
     * @param pattern Date format pattern, default is "yyyy-MM-dd HH:mm:ss"
     * @return Formatted time string
     */
    fun formatTimestamp(timestamp: Long, pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
        val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    
    /**
     * Format Relative Time
     * @param timestamp Timestamp in milliseconds
     * @return Relative time string, e.g. "5 minutes ago", "2 hours ago", "3 days ago"
     */
    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60 * 1000 -> "Just now"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} minutes ago"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours ago"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)} days ago"
            else -> formatTimestamp(timestamp, "yyyy-MM-dd")
        }
    }
}
