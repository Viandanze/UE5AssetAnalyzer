package com.example.ue5analyzer.util

import org.junit.Assert.*
import org.junit.Test
import java.util.*

/**
 * FormatUtils 单元测试
 */
class FormatUtilsTest {

    // ========== formatFileSize 测试 ==========

    @Test
    fun `formatFileSize zeroBytes returnsCorrectFormat`() {
        val result = FormatUtils.formatFileSize(0)
        assertEquals("0 B", result)
    }

    @Test
    fun `formatFileSize lessThan1024Bytes returnsBytes`() {
        val result = FormatUtils.formatFileSize(512)
        assertEquals("512 B", result)
    }

    @Test
    fun `formatFileSize exactly1024Bytes returnsKB`() {
        val result = FormatUtils.formatFileSize(1024)
        assertEquals("1 KB", result)
    }

    @Test
    fun `formatFileSize 1536Bytes returnsCorrectKB`() {
        val result = FormatUtils.formatFileSize(1536)
        assertEquals("1 KB", result)
    }

    @Test
    fun `formatFileSize 1048576Bytes returnsMB`() {
        val result = FormatUtils.formatFileSize(1048576)
        assertEquals("1.0 MB", result)
    }

    @Test
    fun `formatFileSize 1572864Bytes returnsCorrectMB`() {
        val result = FormatUtils.formatFileSize(1572864)
        assertEquals("1.5 MB", result)
    }

    @Test
    fun `formatFileSize 1073741824Bytes returnsGB`() {
        val result = FormatUtils.formatFileSize(1073741824)
        assertEquals("1.0 GB", result)
    }

    @Test
    fun `formatFileSize largeFile returnsGB`() {
        val result = FormatUtils.formatFileSize(5368709120L) // 5 GB
        assertEquals("5.0 GB", result)
    }

    @Test
    fun `formatFileSize boundaryAtKB MB border`() {
        // 边界值: 1023 B -> B 格式
        assertEquals("1023 B", FormatUtils.formatFileSize(1023))
        // 边界值: 1024 B -> KB 格式
        assertEquals("1 KB", FormatUtils.formatFileSize(1024))
    }

    @Test
    fun `formatFileSize boundaryAtMB GB border`() {
        // 边界值: 1048575 B -> KB 格式
        // 1048575 / 1024 = 1023.999... -> "1024.0 KB"
        assertEquals("1024.0 KB", FormatUtils.formatFileSize(1048575))
        // 边界值: 1048576 B -> MB 格式
        assertEquals("1.0 MB", FormatUtils.formatFileSize(1048576))
    }

    @Test
    fun `formatFileSize boundaryAtGB border`() {
        // 边界值: 1073741823 B -> MB 格式
        // 1073741823 / (1024*1024) = 1023.999... -> "1024.0 MB"
        assertEquals("1024.0 MB", FormatUtils.formatFileSize(1073741823))
        // 边界值: 1073741824 B -> GB 格式
        assertEquals("1.0 GB", FormatUtils.formatFileSize(1073741824))
    }

    @Test
    fun `formatFileSize negativeValue handled`() {
        // 测试负数（可能不应该出现，但测试边界情况）
        val result = FormatUtils.formatFileSize(-100)
        assertEquals("-100 B", result)
    }

    // ========== formatTimestamp 测试 ==========

    @Test
    fun `formatTimestamp defaultPattern returnsCorrectFormat`() {
        val timestamp = 1704067200000L // 2024-01-01 00:00:00 UTC
        
        val result = FormatUtils.formatTimestamp(timestamp)
        
        // 验证格式为 yyyy-MM-dd HH:mm:ss
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun `formatTimestamp customPattern returnsCorrectFormat`() {
        val timestamp = 1704067200000L
        
        val result = FormatUtils.formatTimestamp(timestamp, "yyyy/MM/dd")
        
        assertTrue(result.matches(Regex("\\d{4}/\\d{2}/\\d{2}")))
        assertTrue(result.contains("2024"))
    }

    @Test
    fun `formatTimestamp dateOnlyPattern`() {
        val timestamp = 1704067200000L
        
        val result = FormatUtils.formatTimestamp(timestamp, "yyyy-MM-dd")
        
        assertEquals("2024-01-01", result)
    }

    @Test
    fun `formatTimestamp timeOnlyPattern`() {
        val timestamp = 1704067200000L
        
        val result = FormatUtils.formatTimestamp(timestamp, "HH:mm:ss")
        
        assertTrue(result.matches(Regex("\\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun `formatTimestamp differentTimestamps differentResults`() {
        val timestamp1 = 1704067200000L // 2024-01-01
        val timestamp2 = 1704153600000L // 2024-01-02
        
        val result1 = FormatUtils.formatTimestamp(timestamp1)
        val result2 = FormatUtils.formatTimestamp(timestamp2)
        
        assertNotEquals(result1, result2)
    }

    @Test
    fun `formatTimestamp zeroTimestamp handled`() {
        val result = FormatUtils.formatTimestamp(0)
        
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `formatTimestamp negativeTimestamp handled`() {
        val result = FormatUtils.formatTimestamp(-1)
        
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    // ========== formatRelativeTime 测试 ==========

    @Test
    fun `formatRelativeTime justNow returnsJustNow`() {
        val now = System.currentTimeMillis()
        val result = FormatUtils.formatRelativeTime(now)
        
        assertEquals("刚刚", result)
    }

    @Test
    fun `formatRelativeTime fewMinutesAgo returnsMinutes`() {
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
        val result = FormatUtils.formatRelativeTime(fiveMinutesAgo)
        
        assertEquals("5分钟前", result)
    }

    @Test
    fun `formatRelativeTime oneHourAgo returnsHours`() {
        val oneHourAgo = System.currentTimeMillis() - (1 * 60 * 60 * 1000)
        val result = FormatUtils.formatRelativeTime(oneHourAgo)
        
        assertEquals("1小时前", result)
    }

    @Test
    fun `formatRelativeTime multipleHoursAgo returnsHours`() {
        val threeHoursAgo = System.currentTimeMillis() - (3 * 60 * 60 * 1000)
        val result = FormatUtils.formatRelativeTime(threeHoursAgo)
        
        assertEquals("3小时前", result)
    }

    @Test
    fun `formatRelativeTime oneDayAgo returnsDays`() {
        val oneDayAgo = System.currentTimeMillis() - (1 * 24 * 60 * 60 * 1000)
        val result = FormatUtils.formatRelativeTime(oneDayAgo)
        
        assertEquals("1天前", result)
    }

    @Test
    fun `formatRelativeTime multipleDaysAgo returnsDays`() {
        val fiveDaysAgo = System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000)
        val result = FormatUtils.formatRelativeTime(fiveDaysAgo)
        
        assertEquals("5天前", result)
    }

    @Test
    fun `formatRelativeTime oneWeekAgo returnsDate`() {
        val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        val result = FormatUtils.formatRelativeTime(oneWeekAgo)
        
        // 超过7天返回日期格式
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun `formatRelativeTime longAgo returnsDate`() {
        val oneMonthAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        val result = FormatUtils.formatRelativeTime(oneMonthAgo)
        
        // 超过7天返回日期格式
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }
}
