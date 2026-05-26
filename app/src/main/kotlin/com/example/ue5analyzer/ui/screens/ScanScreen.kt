package com.example.ue5analyzer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Blender
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
 * Scan Screen - Original MainScreen scan and list functionality
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    viewModel: MainViewModel,
    onAssetClick: (UEAsset) -> Unit,
    onHistoryClick: () -> Unit,
    onObjPreviewClick: () -> Unit = {},
    onHistoryCompareClick: () -> Unit = {},
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

    // Selection Mode State
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedAssetIds = viewModel.selectedAssetIds

    val context = LocalContext.current

    // Snackbar State
    val snackbarHostState = remember { SnackbarHostState() }

    // Coroutine Scope
    val scope = rememberCoroutineScope()

    // Settings Dialog State
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Theme State
    var currentThemeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }

    // Collect Theme Preferences
    LaunchedEffect(themePreferencesManager) {
        themePreferencesManager?.themeModeFlow?.collect { mode ->
            currentThemeMode = mode
        }
    }

    // File Picker
    val projectPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Acquire persistent read permission for the selected directory
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.scanProject(it)
        }
    }

    // Export Selected Assets
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
                        message = "Exported ${viewModel.selectedAssetIds.size} assets",
                        duration = SnackbarDuration.Short
                    )
                }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Export failed",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    // Settings Dialog
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
                // Selection Mode Top Bar
                TopAppBar(
                    title = { Text("${selectedAssetIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.DoneAll, "Select All")
                        }
                    }
                )
            } else {
                // Normal Mode Top Bar
                TopAppBar(
                    title = {
                        Text(currentProject?.name ?: "UE5 Asset Analyzer")
                    },
                    actions = {
                        // Open Project (primary action)
                        IconButton(onClick = { projectPicker.launch(null) }) {
                            Icon(Icons.Default.FolderOpen, "Open Project")
                        }

                        // Overflow menu for secondary actions
                        var showOverflow by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(Icons.Default.MoreVert, "More")
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("3D Model Preview") },
                                    onClick = { showOverflow = false; onObjPreviewClick() },
                                    leadingIcon = { Icon(Icons.Default.ViewInAr, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Scan History & Compare") },
                                    onClick = { showOverflow = false; onHistoryCompareClick() },
                                    leadingIcon = { Icon(Icons.Default.Compare, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("History Projects") },
                                    onClick = { showOverflow = false; onHistoryClick() },
                                    leadingIcon = { Icon(Icons.Default.History, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Scan Settings") },
                                    onClick = { showOverflow = false; showSettingsDialog = true },
                                    leadingIcon = { Icon(Icons.Default.Build, null) }
                                )
                                if (themePreferencesManager != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Theme: ${
                                                    currentThemeMode.name.lowercase()
                                                        .replaceFirstChar { it.uppercase() }
                                                }"
                                            )
                                        },
                                        onClick = {
                                            showOverflow = false
                                            val newMode = when (currentThemeMode) {
                                                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                                                ThemeMode.LIGHT -> ThemeMode.DARK
                                                ThemeMode.DARK -> ThemeMode.SYSTEM
                                                else -> ThemeMode.SYSTEM
                                            }
                                            scope.launch {
                                                themePreferencesManager.saveThemeMode(
                                                    newMode
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                when (currentThemeMode) {
                                                    ThemeMode.SYSTEM -> Icons.Default.Brightness6
                                                    ThemeMode.LIGHT -> Icons.Default.WbSunny
                                                    ThemeMode.DARK -> Icons.Default.Nightlight
                                                    else -> Icons.Default.Brightness6
                                                }, null
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            // Selection mode bottom action bar
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
                    // Search bar + selection button
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

                        // Selection button
                        FilledTonalIconButton(
                            onClick = { viewModel.toggleSelectionMode() }
                        ) {
                            Icon(
                                imageVector = if (isSelectionMode) Icons.Default.Check else Icons.Default.Checklist,
                                contentDescription = if (isSelectionMode) "Exit selection" else "Select"
                            )
                        }
                    }

                    // Type filter + sort
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Type filter
                        TypeFilter(
                            selectedType = filterType,
                            onTypeSelected = { viewModel.filterByType(it) },
                            modifier = Modifier.weight(1f)
                        )

                        // Sort dropdown
                        SortDropdown(
                            selectedOrder = sortOrder,
                            onOrderSelected = { viewModel.setSortOrder(it) }
                        )
                    }

                    // Show orphan only filter + risk level filter
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
                            label = { Text("Orphan only") },
                            leadingIcon = if (showOrphanOnly) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null
                        )

                        // Risk level filter
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
 * Scan configuration dialog
 */
@Composable
fun ScanConfigDialog(
    currentConfig: ScanConfig,
    onDismiss: () -> Unit,
    onSave: (ScanConfig) -> Unit
) {
    var ignoredDirectories by remember {
        mutableStateOf(
            currentConfig.ignoredDirectories.joinToString(
                "\n"
            )
        )
    }
    var ignoredExtensions by remember {
        mutableStateOf(
            currentConfig.ignoredExtensions.joinToString(
                "\n"
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan Settings") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Ignored directories (one per line)",
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
                    text = "Ignored extensions (one per line, without dots)",
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
            text = "Select a UE5 project to start analysis",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onScanClick) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Project")
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
        // Scan icon
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Scanning project...",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (progress != null) {
            Spacer(modifier = Modifier.height(24.dp))

            // Linear progress bar
            if (progress.totalCount > 0) {
                val progressFloat =
                    (progress.scannedCount.toFloat() / progress.totalCount.toFloat()).coerceIn(
                        0f,
                        1f
                    )
                LinearProgressIndicator(
                    progress = progressFloat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Progress percentage text
                val percentage =
                    ((progress.scannedCount.toFloat() / progress.totalCount.toFloat()) * 100).toInt()
                Text(
                    text = "Scanning: ${progress.scannedCount} / ${progress.totalCount} files ($percentage%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Show indeterminate progress bar when total is unknown
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Scanned ${progress.scannedCount} assets",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Current filename (scrolling display)
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

        // Cancel button
        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cancel Scan")
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
            Text("Retry")
        }
    }
}

/**
 * Asset search bar - with debounce animation hint
 */
@Composable
fun AssetSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isSearching by remember { mutableStateOf(false) }

    // Debounce logic: trigger actual search after 300ms
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
        placeholder = { Text("Search asset name or path...") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            when {
                isSearching && query.isNotEmpty() -> {
                    // Show progress indicator during search
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }

                query.isNotEmpty() -> {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
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
                    Text(type?.displayName ?: "All")
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

    Box(modifier = modifier) {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = {
                Text(
                    selectedOrder.displayName,
                    style = MaterialTheme.typography.labelSmall
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = "Sort",
                    modifier = Modifier.size(16.dp)
                )
            }
        )
        DropdownMenu(
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
                    trailingIcon = if (order == selectedOrder) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
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
                text = "No matching assets found",
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
            // Checkbox in selection mode
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelect?.invoke() },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            // Asset Icon
            Icon(
                imageVector = getTypeIcon(asset.type),
                contentDescription = null,
                tint = if (asset.isOrphan && asset.type == AssetType.LEVEL) {
                    MaterialTheme.colorScheme.tertiary  // Level orphan uses orange
                } else if (asset.isOrphan) {
                    MaterialTheme.colorScheme.error      // Normal orphan uses red
                } else {
                    MaterialTheme.colorScheme.primary   // Normal uses blue
                },
                modifier = Modifier.padding(start = if (isSelectionMode) 0.dp else 8.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Asset name (with highlight)
                Text(
                    text = highlightText(asset.name, searchQuery),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Type
                Text(
                    text = asset.type.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Path (with highlight, only shown when searching)
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

            // Orphan Flag
            if (asset.isOrphan) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Orphan Assets",
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
                text = "$selectedCount / $totalCount selected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row {
                TextButton(onClick = onClearClick) {
                    Text("Clear")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onExportClick) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export")
                }
            }
        }
    }
}

/**
 * Highlight Search Keywords
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
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            ) {
                append(text.substring(matchIndex, matchIndex + query.length))
            }
            currentIndex = matchIndex + query.length
        }
    }
}

/**
 * Get Icon for Asset Type
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
    AssetType.MATERIAL_FUNCTION -> Icons.Default.Build
    AssetType.FUNCTION -> Icons.Default.Build
    AssetType.WORLD_PARTITION -> Icons.Default.ViewModule
    AssetType.UNKNOWN -> Icons.Default.HelpOutline
}

/**
 * Risk Level Filter
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
                Text(selectedLevel?.displayName ?: "Risk Level")
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
            // All option
            DropdownMenuItem(
                text = { Text("All") },
                onClick = {
                    onLevelSelected(null)
                    expanded = false
                },
                leadingIcon = if (selectedLevel == null) {
                    { Icon(Icons.Default.Check, null) }
                } else null
            )

            Divider()

            // Each Risk Level
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

// Extend OrphanRiskLevel with displayName
val OrphanRiskLevel.displayName: String
    get() = when (this) {
        OrphanRiskLevel.NONE -> "None"
        OrphanRiskLevel.LOW -> "Low Risk"
        OrphanRiskLevel.HIGH -> "High Risk"
    }
