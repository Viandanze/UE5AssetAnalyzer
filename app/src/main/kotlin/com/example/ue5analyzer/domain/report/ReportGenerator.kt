package com.example.ue5analyzer.domain.report

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.ue5analyzer.model.*
import com.example.ue5analyzer.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.*

/**
 * 报告生成器
 */
class ReportGenerator(private val context: Context) {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    /**
     * 生成 Markdown 格式报告
     */
    fun generateMarkdownReport(report: AnalysisReport): String {
        return buildString {
            appendLine("# UE5 项目资源分析报告")
            appendLine()
            appendLine("**项目名称**: ${report.projectName}")
            appendLine()
            appendLine("**生成时间**: ${dateFormat.format(Date(report.generatedAt))}")
            appendLine()
            appendLine("---")
            appendLine()
            
            // 概览
            appendLine("## 概览")
            appendLine()
            appendLine("| 指标 | 数值 |")
            appendLine("|-----|------|")
            appendLine("| 总资源数 | ${report.totalAssets} |")
            appendLine("| 总大小 | ${FormatUtils.formatFileSize(report.totalSize)} |")
            appendLine("| 孤立资源 | ${report.orphanCount} |")
            appendLine("| 健康度 | ${report.healthScore}% |")
            appendLine()
            
            // 资源类型分布
            appendLine("## 资源类型分布")
            appendLine()
            appendLine("| 类型 | 数量 | 占比 |")
            appendLine("|-----|------|------|")
            report.assetsByType.entries
                .sortedByDescending { it.value }
                .forEach { (type, count) ->
                    val percentage = count.toFloat() / report.totalAssets * 100
                    appendLine("| ${type.displayName} | $count | ${"%.1f".format(percentage)}% |")
                }
            appendLine()
            
            // 最大的资源
            appendLine("## 最大的资源 TOP 10")
            appendLine()
            appendLine("| 资源名称 | 类型 | 大小 |")
            appendLine("|---------|------|------|")
            report.largestAssets.forEach { asset ->
                appendLine("| ${asset.name} | ${asset.type.displayName} | ${FormatUtils.formatFileSize(asset.size)} |")
            }
            appendLine()
            
            // 被引用最多的资源
            appendLine("## 被引用最多的资源 TOP 10")
            appendLine()
            appendLine("| 资源名称 | 类型 | 引用数 |")
            appendLine("|---------|------|--------|")
            report.mostReferenced.forEach { asset ->
                appendLine("| ${asset.name} | ${asset.type.displayName} | ${asset.references.size} |")
            }
            appendLine()
            
            // 孤立资源
            if (report.orphanAssets.isNotEmpty()) {
                appendLine("## 孤立资源")
                appendLine()
                appendLine("> 以下资源没有被任何其他资源引用，可能是冗余资源")
                appendLine()
                appendLine("| 资源名称 | 类型 | 大小 |")
                appendLine("|---------|------|------|")
                report.orphanAssets.forEach { asset ->
                    appendLine("| ${asset.name} | ${asset.type.displayName} | ${FormatUtils.formatFileSize(asset.size)} |")
                }
                appendLine()
            }
            
            // 循环依赖
            appendLine("## 循环依赖")
            appendLine()
            if (report.circularDependencies.isEmpty()) {
                appendLine("未检测到循环依赖 ✅")
            } else {
                appendLine("> 以下资源存在循环引用关系，可能导致加载问题")
                appendLine()
                appendLine("| 循环路径 |")
                appendLine("|---------|")
                report.circularDependencies.forEach { cycle ->
                    val pathStr = cycle.joinToString(" → ") + " → " + cycle.firstOrNull().orEmpty()
                    appendLine("| $pathStr |")
                }
            }
            appendLine()
            
            // 建议
            appendLine("## 优化建议")
            appendLine()
            if (report.orphanCount > 0) {
                appendLine("- 发现 ${report.orphanCount} 个孤立资源，建议检查是否可以删除")
            }
            val largeAssets = report.largestAssets.filter { it.size > 10 * 1024 * 1024 }
            if (largeAssets.isNotEmpty()) {
                appendLine("- 发现 ${largeAssets.size} 个超过 10MB 的大型资源，建议优化压缩")
            }
            if (report.circularDependencies.isNotEmpty()) {
                appendLine("- 发现 ${report.circularDependencies.size} 个循环依赖，建议解除以避免加载问题")
            }
            appendLine()
            
            appendLine("---")
            appendLine()
            appendLine("*报告由 UE5 Asset Analyzer 生成*")
        }
    }
    
    /**
     * 导出报告到文件
     */
    fun exportReport(report: AnalysisReport, uri: Uri): Boolean {
        return try {
            val content = generateMarkdownReport(report)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 分享报告
     */
    fun shareReport(report: AnalysisReport): Intent {
        val content = generateMarkdownReport(report)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "UE5 项目分析报告 - ${report.projectName}")
            putExtra(Intent.EXTRA_TEXT, content)
        }
        return Intent.createChooser(intent, "分享报告")
    }
}
