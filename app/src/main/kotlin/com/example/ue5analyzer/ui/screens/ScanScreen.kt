package com.example.ue5analyzer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.example.ue5analyzer.data.manager.ThemeMode
import com.example.ue5analyzer.data.manager.ThemePreferencesManager
import com.example.ue5analyzer.model.AssetType
import com.example.ue5analyzer.model.OrphanRiskLevel
import com.example.ue5analyzer.model.ScanConfig
import com.example.ue5analyzer.model.UEAsset
import com.example.ue5analyzer.ui.viewmodel.MainViewModel
import com.example.ue5analyzer.ui.viewmodel.ScanProgress
import com.example.ue5analyzer.ui.viewmodel.SortOrder
import com.example.ue5analyzer.ui.viewmodel.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 扫描页面 - 原 MainScreen 的扫描和列表功能
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: MainViewModel,
    onAssetClick: (UEAsset) -> Unit,
    onHistoryClick: () -> Unit,
    themePreferencesManager: ThemePreferencesManager? = null,
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
    val scanConfig by viewModel.scanConfigFlow.collectAsState()
    
    // 选择模式状态
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedAssetIds = viewModel.selectedAssetIds
    
    val context = LocalContext.current
    
    // Snackbar 状态
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 协程作用域
    val scope = rememberCoroutineScope()
    
    // 设置对话框状态
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // 主题状态
    var currentThemeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
    
    // 收集主题偏好
    LaunchedEffect(themePreferencesManager) {
        themePreferencesManager?.themeModeFlow?.collect { mode ->
            currentThemeMode = mode
        }
    }
    
    // 文件选择器
    val projectPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { 
            // 如果有自定义配置，先保存再扫描
            viewModel.scanProject(it) 
        }
    }
    
    // 导出选中资源
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        uri?.let { exportUri ->
            try {
                val content = viewModel.exportSelectedAssets()
                context.contentResolver.openOutputStream(exportUri)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "已导出 ${viewModel.selectedAssetIds.size} 个资源",
                        duration = SnackbarDuration.Short
                    )
                }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "导出失败",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }
    
    // 设置对话框
    if (showSettingsDialog) {
        ScanConfigDialog(
            currentConfig = scanConfig,
            onDismiss = { showSettingsDialog = false },
            onSave = { newConfig ->
                viewModel.updateScanConfig(newConfig)
                showSettingsDialog = false
            }
        )
    }
    
    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // 选择模式顶栏
                TopAppBar(
                    title = { Text("已选 ${selectedAssetIds.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, "取消选择")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.DoneAll, "全选")
                        }
                    }
                )
            } else {
                // 普通模式顶栏
                TopAppBar(
                    title = { 
                        Text(currentProject?.name ?: "UE5 Asset Analyzer") 
                    },
                    actions = {
                        // 主题切换按钮
                        if (themePreferencesManager != null) {
                            IconButton(onClick = {
                                val newMode = when (currentThemeMode) {
                                    ThemeMode.SYSTEM -> ThemeMode.LIGHT
                                    ThemeMode.LIGHT -> ThemeMode.DARK
                                    ThemeMode.DARK -> ThemeMode.SYSTEM
                                    else -> ThemeMode.SYSTEM
                                }
                                scope.launch {
                                    themePreferencesManager.saveThemeMode(newMode)
                                }
                            }) {
                                Icon(
                                    imageVector = when (currentThemeMode) {
                                        ThemeMode.SYSTEM -> Icons.Default.Brightness6
                                        ThemeMode.LIGHT -> Icons.Default.WbSunny
                                        ThemeMode.DARK -> Icons.Default.Nightlight
                                        else -> Icons.Default.Brightness6
                                    },
                                    contentDescription = "切换主题"
                                )
                            }
                        }
                        
                        // 设置按钮（仅在成功状态显示）
                        if (uiState is UiState.Success) {
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(Icons.Default.Build, "扫描设置")
                            }
                        }
                        
                        IconButton(onClick = onHistoryClick) {
                            Icon(Icons.Default.History, "历史项目")
                        }
                        IconButton(onClick = { projectPicker.launch(null) }) {
                            Icon(Icons.Default.FolderOpen, "打开项目")
                        }
                    }
                )
            }
        },
        bottomBar = {
            // 选择模式底部操作栏
            if (isSelectionMode) {
                SelectionBottomBar(
                    selectedCount = selectedAssetIds.size,
                    totalCount = filteredAssets.size,
                    onExportClick = { 
                        exportLauncher.launch("selected_assets_${System.currentTimeMillis()}.md")
                    },
                    onClearClick = { viewModel.clearSelection() }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    // 搜索栏 + 选择按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssetSearchBar(
                            query = searchQuery,
                            onQueryChange = { viewModel.searchAssets(it) },
                            modifier = Modifier.weight(1f)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // 选择按钮
                        FilledTonalIconButton(
                            onClick = { viewModel.toggleSelectionMode() }
                        ) {
                            Icon(
                                imageVector = if (isSelectionMode) Icons.Default.Check else Icons.Default.Checklist,
                                contentDescription = if (isSelectionMode) "退出选择" else "选择"
                            )
                        }
                    }
                    
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
                        isSelectionMode = isSelectionMode,
                        selectedAssetIds = selectedAssetIds,
                        onAssetSelect = { assetId -> viewModel.toggleAssetSelection(assetId) },
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

/**
 * 扫描配置对话框
 */
@Composable
fun ScanConfigDialog(
    currentConfig: ScanConfig,
    onDismiss: () -> Unit,
    onSave: (ScanConfig) -> Unit
) {
    var ignoredDirectories by remember { mutableStateOf(currentConfig.ignoredDirectories.joinToString("\n")) }
    var ignoredExtensions by remember { mutableStateOf(currentConfig.ignoredExtensions.joinToString("\n")) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("扫描设置") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "忽略目录（每行一个）",
                    style = MaterialTheme.typography.labelMedium
                )
                OutlinedTextField(
                    value = ignoredDirectories,
                    onValueChange = { ignoredDirectories = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = { Text("Intermediate\nSaved\nDerivedDataCache") },
                    maxLines = 10
                )
                
                Text(
                    text = "忽略扩展名（每行一个，不含点号）",
                    style = MaterialTheme.typography.labelMedium
                )
                OutlinedTextField(
                    value = ignoredExtensions,
                    onValueChange = { ignoredExtensions = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    placeholder = { Text("uproject\npng\njpg") },
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newConfig = currentConfig.copy(
                    ignoredDirectories = ignoredDirectories
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet(),
                    ignoredExtensions = ignoredExtensions
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                )
                onSave(newConfig)
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
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
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 扫描图标
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "正在扫描项目...",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        if (progress != null) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // 线性进度条
            if (progress.totalCount > 0) {
                val progressFloat = (progress.scannedCount.toFloat() / progress.totalCount.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = progressFloat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 进度百分比文字
                val percentage = ((progress.scannedCount.toFloat() / progress.totalCount.toFloat()) * 100).toInt()
                Text(
                    text = "正在扫描: ${progress.scannedCount} / ${progress.totalCount} 文件 ($percentage%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 无总数时显示不确定进度条
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "已扫描 ${progress.scannedCount} 个资源",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 当前文件名（滚动显示）
            if (progress.currentFile.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = progress.currentFile,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 取消按钮
        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
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

/**
 * 资产搜索栏 - 带防抖动画提示
 */
@Composable
fun AssetSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isSearching by remember { mutableStateOf(false) }
    
    // 防抖逻辑：300ms 后触发实际搜索
    LaunchedEffect(query) {
        if (query.isNotEmpty()) {
            isSearching = true
            delay(300)
            isSearching = false
        } else {
            isSearching = false
        }
    }
    
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("搜索资源名称或路径...") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            when {
                isSearching && query.isNotEmpty() -> {
                    // 搜索中显示进度指示器
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                query.isNotEmpty() -> {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "清除")
                    }
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
    isSelectionMode: Boolean = false,
    selectedAssetIds: List<String> = emptyList(),
    onAssetSelect: ((String) -> Unit)? = null,
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
                    searchQuery = searchQuery,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedAssetIds.contains(asset.id),
                    onSelect = { onAssetSelect?.invoke(asset.id) }
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
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelectionMode) {
                    Modifier.clickable(onClick = { onSelect?.invoke() })
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 选择模式下的 Checkbox
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelect?.invoke() },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            
            // 资源图标
            Icon(
                imageVector = getTypeIcon(asset.type),
                contentDescription = null,
                tint = if (asset.isOrphan && asset.type == AssetType.LEVEL) {
                    MaterialTheme.colorScheme.tertiary  // 关卡孤立用橙色
                } else if (asset.isOrphan) {
                    MaterialTheme.colorScheme.error      // 普通孤立用红色
                } else {
                    MaterialTheme.colorScheme.primary   // 正常用蓝色
                },
                modifier = Modifier.padding(start = if (isSelectionMode) 0.dp else 8.dp)
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
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = highlightText(asset.path, searchQuery),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // 孤立标记
            if (asset.isOrphan) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "孤立资源",
                    modifier = Modifier.size(18.dp),
                    tint = if (asset.type == AssetType.LEVEL) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

@Composable
private fun SelectionBottomBar(
    selectedCount: Int,
    totalCount: Int,
    onExportClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已选 $selectedCount / $totalCount",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row {
                TextButton(onClick = onClearClick) {
                    Text("清空")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onExportClick) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("导出")
                }
            }
        }
    }
}

/**
 * 高亮搜索关键词
 */
@Composable
private fun highlightText(text: String, query: String): AnnotatedString {
    if (query.isEmpty()) {
        return AnnotatedString(text)
    }
    
    return buildAnnotatedString {
        var currentIndex = 0
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        
        while (currentIndex < text.length) {
            val matchIndex = lowerText.indexOf(lowerQuery, currentIndex)
            if (matchIndex == -1) {
                append(text.substring(currentIndex))
                break
            }
            
        append(text.substring(currentIndex, matchIndex))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
            append(text.substring(matchIndex, matchIndex + query.length))
        }
        currentIndex = matchIndex + query.length
        }
    }
}

/**
 * 获取资源类型对应的图标
 */
fun getTypeIcon(type: AssetType) = when (type) {
    AssetType.BLUEPRINT -> Icons.Default.Memory
    AssetType.STATIC_MESH -> Icons.Default.ViewInAr
    AssetType.SKELETAL_MESH -> Icons.Default.ViewInAr
    AssetType.MATERIAL -> Icons.Default.Texture
    AssetType.MATERIAL_INSTANCE -> Icons.Default.Texture
    AssetType.TEXTURE -> Icons.Default.Image
    AssetType.SOUND -> Icons.Default.VolumeUp
    AssetType.PARTICLE_SYSTEM -> Icons.Default.Blender
    AssetType.ANIMATION -> Icons.Default.Movie
    AssetType.LEVEL -> Icons.Default.Map
    AssetType.WIDGET -> Icons.Default.Dashboard
    AssetType.ENUM -> Icons.Default.List
    AssetType.STRUCT -> Icons.Default.DataObject
    AssetType.INTERFACE -> Icons.Default.Cable
    AssetType.DATA_TABLE -> Icons.Default.TableChart
    AssetType.CURVE -> Icons.Default.ShowChart
    AssetType.UNKNOWN -> Icons.Default.HelpOutline
}

/**
 * 风险等级筛选器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrphanRiskLevelFilter(
    selectedLevel: OrphanRiskLevel?,
    onLevelSelected: (OrphanRiskLevel?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        FilterChip(
            selected = selectedLevel != null,
            onClick = { expanded = true },
            label = { 
                Text(selectedLevel?.displayName ?: "风险等级") 
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier.menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // 全部选项
            DropdownMenuItem(
                text = { Text("全部") },
                onClick = {
                    onLevelSelected(null)
                    expanded = false
                },
                leadingIcon = if (selectedLevel == null) {
                    { Icon(Icons.Default.Check, null) }
                } else null
            )
            
            Divider()
            
            // 各个风险等级
            OrphanRiskLevel.entries.filter { it != OrphanRiskLevel.NONE }.forEach { level ->
                DropdownMenuItem(
                    text = { Text(level.displayName) },
                    onClick = {
                        onLevelSelected(level)
                        expanded = false
                    },
                    leadingIcon = if (selectedLevel == level) {
                        { Icon(Icons.Default.Check, null) }
                    } else null
                )
            }
        }
    }
}

// 扩展 OrphanRiskLevel 添加 displayName
val OrphanRiskLevel.displayName: String
    get() = when (this) {
        OrphanRiskLevel.NONE -> "无"
        OrphanRiskLevel.LOW -> "低风险"
        OrphanRiskLevel.HIGH -> "高风险"
    }
