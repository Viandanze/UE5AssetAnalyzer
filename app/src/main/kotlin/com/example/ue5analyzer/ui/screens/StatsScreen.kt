package com.example.ue5analyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ue5analyzer.domain.analyzer.AssetAnalyzer
import com.example.ue5analyzer.domain.analyzer.DependencyAnalyzer
import com.example.ue5analyzer.model.AssetType
import com.example.ue5analyzer.model.UEAsset
import com.example.ue5analyzer.ui.components.*
import com.example.ue5analyzer.ui.viewmodel.MainViewModel
import com.example.ue5analyzer.ui.viewmodel.UiState
import com.example.ue5analyzer.util.FormatUtils

/**
 * 统计页面 - 显示资源分析统计图表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val assets by viewModel.assets.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    
    val analyzer = remember { AssetAnalyzer() }
    val dependencyAnalyzer = remember { DependencyAnalyzer() }
    // 使用 derivedStateOf 避免在每次重组时重新计算
    val circularDeps by remember(assets) { 
        derivedStateOf { dependencyAnalyzer.findCircularDependencies(assets) } 
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计分析") }
            )
        }
    ) { padding ->
        when (uiState) {
            is UiState.Idle, is UiState.Scanning -> {
                // 扫描未开始或进行中
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState is UiState.Scanning) "正在分析..." else "暂无统计数据",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState is UiState.Scanning) "扫描完成后将自动显示统计信息" else "请先扫描 UE5 项目以查看资源统计",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            is UiState.Success -> {
                // 显示统计数据
                LazyColumn(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 项目概况卡片
                    item {
                        ProjectOverviewSection(
                            totalAssets = assets.size,
                            orphanCount = assets.count { it.isOrphan },
                            totalSize = assets.sumOf { it.size }
                        )
                    }
                    
                    // 健康度评分
                    item {
                        HealthScoreSection(
                            score = analyzer.calculateHealthScore(assets)
                        )
                    }
                    
                    // 资源类型分布
                    item {
                        AssetTypeDistributionSection(assets = assets)
                    }
                    
                    // 资源大小TOP10
                    item {
                        LargestAssetsSection(assets = assets)
                    }
                    
                    // 循环依赖检测
                    if (circularDeps.isNotEmpty()) {
                        item {
                            CircularDependenciesSection(circularDeps = circularDeps)
                        }
                    }
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "分析失败",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectOverviewSection(
    totalAssets: Int,
    orphanCount: Int,
    totalSize: Long
) {
    Column {
        Text(
            text = "项目概况",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "总资源数",
                value = totalAssets.toString(),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.weight(1f)
            )
            
            StatCard(
                title = "孤立资源",
                value = orphanCount.toString(),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (orphanCount > 0) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        StatCard(
            title = "项目总大小",
            value = FormatUtils.formatFileSize(totalSize),
            icon = {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HealthScoreSection(score: Int) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "健康度评分",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            CircularProgressBar(
                percentage = score.toFloat(),
                modifier = Modifier.size(150.dp),
                color = when {
                    score >= 80 -> MaterialTheme.colorScheme.primary
                    score >= 60 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = when {
                    score >= 80 -> "项目状态良好"
                    score >= 60 -> "存在一些问题"
                    else -> "需要优化"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AssetTypeDistributionSection(assets: List<UEAsset>) {
    val assetsByType = assets.groupingBy { it.type }.eachCount()
    val sortedData = assetsByType.entries
        .sortedByDescending { it.value }
        .map { it.key.displayName to it.value }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "资源类型分布",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (sortedData.isNotEmpty()) {
                PieChart(
                    data = sortedData,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = "暂无数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LargestAssetsSection(assets: List<UEAsset>) {
    val largestAssets = assets.sortedByDescending { it.size }.take(10)
    val sortedData = largestAssets.map { it.name to it.size }
    val maxSize = largestAssets.maxOfOrNull { it.size } ?: 1L
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "资源大小 TOP 10",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (sortedData.isNotEmpty()) {
                BarChart(
                    data = sortedData,
                    maxValue = maxSize,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = "暂无数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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


@Composable
private fun CircularDependenciesSection(circularDeps: List<List<String>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
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
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "循环依赖 (${circularDeps.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "以下资源存在循环依赖关系，建议检查是否可以解除",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            circularDeps.take(5).forEachIndexed { index, cycle ->
                Column(
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "循环 ${index + 1}:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    cycle.forEach { assetId ->
                        Row(
                            modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = assetId,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            
            if (circularDeps.size > 5) {
                Text(
                    text = "... 还有 ${circularDeps.size - 5} 个循环",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
