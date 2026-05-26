package com.example.ue5analyzer.domain.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.TextPaint
import com.example.ue5analyzer.model.AnalysisReport
import com.example.ue5analyzer.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF Exporter - generates styled PDF reports from analysis data
 */
class PdfExporter(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private var pageCount = 0

    // Page dimensions (A4-ish at 72dpi)
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40
    private val contentWidth = pageWidth - 2 * margin

    // Paints
    private val titlePaint = TextPaint().apply {
        color = Color.parseColor("#1A1A2E")
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val headingPaint = TextPaint().apply {
        color = Color.parseColor("#E94560")
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val bodyPaint = TextPaint().apply {
        color = Color.parseColor("#333333")
        textSize = 11f
        isAntiAlias = true
    }

    private val smallPaint = TextPaint().apply {
        color = Color.parseColor("#666666")
        textSize = 9f
        isAntiAlias = true
    }

    private val tableHeaderPaint = TextPaint().apply {
        color = Color.WHITE
        textSize = 10f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val tableBodyPaint = TextPaint().apply {
        color = Color.parseColor("#333333")
        textSize = 10f
        isAntiAlias = true
    }

    fun exportPdf(report: AnalysisReport, uri: Uri): Boolean {
        return try {
            val document = PdfDocument()
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            var y = margin.toFloat()
            pageCount = 1

            fun newPage() {
                document.finishPage(page)
                pageCount++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = margin.toFloat()
            }

            fun ensureSpace(needed: Float) {
                if (y + needed > pageHeight - margin) {
                    newPage()
                }
            }

            fun drawText(text: String, paint: TextPaint, x: Float = margin.toFloat()) {
                ensureSpace(paint.textSize + 4)
                canvas.drawText(text, x, y, paint)
                y += paint.textSize + 4
            }

            fun drawWrappedText(
                text: String,
                paint: TextPaint,
                maxWidth: Float = contentWidth.toFloat()
            ): Float {
                val lines = mutableListOf<String>()
                var currentLine = ""
                for (word in text.split(" ")) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (paint.measureText(testLine) > maxWidth) {
                        if (currentLine.isNotEmpty()) lines.add(currentLine)
                        currentLine = word
                    } else {
                        currentLine = testLine
                    }
                }
                if (currentLine.isNotEmpty()) lines.add(currentLine)

                for (line in lines) {
                    ensureSpace(paint.textSize + 2)
                    canvas.drawText(line, margin.toFloat(), y, paint)
                    y += paint.textSize + 2
                }
                return y
            }

            fun drawDivider() {
                ensureSpace(10f)
                canvas.drawLine(
                    margin.toFloat(),
                    y,
                    (pageWidth - margin).toFloat(),
                    y,
                    Paint().apply {
                        color = Color.parseColor("#DDDDDD")
                        strokeWidth = 1f
                    })
                y += 10f
            }

            fun drawTable(
                headers: List<String>,
                rows: List<List<String>>,
                colWidths: List<Float>? = null
            ) {
                val rowHeight = 22f
                val headerBgPaint = Paint().apply { color = Color.parseColor("#1A1A2E") }
                val altRowPaint = Paint().apply { color = Color.parseColor("#F5F5F5") }
                val widths =
                    colWidths ?: List(headers.size) { contentWidth.toFloat() / headers.size }
                val cellPadding = 6f

                // Header row
                ensureSpace(rowHeight + 4)
                canvas.drawRect(
                    margin.toFloat(),
                    y,
                    (margin + contentWidth).toFloat(),
                    y + rowHeight,
                    headerBgPaint
                )
                var x = margin.toFloat()
                headers.forEachIndexed { i, header ->
                    canvas.drawText(header, x + cellPadding, y + rowHeight - 6f, tableHeaderPaint)
                    x += widths[i]
                }
                y += rowHeight

                // Data rows
                rows.forEachIndexed { rowIdx, row ->
                    ensureSpace(rowHeight)
                    if (rowIdx % 2 == 1) {
                        canvas.drawRect(
                            margin.toFloat(),
                            y,
                            (margin + contentWidth).toFloat(),
                            y + rowHeight,
                            altRowPaint
                        )
                    }
                    x = margin.toFloat()
                    row.forEachIndexed { colIdx, cell ->
                        val displayCell =
                            if (cell.length > 25) cell.substring(0, 22) + "..." else cell
                        canvas.drawText(
                            displayCell,
                            x + cellPadding,
                            y + rowHeight - 6f,
                            tableBodyPaint
                        )
                        x += widths[colIdx]
                    }
                    y += rowHeight
                }
            }

            // === TITLE ===
            drawText("UE5 Project Asset Analysis Report", titlePaint)
            y += 8f
            drawText("Project: ${report.projectName}", headingPaint)
            drawText("Generated: ${dateFormat.format(Date(report.generatedAt))}", smallPaint)
            y += 8f
            drawDivider()

            // === OVERVIEW ===
            drawText("Overview", headingPaint)
            y += 4f
            drawTable(
                headers = listOf("Metric", "Value"),
                rows = listOf(
                    listOf("Total Assets", report.totalAssets.toString()),
                    listOf("Total Size", FormatUtils.formatFileSize(report.totalSize)),
                    listOf("Orphan Assets", report.orphanCount.toString()),
                    listOf("Health Score", "${report.healthScore}%")
                ),
                colWidths = listOf(contentWidth * 0.5f, contentWidth * 0.5f)
            )
            y += 12f
            drawDivider()

            // === ASSET TYPE DISTRIBUTION ===
            drawText("Asset Type Distribution", headingPaint)
            y += 4f
            val typeRows = report.assetsByType.entries
                .sortedByDescending { it.value }
                .map { (type, count) ->
                    val pct = "%.1f".format(count.toFloat() / report.totalAssets * 100)
                    listOf(type.displayName, count.toString(), "$pct%")
                }
            drawTable(
                headers = listOf("Type", "Count", "Percentage"),
                rows = typeRows,
                colWidths = listOf(contentWidth * 0.4f, contentWidth * 0.3f, contentWidth * 0.3f)
            )
            y += 12f
            drawDivider()

            // === LARGEST ASSETS ===
            drawText("Largest Assets TOP 10", headingPaint)
            y += 4f
            val largestRows = report.largestAssets.map { asset ->
                listOf(asset.name, asset.type.displayName, FormatUtils.formatFileSize(asset.size))
            }
            drawTable(
                headers = listOf("Asset Name", "Type", "Size"),
                rows = largestRows,
                colWidths = listOf(contentWidth * 0.45f, contentWidth * 0.25f, contentWidth * 0.3f)
            )
            y += 12f
            drawDivider()

            // === ORPHAN ASSETS ===
            if (report.orphanAssets.isNotEmpty()) {
                drawText("Orphan Assets (${report.orphanAssets.size})", headingPaint)
                y += 4f
                val orphanRows = report.orphanAssets.map { asset ->
                    listOf(
                        asset.name,
                        asset.type.displayName,
                        FormatUtils.formatFileSize(asset.size)
                    )
                }
                drawTable(
                    headers = listOf("Asset Name", "Type", "Size"),
                    rows = orphanRows,
                    colWidths = listOf(
                        contentWidth * 0.45f,
                        contentWidth * 0.25f,
                        contentWidth * 0.3f
                    )
                )
                y += 12f
                drawDivider()
            }

            // === CIRCULAR DEPENDENCIES ===
            drawText("Circular Dependencies", headingPaint)
            y += 4f
            if (report.circularDependencies.isEmpty()) {
                drawText("No circular dependencies detected", bodyPaint)
            } else {
                report.circularDependencies.forEach { cycle ->
                    val pathStr =
                        cycle.joinToString(" -> ") + " -> " + cycle.firstOrNull().orEmpty()
                    drawWrappedText(pathStr, smallPaint)
                }
            }
            y += 12f
            drawDivider()

            // === FOOTER ===
            drawText("Report generated by UE5 Asset Analyzer", smallPaint)

            document.finishPage(page)

            // Write to URI
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                document.writeTo(outputStream)
            }
            document.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
