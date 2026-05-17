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
 * Statistics Screen - Display asset analysis charts
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
    // Use derivedStateOf to avoid recalculating on every recomposition
    val circularDeps by remember(assets) { 
        derivedStateOf { dependencyAnalyzer.findCircularDependencies(assets) } 
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics Analysis") }
            )
        }
    ) { padding ->
        when (uiState) {
            is UiState.Idle, is UiState.Scanning -> {
                // Scanning not started or in progress
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
                            text = if (uiState is UiState.Scanning) "Analyzing..." else "No statistics data yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState is UiState.Scanning) "Statistics will be displayed automatically after scanning" else "Please scan a UE5 project to view asset statistics",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            is UiState.Success -> {
                // Display Statistics Data
                LazyColumn(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Project Overview Card
                    item {
                        ProjectOverviewSection(
                            totalAssets = assets.size,
                            orphanCount = assets.count { it.isOrphan },
                            totalSize = assets.sumOf { it.size }
                        )
                    }
                    
                    // Health score
                    item {
                        HealthScoreSection(
                            score = analyzer.calculateHealthScore(assets)
                        )
                    }
                    
                    // Asset Type Distribution
                    item {
                        AssetTypeDistributionSection(assets = assets)
                    }
                    
                    // Top 10 assets by size
                    item {
                        LargestAssetsSection(assets = assets)
                    }
                    
                    // Circular dependencies detection
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
                        text = "Analysis failed",
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
            text = "Project Overview",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Total Assets",
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
                title = "Orphan Assets",
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
            title = "Total Project Size",
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
                text = "Health Score",
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
                    score >= 80 -> "Project health is good"
                    score >= 60 -> "Some issues exist"
                    else -> "Needs optimization"
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
                text = "Asset Type Distribution",
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
                    text = "No data",
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
                text = "Assets by Size TOP 10",
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
                    text = "No data",
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
                    text = "Circular Dependencies (${circularDeps.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "The following assets have circular dependencies. Consider checking if they can be resolved",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            circularDeps.take(5).forEachIndexed { index, cycle ->
                Column(
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Cycle ${index + 1}:",
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
                    text = "... and ${circularDeps.size - 5} more cycles",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
