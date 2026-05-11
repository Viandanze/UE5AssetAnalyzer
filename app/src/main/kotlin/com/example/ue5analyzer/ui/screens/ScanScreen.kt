package com.example.ue5analyzer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.ue5analyzer.model.AssetType
import com.example.ue5analyzer.model.OrphanRiskLevel
import com.example.ue5analyzer.model.UEAsset
import com.example.ue5analyzer.ui.viewmodel.MainViewModel
import com.example.ue5analyzer.ui.viewmodel.ScanProgress
import com.example.ue5analyzer.ui.viewmodel.SortOrder
import com.example.ue5analyzer.ui.viewmodel.UiState

/**
 * 扫描页面 - 原 MainScreen 的扫描和列表功能
 */

// 魔法数字常量
private const val PERCENTAGE_MAX = 100  // 百分比最大值

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: MainViewModel,
    onAssetClick: (UEAsset) -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val filteredAssets by viewModel.filteredAssets.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val showOrphanOnly by viewModel.showOrphanOnly.collectAsState()
    val orphanRiskLevelFilter by viewModel.orphanRiskLevelFilter.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    
    val context = LocalContext.current
    
    // 文件选择器
    val projectPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.scanProject(it) }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(currentProject?.name ?: "UE5 Asset Analyzer") 
                },
                actions = {
                    IconButton(onClick = onHistoryClick) {
                        Icon(Icons.Default.History, "历史项目")
                    }
                    IconButton(onClick = { projectPicker.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, "打开项目")
                    }
                }
            )
        }
    ) { padding ->
        when (uiState) {
            is UiState.Idle -> {
                EmptyState(
                    onScanClick = { projectPicker.launch(null) },
                    modifier = Modifier.padding(padding)
                )
            }
            is UiState.Scanning -> {
                LoadingState(
                    progress = scanProgress,
                    onCancel = { viewModel.cancelScan() },
                    modifier = Modifier.padding(padding)
                )
            }
            is UiState.Success -> {
                Column(modifier = Modifier.padding(padding)) {
                    AssetSearchBar(
                        query = searchQuery,
                        onQueryChange = { viewModel.searchAssets(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    // 类型筛选 + 排序
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 类型筛选
                        TypeFilter(
                            selectedType = filterType,
                            onTypeSelected = { viewModel.filterByType(it) },
                            modifier = Modifier.weight(1f)
                        )
                        
                        // 排序下拉
                        SortDropdown(
                            selectedOrder = sortOrder,
                            onOrderSelected = { viewModel.setSortOrder(it) }
                        )
                    }
                    
                    // 只看孤立资源筛选 + 风险等级筛选
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = showOrphanOnly,
                            onClick = { viewModel.setShowOrphanOnly(!showOrphanOnly) },
                            label = { Text("只看孤立") },
                            leadingIcon = if (showOrphanOnly) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                        
                        // 风险等级筛选
                        OrphanRiskLevelFilter(
                            selectedLevel = orphanRiskLevelFilter,
                            onLevelSelected = { viewModel.setOrphanRiskLevelFilter(it) }
                        )
                    }
                    
                    AssetList(
                        assets = filteredAssets,
                        onAssetClick = onAssetClick,
                        searchQuery = searchQuery,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            is UiState.Error -> {
                ErrorState(
                    message = (uiState as UiState.Error).message,
                    onRetry = { projectPicker.launch(null) },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "选择一个 UE5 项目开始分析",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onScanClick) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("选择项目")
        }
    }
}

@Composable
fun LoadingState(
    progress: ScanProgress?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text("正在扫描项目...", style = MaterialTheme.typography.bodyMedium)
        
        if (progress != null) {
            Spacer(modifier = Modifier.height(8.dp))
            if (progress.totalCount > 0) {
                // 显示进度百分比
                val percentage = (progress.scannedCount * PERCENTAGE_MAX / progress.totalCount).coerceIn(0, PERCENTAGE_MAX)
                Text(
                    text = "已扫描 ${progress.scannedCount} / ${progress.totalCount} ($percentage%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "已扫描 ${progress.scannedCount} 个资源",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (progress.currentFile.isNotEmpty()) {
                Text(
                    text = progress.currentFile,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 取消按钮
        OutlinedButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("取消扫描")
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}

@Composable
fun AssetSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("搜索资源名称或路径...") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "清除")
                }
            }
        },
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeFilter(
    selectedType: AssetType?,
    onTypeSelected: (AssetType?) -> Unit,
    modifier: Modifier = Modifier
) {
    val types = listOf(null) + AssetType.entries.filter { 
        it != AssetType.UNKNOWN 
    }
    
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(types) { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onTypeSelected(type) },
                label = { 
                    Text(type?.displayName ?: "全部") 
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortDropdown(
    selectedOrder: SortOrder,
    onOrderSelected: (SortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedOrder.displayName,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .menuAnchor()
                .width(120.dp),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            textStyle = MaterialTheme.typography.bodySmall,
            singleLine = true
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.displayName) },
                    onClick = {
                        onOrderSelected(order)
                        expanded = false
                    },
                    leadingIcon = if (order == selectedOrder) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
fun AssetList(
    assets: List<UEAsset>,
    onAssetClick: (UEAsset) -> Unit,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    if (assets.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "没有找到匹配的资源",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = assets, key = { it.id }) { asset ->
                AssetItem(
                    asset = asset,
                    onClick = { onAssetClick(asset) },
                    searchQuery = searchQuery
                )
            }
        }
    }
}

@Composable
fun AssetItem(
    asset: UEAsset,
    onClick: () -> Unit,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getTypeIcon(asset.type),
                contentDescription = null,
                tint = if (asset.isOrphan && asset.type == AssetType.LEVEL) {
                    MaterialTheme.colorScheme.tertiary  // 关卡孤立用橙色
                } else if (asset.isOrphan) {
                    MaterialTheme.colorScheme.error      // 普通孤立用红色
                } else {
                    MaterialTheme.colorScheme.primary   // 正常用蓝色
                }
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // 资源名称（带高亮）
                Text(
                    text = highlightText(asset.name, searchQuery),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 类型
                Text(
                    text = asset.type.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 路径（带高亮，仅在有搜索时显示）
                if (searchQuery.isNotEmpty() && asset.path.contains(searchQuery, ignoreCase = true)) {
                    Text(
                        text = highlightText(asset.path, searchQuery),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            if (asset.isOrphan) {
                SuggestionChip(
                    onClick = {},
                    label = { Text("孤立") },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (asset.type == AssetType.LEVEL) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 高亮匹配的文本
 * 使用 remember 缓存结果，只有 text 或 query 变化时才重新计算
 */
@Composable
fun highlightText(text: String, query: String): AnnotatedString {
    if (query.isEmpty()) {
        return androidx.compose.ui.text.AnnotatedString(text)
    }
    
    // 提取颜色到 remember 外部
    val primaryColor = MaterialTheme.colorScheme.primary
    
    return remember(text, query, primaryColor) {
        buildAnnotatedString {
            var lastIndex = 0
            val lowerText = text.lowercase()
            val lowerQuery = query.lowercase()
            
            var index = lowerText.indexOf(lowerQuery)
            while (index >= 0) {
                // 添加匹配前的文本
                if (index > lastIndex) {
                    append(text.substring(lastIndex, index))
                }
                // 添加高亮的匹配文本
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = primaryColor)) {
                    append(text.substring(index, index + query.length))
                }
                lastIndex = index + query.length
                index = lowerText.indexOf(lowerQuery, lastIndex)
            }
            
            // 添加剩余文本
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }
}

fun getTypeIcon(type: AssetType) = when (type) {
    AssetType.BLUEPRINT -> Icons.Default.Code
    AssetType.STATIC_MESH -> Icons.Default.ViewInAr
    AssetType.SKELETAL_MESH -> Icons.Default.Architecture
    AssetType.MATERIAL -> Icons.Default.Palette
    AssetType.MATERIAL_INSTANCE -> Icons.Default.Palette
    AssetType.TEXTURE -> Icons.Default.Image
    AssetType.SOUND -> Icons.Default.VolumeUp
    AssetType.PARTICLE_SYSTEM -> Icons.Default.BubbleChart
    AssetType.ANIMATION -> Icons.Default.Movie
    AssetType.LEVEL -> Icons.Default.Map
    AssetType.WIDGET -> Icons.Default.Dashboard
    AssetType.ENUM -> Icons.Default.List
    AssetType.STRUCT -> Icons.Default.DataObject
    AssetType.INTERFACE -> Icons.Default.Hub
    AssetType.DATA_TABLE -> Icons.Default.TableChart
    AssetType.CURVE -> Icons.Default.ShowChart
    else -> Icons.Default.InsertDriveFile
}

/**
 * 孤立风险等级筛选
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrphanRiskLevelFilter(
    selectedLevel: OrphanRiskLevel?,
    onLevelSelected: (OrphanRiskLevel?) -> Unit,
    modifier: Modifier = Modifier
) {
    val levels = listOf(null) + OrphanRiskLevel.entries
    
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(levels) { level ->
            val isSelected = selectedLevel == level
            val label = when (level) {
                null -> "全部"
                OrphanRiskLevel.HIGH -> "高风险"
                OrphanRiskLevel.LOW -> "低风险"
                OrphanRiskLevel.NONE -> "无风险"
            }
            val chipColor = when (level) {
                OrphanRiskLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
                OrphanRiskLevel.LOW -> MaterialTheme.colorScheme.tertiaryContainer
                OrphanRiskLevel.NONE -> MaterialTheme.colorScheme.secondaryContainer
                null -> MaterialTheme.colorScheme.surfaceVariant
            }
            
            FilterChip(
                selected = isSelected,
                onClick = { onLevelSelected(level) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipColor
                )
            )
        }
    }
}
