package com.example.ue5analyzer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ue5analyzer.model.AssetType
import com.example.ue5analyzer.model.OrphanRiskLevel
import com.example.ue5analyzer.model.UEAsset
import com.example.ue5analyzer.ui.viewmodel.MainViewModel

/**
 * 资源详情页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    assetId: String,
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onAssetClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val assets by viewModel.assets.collectAsState()
    val asset = remember(assetId, assets) { assets.find { it.id == assetId } }
    val assetMap = remember(assets) { assets.associateBy { it.id } }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("资源详情") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (asset == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "资源未找到",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            AssetDetailContent(
                asset = asset,
                assets = assets,
                assetMap = assetMap,
                onAssetClick = onAssetClick,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun AssetDetailContent(
    asset: UEAsset,
    assets: List<UEAsset>,
    assetMap: Map<String, UEAsset>,
    onAssetClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 基本信息卡片
        item {
            BasicInfoCard(asset = asset)
        }
        
        // 风险评级（替代原来的健康度评分）
        item {
            RiskLevelCard(asset = asset)
        }
        
        // 依赖关系
        if (asset.dependencies.isNotEmpty()) {
            item {
                DependenciesCard(
                    title = "依赖的资源",
                    description = "此资源引用了以下资源",
                    dependencies = asset.dependencies,
                    assetMap = assetMap,
                    onAssetClick = onAssetClick
                )
            }
        }
        
        // 被引用关系
        if (asset.references.isNotEmpty()) {
            item {
                ReferencesCard(
                    title = "被引用的资源",
                    description = "以下资源引用了此资源",
                    references = asset.references,
                    assetMap = assetMap,
                    onAssetClick = onAssetClick
                )
            }
        }
        
        // 孤立标记说明
        if (asset.isOrphan) {
            item {
                OrphanWarningCard()
            }
        }
    }
}

@Composable
private fun BasicInfoCard(asset: UEAsset) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getTypeIcon(asset.type),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = asset.type.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Divider(
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            InfoRow(
                icon = Icons.Default.Folder,
                label = "路径",
                value = asset.path
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            InfoRow(
                icon = Icons.Default.Storage,
                label = "大小",
                value = formatFileSize(asset.size)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            InfoRow(
                icon = Icons.Default.Schedule,
                label = "最后修改",
                value = formatTimestamp(asset.lastModified)
            )
        }
    }
}

@Composable
private fun RiskLevelCard(asset: UEAsset) {
    val (containerColor, contentColor, icon, title, description) = when {
        // 孤立资源 - 高风险
        asset.isOrphan && asset.type != AssetType.LEVEL -> RiskLevelInfo(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            icon = Icons.Default.Error,
            title = "高风险 - 孤立资源",
            description = "此资源无任何引用，可能为冗余资源，建议检查是否可删除"
        )
        // 孤立关卡 - 低风险
        asset.isOrphan && asset.type == AssetType.LEVEL -> RiskLevelInfo(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = Icons.Default.Warning,
            title = "低风险 - 孤立关卡",
            description = "此关卡无其他资源引用，可能为未使用的地图文件"
        )
        // 引用数=1 - 低风险
        asset.references.size == 1 -> RiskLevelInfo(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = Icons.Default.Info,
            title = "低风险 - 仅1个引用",
            description = "此资源只有1个引用，存在一定风险"
        )
        // 引用数>=2 - 健康
        else -> RiskLevelInfo(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = Icons.Default.CheckCircle,
            title = "健康 - ${asset.references.size}个引用",
            description = "此资源被多个资源引用，状态正常"
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = contentColor
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }
        }
    }
}

private data class RiskLevelInfo(
    val containerColor: androidx.compose.ui.graphics.Color,
    val contentColor: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val description: String
)

@Composable
private fun DependenciesCard(
    title: String,
    description: String,
    dependencies: List<String>,
    assetMap: Map<String, UEAsset>,
    onAssetClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            dependencies.forEach { depId ->
                val depAsset = assetMap[depId]
                val assetName = depAsset?.name ?: depId
                val isClickable = depAsset != null
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isClickable) {
                                Modifier.clickable { onAssetClick(depId) }
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isClickable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = assetName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isClickable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isClickable) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferencesCard(
    title: String,
    description: String,
    references: List<String>,
    assetMap: Map<String, UEAsset>,
    onAssetClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TransferWithinAStation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            references.forEach { refId ->
                val refAsset = assetMap[refId]
                val assetName = refAsset?.name ?: refId
                val isClickable = refAsset != null
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isClickable) {
                                Modifier.clickable { onAssetClick(refId) }
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowLeft,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isClickable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = assetName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isClickable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isClickable) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrphanWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "孤立资源",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "此资源没有被任何其他资源引用，可能是冗余资源，建议检查是否可以删除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

