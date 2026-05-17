package com.example.ue5analyzer.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ue5analyzer.util.FormatUtils

/**
 * Extended Pie Chart Color Palette (15 colors)
 */
private val chartColors = listOf(
    Color(0xFF4FC3F7),  // Blue
    Color(0xFF81C784),  // Green
    Color(0xFFFFB74D),  // Orange
    Color(0xFFE57373),  // Red
    Color(0xFFBA68C8),  // Purple
    Color(0xFF4DD0E1),  // Cyan
    Color(0xFFFFD54F),  // Yellow
    Color(0xFFA1887F),  // Brown
    Color(0xFF90A4AE),  // Gray
    Color(0xFFF06292),  // Pink
    Color(0xFF7986CB),  // Indigo
    Color(0xFF4DB6AC),  // Cyan2
    Color(0xFFFFD180),  // Light orange
    Color(0xFFAED581),  // Light green
    Color(0xFFB39DDB)   // Light purple
)

/**
 * Circular Progress Bar - For Health Score Display
 * Optimization: Use animateFloatAsState directly, no need for LaunchedEffect
 */
@Composable
fun CircularProgressBar(
    percentage: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    animationDuration: Int = 1000,
    color: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    // Use animateFloatAsState directly, animation triggers automatically when targetValue changes
    val currentPercentage by animateFloatAsState(
        targetValue = percentage,
        animationSpec = tween(durationMillis = animationDuration),
        label = "progress"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sweepAngle = (currentPercentage / 100) * 360f
            val stroke = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round
            )
            val diameter = size.minDimension - strokeWidth.toPx()
            val topLeft = Offset(
                (size.width - diameter) / 2,
                (size.height - diameter) / 2
            )

            // Background circle
            drawArc(
                color = backgroundColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = stroke
            )

            // Progress circle
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = stroke
            )
        }

        Text(
            text = "${currentPercentage.toInt()}%",
            style = MaterialTheme.typography.headlineMedium,
            color = color
        )
    }
}

/**
 * Pie Chart - Asset Type Distribution (Improved Version)
 * Types with less than 5% share are merged into "Other"
 * Optimization: Use constants instead of magic numbers, use animateFloatAsState directly
 */
private const val CHART_THRESHOLD_PERCENT = 0.05f  // 5% threshold
private const val PIE_CHART_SIZE = 150            // Pie chart size (dp)
private const val LEGEND_MAX_ITEMS = 6            // Maximum legend items

@Composable
fun PieChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    animationDuration: Int = 1000
) {
    if (data.isEmpty()) return

    val total = data.sumOf { it.second }.toFloat()
    
    // Preprocess data: merge types with less than 5% into "Other"
    val processedData = remember(data) {
        val sorted = data.sortedByDescending { it.second }
        val mainItems = mutableListOf<Pair<String, Int>>()
        val otherItems = mutableListOf<Pair<String, Int>>()
        
        sorted.forEach { item ->
            val ratio = item.second / total
            if (ratio >= CHART_THRESHOLD_PERCENT) {
                mainItems.add(item)
            } else {
                otherItems.add(item)
            }
        }
        
        if (otherItems.isNotEmpty()) {
            val otherSum = otherItems.sumOf { it.second }
            mainItems.add("Other (${otherItems.size} types)" to otherSum)
        }
        
        mainItems
    }

    // Use animateFloatAsState directly, simplify animation state management
    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = animationDuration),
        label = "pie"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pie chart
        Canvas(
            modifier = Modifier
                .size(PIE_CHART_SIZE.dp)
                .padding(8.dp)
        ) {
            var startAngle = -90f
            processedData.forEachIndexed { index, (_, value) ->
                val sweepAngle = animationProgress * (value / total) * 360f
                drawArc(
                    color = chartColors[index % chartColors.size],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    size = size
                )
                startAngle += sweepAngle
            }
        }

        // Legend
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            processedData.take(LEGEND_MAX_ITEMS).forEachIndexed { index, (label, value) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(color = chartColors[index % chartColors.size])
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (processedData.size > LEGEND_MAX_ITEMS) {
                Text(
                    text = "... ${processedData.size} types in total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Bar Chart - Top 10 Assets by Size
 */
@Composable
fun BarChart(
    data: List<Pair<String, Long>>,
    modifier: Modifier = Modifier,
    maxValue: Long = data.maxOfOrNull { it.second } ?: 1L,
    barColor: Color = MaterialTheme.colorScheme.primary,
    animationDuration: Int = 1000
) {
    if (data.isEmpty()) return

    // Use animateFloatAsState directly, simplify animation state management
    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = animationDuration),
        label = "bar"
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        data.take(10).forEachIndexed { index, (label, value) ->
            val progress = animationProgress * value.toFloat() / maxValue
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = label.take(15),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(80.dp),
                    maxLines = 1
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Background
                        drawRoundRect(
                            color = Color.Gray.copy(alpha = 0.2f),
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                        )
                        // Progress
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.7f + (index * 0.03f).coerceAtMost(0.3f)),
                            size = Size(size.width * progress, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                        )
                    }
                }

                Text(
                    text = FormatUtils.formatFileSize(value),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(60.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/**
 * Statistics Card
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            icon?.invoke()
            if (icon != null) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
