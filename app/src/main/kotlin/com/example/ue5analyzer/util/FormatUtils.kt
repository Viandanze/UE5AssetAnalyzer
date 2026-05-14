package com.example.ue5analyzer.util

import java.text.SimpleDateFormat
import java.util.*

/**
 * 格式化工具类
 * 集中管理项目中重复使用的格式化函数
 */
object FormatUtils {
    
    /**
     * 格式化文件大小
     * @param bytes 文件大小（字节）
     * @return 格式化后的大小字符串，如 "1.5 MB"
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
     * 格式化时间戳
     * @param timestamp 时间戳（毫秒）
     * @param pattern 日期格式模式，默认为 "yyyy-MM-dd HH:mm:ss"
     * @return 格式化后的时间字符串
     */
    fun formatTimestamp(timestamp: Long, pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
        val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    
    /**
     * 格式化相对时间
     * @param timestamp 时间戳（毫秒）
     * @return 相对时间字符串，如 "5分钟前"、"2小时前"、"3天前"
     */
    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60 * 1000 -> "刚刚"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}天前"
            else -> formatTimestamp(timestamp, "yyyy-MM-dd")
        }
    }
}
