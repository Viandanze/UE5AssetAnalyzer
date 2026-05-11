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
 * 扩展的饼图颜色列表（15种）
 */
private val chartColors = listOf(
    Color(0xFF4FC3F7),  // 蓝色
    Color(0xFF81C784),  // 绿色
    Color(0xFFFFB74D),  // 橙色
    Color(0xFFE57373),  // 红色
    Color(0xFFBA68C8),  // 紫色
    Color(0xFF4DD0E1),  // 青色
    Color(0xFFFFD54F),  // 黄色
    Color(0xFFA1887F),  // 棕色
    Color(0xFF90A4AE),  // 灰色
    Color(0xFFF06292),  // 粉色
    Color(0xFF7986CB),  // 靛蓝
    Color(0xFF4DB6AC),  // 青色2
    Color(0xFFFFD180),  // 浅橙色
    Color(0xFFAED581),  // 浅绿色
    Color(0xFFB39DDB)   // 浅紫色
)

/**
 * 环形进度条 - 用于健康度评分显示
 * 优化：直接使用 animateFloatAsState，无需 LaunchedEffect
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
    // 直接使用 animateFloatAsState，targetValue 变化时自动触发动画
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

            // 背景圆
            drawArc(
                color = backgroundColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = stroke
            )

            // 进度圆
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
 * 饼图 - 资源类型分布（改进版）
 * 占比小于5%的类型合并为"其他"
 * 优化：使用常量替代魔法数字，直接使用 animateFloatAsState
 */
private const val CHART_THRESHOLD_PERCENT = 0.05f  // 5%阈值
private const val PIE_CHART_SIZE = 150            // 饼图大小(dp)
private const val LEGEND_MAX_ITEMS = 6            // 图例最大显示项数

@Composable
fun PieChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    animationDuration: Int = 1000
) {
    if (data.isEmpty()) return

    val total = data.sumOf { it.second }.toFloat()
    
    // 预处理数据：将占比小于5%的类型合并为"其他"
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
            mainItems.add("其他(${otherItems.size}种)" to otherSum)
        }
        
        mainItems
    }

    // 直接使用 animateFloatAsState，简化动画状态管理
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
        // 饼图
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

        // 图例
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
                    text = "...共 ${processedData.size} 种类型",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 柱状图 - 资源大小TOP10
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

    // 直接使用 animateFloatAsState，简化动画状态管理
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
                        // 背景
                        drawRoundRect(
                            color = Color.Gray.copy(alpha = 0.2f),
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                        )
                        // 进度
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
 * 统计卡片
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
