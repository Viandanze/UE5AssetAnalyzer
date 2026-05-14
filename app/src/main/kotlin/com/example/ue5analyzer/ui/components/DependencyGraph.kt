package com.example.ue5analyzer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ue5analyzer.model.UEAsset

/**
 * 依赖关系图节点数据
 */
data class GraphNode(
    val id: String,
    val name: String,
    val isCenter: Boolean = false,
    val type: NodeType = NodeType.CENTER
)

enum class NodeType {
    CENTER,   // 中心节点（蓝色）
    DEPENDENCY, // 依赖节点（红色）
    REFERENCE  // 被引用节点（绿色）
}

/**
 * 依赖关系图组件
 * 使用 Compose Canvas 绘制交互式依赖关系图
 * - 中心节点：当前资源（蓝色圆）
 * - 上方节点：依赖的资源（红色圆）
 * - 下方节点：被引用的资源（绿色圆）
 * - 支持拖拽平移和缩放
 * - 点击节点可跳转到对应资源详情
 */
@Composable
fun DependencyGraph(
    currentAsset: UEAsset,
    assets: List<UEAsset>,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val assetMap = remember(assets) { assets.associateBy { it.id } }
    
    // 构建节点
    val nodes = remember(currentAsset, assetMap) {
        buildList {
            // 中心节点
            add(GraphNode(currentAsset.id, currentAsset.name, isCenter = true, type = NodeType.CENTER))
            
            // 依赖节点（上方的红色圆）
            currentAsset.dependencies.forEach { depId ->
                val depAsset = assetMap[depId]
                add(GraphNode(depId, depAsset?.name ?: depId, type = NodeType.DEPENDENCY))
            }
            
            // 被引用节点（下方的绿色圆）
            currentAsset.references.forEach { refId ->
                val refAsset = assetMap[refId]
                add(GraphNode(refId, refAsset?.name ?: refId, type = NodeType.REFERENCE))
            }
        }
    }
    
    // 变换状态
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // 节点位置计算
    val nodePositions = remember(nodes) {
        calculateNodePositions(nodes)
    }
    
    // 点击检测
    var lastTappedNodeId by remember { mutableStateOf<String?>(null) }
    
    // 文本测量器（在 Canvas 外部声明）
    val textMeasurer = rememberTextMeasurer()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        if (nodes.size <= 1) {
            // 没有依赖关系时显示提示
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无依赖关系",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 3f)
                            offset = Offset(
                                offset.x + pan.x,
                                offset.y + pan.y
                            )
                        }
                    }
                    .pointerInput(nodePositions) {
                        detectTapGestures { tapOffset ->
                            // 将点击位置转换为画布坐标
                            val canvasX = (tapOffset.x - offset.x) / scale
                            val canvasY = (tapOffset.y - offset.y) / scale
                            
                            // 检测点击的节点
                            nodePositions.forEach { (nodeId, position) ->
                                val distance = kotlin.math.sqrt(
                                    (canvasX - position.x) * (canvasX - position.x) +
                                    (canvasY - position.y) * (canvasY - position.y)
                                )
                                if (distance <= NODE_RADIUS + 10) {
                                    lastTappedNodeId = nodeId
                                    onNodeClick(nodeId)
                                    return@detectTapGestures
                                }
                            }
                        }
                    }
            ) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                
                // 绘制连线
                val centerNode = nodes.find { it.isCenter }
                if (centerNode != null) {
                    val centerPos = nodePositions[centerNode.id] ?: Offset(centerX, centerY)
                    
                    nodes.forEach { node ->
                        if (!node.isCenter) {
                            val nodePos = nodePositions[node.id] ?: return@forEach
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.5f),
                                start = centerPos,
                                end = nodePos,
                                strokeWidth = 2f
                            )
                            // 绘制箭头
                            drawArrow(nodePos, centerPos)
                        }
                    }
                }
                
                // 绘制节点
                nodes.forEach { node ->
                    val position = nodePositions[node.id] ?: return@forEach
                    drawNode(node, position)
                }
            }
            
            // 绘制节点名称
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            ) {
                val textStyle = TextStyle(
                    fontSize = 10.sp,
                    color = Color.Black
                )
                
                nodes.forEach { node ->
                    val position = nodePositions[node.id] ?: return@forEach
                    val displayName = if (node.name.length > 10) {
                        node.name.take(8) + "..."
                    } else {
                        node.name
                    }
                    
                    val textLayoutResult = textMeasurer.measure(
                        text = displayName,
                        style = textStyle,
                        constraints = Constraints()
                    )
                    
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(
                            position.x - textLayoutResult.size.width / 2,
                            position.y + NODE_RADIUS + 4
                        )
                    )
                }
            }
        }
        
        // 操作提示
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = MaterialTheme.shapes.small
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "双指缩放 · 点击节点跳转",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 图例
        Legend(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )
    }
}

/**
 * 计算节点位置
 * - 中心节点在中间
 * - 依赖节点在上半圆
 * - 被引用节点在下半圆
 */
private fun calculateNodePositions(nodes: List<GraphNode>): Map<String, Offset> {
    val positions = mutableMapOf<String, Offset>()
    if (nodes.isEmpty()) return positions
    
    val centerX = 400f
    val centerY = 400f
    val radius = 250f
    
    // 中心节点
    val centerNode = nodes.find { it.isCenter }
    if (centerNode != null) {
        positions[centerNode.id] = Offset(centerX, centerY)
    }
    
    // 依赖节点（上方）
    val dependencies = nodes.filter { it.type == NodeType.DEPENDENCY }
    if (dependencies.isNotEmpty()) {
        val startAngle = -150.0  // 从左上方开始
        val endAngle = -30.0     // 到右上方结束
        val angleStep = (endAngle - startAngle) / (dependencies.size - 1).coerceAtLeast(1)
        
        dependencies.forEachIndexed { index, node ->
            val angle = Math.toRadians(startAngle + angleStep * index)
            val x = centerX + (radius * kotlin.math.cos(angle)).toFloat()
            val y = centerY + (radius * kotlin.math.sin(angle)).toFloat()
            positions[node.id] = Offset(x, y)
        }
    }
    
    // 被引用节点（下方）
    val references = nodes.filter { it.type == NodeType.REFERENCE }
    if (references.isNotEmpty()) {
        val startAngle = 30.0   // 从右下方开始
        val endAngle = 150.0    // 到左下方结束
        val angleStep = (endAngle - startAngle) / (references.size - 1).coerceAtLeast(1)
        
        references.forEachIndexed { index, node ->
            val angle = Math.toRadians(startAngle + angleStep * index)
            val x = centerX + (radius * kotlin.math.cos(angle)).toFloat()
            val y = centerY + (radius * kotlin.math.sin(angle)).toFloat()
            positions[node.id] = Offset(x, y)
        }
    }
    
    return positions
}

/**
 * 绘制节点
 */
private fun DrawScope.drawNode(node: GraphNode, position: Offset) {
    val color = when (node.type) {
        NodeType.CENTER -> Color(0xFF2196F3)      // 蓝色
        NodeType.DEPENDENCY -> Color(0xFFF44336) // 红色
        NodeType.REFERENCE -> Color(0xFF4CAF50)  // 绿色
    }
    
    // 绘制圆形
    drawCircle(
        color = color,
        radius = NODE_RADIUS,
        center = position,
        style = Fill
    )
    
    // 绘制边框
    drawCircle(
        color = color.copy(alpha = 0.8f),
        radius = NODE_RADIUS,
        center = position,
        style = Stroke(width = 3f)
    )
}

/**
 * 绘制箭头
 */
private fun DrawScope.drawArrow(from: Offset, to: Offset) {
    val arrowSize = 8f
    val direction = (to - from)
    val length = kotlin.math.sqrt(direction.x * direction.x + direction.y * direction.y)
    if (length < 1f) return
    
    val normalized = direction / length
    val arrowTip = to - normalized * (NODE_RADIUS + 2)
    
    // 计算垂直于方向的向量
    val perpendicular = Offset(-normalized.y, normalized.x)
    
    val arrowLeft = arrowTip - normalized * arrowSize + perpendicular * (arrowSize / 2)
    val arrowRight = arrowTip - normalized * arrowSize - perpendicular * (arrowSize / 2)
    
    drawLine(
        color = Color.Gray.copy(alpha = 0.6f),
        start = arrowLeft,
        end = arrowTip,
        strokeWidth = 2f
    )
    drawLine(
        color = Color.Gray.copy(alpha = 0.6f),
        start = arrowRight,
        end = arrowTip,
        strokeWidth = 2f
    )
}

/**
 * 图例组件
 */
@Composable
private fun Legend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.small
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LegendItem(color = Color(0xFF2196F3), label = "当前资源")
        LegendItem(color = Color(0xFFF44336), label = "依赖")
        LegendItem(color = Color(0xFF4CAF50), label = "被引用")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val NODE_RADIUS = 30f
