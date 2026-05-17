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
 * Dependency Graph Node Data
 */
data class GraphNode(
    val id: String,
    val name: String,
    val isCenter: Boolean = false,
    val type: NodeType = NodeType.CENTER
)

enum class NodeType {
    CENTER,   // Center node (blue)
    DEPENDENCY, // Dependency node (red)
    REFERENCE  // Referenced by node (green)
}

/**
 * Dependency Graph Component
 * Use Compose Canvas to draw interactive dependency graph
 * - Center node: Current asset (blue circle)
 * - Upper nodes: Dependencies (red circles)
 * - Lower nodes: Referenced by (green circles)
 * - Support drag pan and zoom
 * - Click node to navigate to asset details
 */
@Composable
fun DependencyGraph(
    currentAsset: UEAsset,
    assets: List<UEAsset>,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val assetMap = remember(assets) { assets.associateBy { it.id } }
    
    // Build nodes
    val nodes = remember(currentAsset, assetMap) {
        buildList {
            // Center node
            add(GraphNode(currentAsset.id, currentAsset.name, isCenter = true, type = NodeType.CENTER))
            
            // Dependency nodes (upper red circles)
            currentAsset.dependencies.forEach { depId ->
                val depAsset = assetMap[depId]
                add(GraphNode(depId, depAsset?.name ?: depId, type = NodeType.DEPENDENCY))
            }
            
            // Referenced by nodes (lower green circles)
            currentAsset.references.forEach { refId ->
                val refAsset = assetMap[refId]
                add(GraphNode(refId, refAsset?.name ?: refId, type = NodeType.REFERENCE))
            }
        }
    }
    
    // Transform state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // Node position calculation
    val nodePositions = remember(nodes) {
        calculateNodePositions(nodes)
    }
    
    // Click detection
    var lastTappedNodeId by remember { mutableStateOf<String?>(null) }
    
    // Text measurer (declared outside Canvas)
    val textMeasurer = rememberTextMeasurer()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        if (nodes.size <= 1) {
            // Show hint when no dependencies exist
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No dependencies",
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
                            // Convert tap position to canvas coordinates
                            val canvasX = (tapOffset.x - offset.x) / scale
                            val canvasY = (tapOffset.y - offset.y) / scale
                            
                            // Detect tapped node
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
                
                // Draw connections
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
                            // Draw Arrow
                            drawArrow(nodePos, centerPos)
                        }
                    }
                }
                
                // Draw Node
                nodes.forEach { node ->
                    val position = nodePositions[node.id] ?: return@forEach
                    drawNode(node, position)
                }
            }
            
            // Draw node names
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
        
        // Operation Tips
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
                text = "Pinch to zoom · Tap node to navigate",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Legend
        Legend(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )
    }
}

/**
 * Calculate Node Positions
 * - Center node in the middle
 * - Dependency nodes in upper semicircle
 * - Referenced by nodes in lower semicircle
 */
private fun calculateNodePositions(nodes: List<GraphNode>): Map<String, Offset> {
    val positions = mutableMapOf<String, Offset>()
    if (nodes.isEmpty()) return positions
    
    val centerX = 400f
    val centerY = 400f
    val radius = 250f
    
    // Center node
    val centerNode = nodes.find { it.isCenter }
    if (centerNode != null) {
        positions[centerNode.id] = Offset(centerX, centerY)
    }
    
    // Dependency nodes (upper)
    val dependencies = nodes.filter { it.type == NodeType.DEPENDENCY }
    if (dependencies.isNotEmpty()) {
        val startAngle = -150.0  // Start from upper left
        val endAngle = -30.0     // End at upper right
        val angleStep = (endAngle - startAngle) / (dependencies.size - 1).coerceAtLeast(1)
        
        dependencies.forEachIndexed { index, node ->
            val angle = Math.toRadians(startAngle + angleStep * index)
            val x = centerX + (radius * kotlin.math.cos(angle)).toFloat()
            val y = centerY + (radius * kotlin.math.sin(angle)).toFloat()
            positions[node.id] = Offset(x, y)
        }
    }
    
    // Referenced by nodes (lower)
    val references = nodes.filter { it.type == NodeType.REFERENCE }
    if (references.isNotEmpty()) {
        val startAngle = 30.0   // Start from lower right
        val endAngle = 150.0    // End at lower left
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
 * Draw Node
 */
private fun DrawScope.drawNode(node: GraphNode, position: Offset) {
    val color = when (node.type) {
        NodeType.CENTER -> Color(0xFF2196F3)      // Blue
        NodeType.DEPENDENCY -> Color(0xFFF44336) // Red
        NodeType.REFERENCE -> Color(0xFF4CAF50)  // Green
    }
    
    // Draw circle
    drawCircle(
        color = color,
        radius = NODE_RADIUS,
        center = position,
        style = Fill
    )
    
    // Draw border
    drawCircle(
        color = color.copy(alpha = 0.8f),
        radius = NODE_RADIUS,
        center = position,
        style = Stroke(width = 3f)
    )
}

/**
 * Draw Arrow
 */
private fun DrawScope.drawArrow(from: Offset, to: Offset) {
    val arrowSize = 8f
    val direction = (to - from)
    val length = kotlin.math.sqrt(direction.x * direction.x + direction.y * direction.y)
    if (length < 1f) return
    
    val normalized = direction / length
    val arrowTip = to - normalized * (NODE_RADIUS + 2)
    
    // Calculate vector perpendicular to direction
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
 * Legend Component
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
        LegendItem(color = Color(0xFF2196F3), label = "Current Asset")
        LegendItem(color = Color(0xFFF44336), label = "Dependency")
        LegendItem(color = Color(0xFF4CAF50), label = "Referenced By")
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
