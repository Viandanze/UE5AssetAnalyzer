package com.example.ue5analyzer.domain.report

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.ue5analyzer.model.*
import com.example.ue5analyzer.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.*

/**
 * Report Generator
 */
class ReportGenerator(private val context: Context) {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    /**
     * Generate Markdown Report
     */
    fun generateMarkdownReport(report: AnalysisReport): String {
        return buildString {
            appendLine("# UE5 Project Asset Analysis Report")
            appendLine()
            appendLine("**Project Name**: ${report.projectName}")
            appendLine()
            appendLine("**Generated At**: ${dateFormat.format(Date(report.generatedAt))}")
            appendLine()
            appendLine("---")
            appendLine()
            
            // Overview
            appendLine("## Overview")
            appendLine()
            appendLine("| Metric | Value |")
            appendLine("|-----|------|")
            appendLine("| Total Assets | ${report.totalAssets} |")
            appendLine("| Total Size | ${FormatUtils.formatFileSize(report.totalSize)} |")
            appendLine("| Orphan Assets | ${report.orphanCount} |")
            appendLine("| Health Score | ${report.healthScore}% |")
            appendLine()
            
            // Asset Type Distribution
            appendLine("## Asset Type Distribution")
            appendLine()
            appendLine("| Type | Count | Percentage |")
            appendLine("|-----|------|------|")
            report.assetsByType.entries
                .sortedByDescending { it.value }
                .forEach { (type, count) ->
                    val percentage = count.toFloat() / report.totalAssets * 100
                    appendLine("| ${type.displayName} | $count | ${"%.1f".format(percentage)}% |")
                }
            appendLine()
            
            // Largest assets
            appendLine("## Largest Assets TOP 10")
            appendLine()
            appendLine("| Asset Name | Type | Size |")
            appendLine("|---------|------|------|")
            report.largestAssets.forEach { asset ->
                appendLine("| ${asset.name} | ${asset.type.displayName} | ${FormatUtils.formatFileSize(asset.size)} |")
            }
            appendLine()
            
            // Most referenced assets
            appendLine("## Most Referenced Assets TOP 10")
            appendLine()
            appendLine("| Asset Name | Type | References |")
            appendLine("|---------|------|--------|")
            report.mostReferenced.forEach { asset ->
                appendLine("| ${asset.name} | ${asset.type.displayName} | ${asset.references.size} |")
            }
            appendLine()
            
            // Orphan Assets
            if (report.orphanAssets.isNotEmpty()) {
                appendLine("## Orphan Assets")
                appendLine()
                appendLine("> The following assets are not referenced by any other assets and may be redundant resources")
                appendLine()
                appendLine("| Asset Name | Type | Size |")
                appendLine("|---------|------|------|")
                report.orphanAssets.forEach { asset ->
                    appendLine("| ${asset.name} | ${asset.type.displayName} | ${FormatUtils.formatFileSize(asset.size)} |")
                }
                appendLine()
            }
            
            // Circular Dependencies
            appendLine("## Circular Dependencies")
            appendLine()
            if (report.circularDependencies.isEmpty()) {
                appendLine("No circular dependencies detected ✅")
            } else {
                appendLine("> The following assets have circular reference relationships that may cause loading issues")
                appendLine()
                appendLine("| Circular Path |")
                appendLine("|---------|")
                report.circularDependencies.forEach { cycle ->
                    val pathStr = cycle.joinToString(" → ") + " → " + cycle.firstOrNull().orEmpty()
                    appendLine("| $pathStr |")
                }
            }
            appendLine()
            
            // Suggestions
            appendLine("## Optimization Suggestions")
            appendLine()
            if (report.orphanCount > 0) {
                appendLine("- Found ${report.orphanCount} orphan assets. Consider checking if they can be deleted.")
            }
            val largeAssets = report.largestAssets.filter { it.size > 10 * 1024 * 1024 }
            if (largeAssets.isNotEmpty()) {
                appendLine("- Found ${largeAssets.size} large assets over 10MB. Consider optimizing and compressing them.")
            }
            if (report.circularDependencies.isNotEmpty()) {
                appendLine("- Found ${report.circularDependencies.size} circular dependencies. Consider resolving them to avoid loading issues.")
            }
            appendLine()
            
            appendLine("---")
            appendLine()
            appendLine("*Report generated by UE5 Asset Analyzer*")
        }
    }
    
    /**
     * Export report to file
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
     * Share Report
     */
    fun shareReport(report: AnalysisReport): Intent {
        val content = generateMarkdownReport(report)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "UE5 Project Analysis Report - ${report.projectName}")
            putExtra(Intent.EXTRA_TEXT, content)
        }
        return Intent.createChooser(intent, "Share Report")
    }
}
