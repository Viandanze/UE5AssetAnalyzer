package com.example.ue5analyzer.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ue5analyzer.data.database.*
import com.example.ue5analyzer.data.filter.AssetFilter
import com.example.ue5analyzer.data.manager.ScanConfigManager
import com.example.ue5analyzer.data.parser.UEProjectParser
import com.example.ue5analyzer.data.repository.AssetRepository
import com.example.ue5analyzer.data.repository.ProjectRepository
import com.example.ue5analyzer.data.selection.SelectionManager
import com.example.ue5analyzer.domain.analyzer.AssetAnalyzer
import com.example.ue5analyzer.domain.report.ReportGenerator
import com.example.ue5analyzer.model.AssetType
import com.example.ue5analyzer.model.OrphanRiskLevel
import com.example.ue5analyzer.model.AnalysisReport
import com.example.ue5analyzer.model.ScanConfig
import com.example.ue5analyzer.model.ScanResult
import com.example.ue5analyzer.model.UEAsset
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.mutableStateListOf

/**
 * Main ViewModel
 * Organized with regions to separate different responsibilities
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    // region Dependency injection
    private val context = application
    
    // Database (Singleton)
    private val db = AppDatabase.getDatabase(context)
    
    // Repository Layer (Singleton, encapsulates single-point database and network access)
    private val projectRepository = ProjectRepository(db)
    private val assetRepository = AssetRepository(db)
    
    // Parser
    private val parser = UEProjectParser(context)
    private val analyzer = AssetAnalyzer()
    private val reportGenerator = ReportGenerator(context)
    
    // ScanConfig Manager
    val scanConfigManager = ScanConfigManager(context)
    
    // Selection Manager
    private val selectionManager = SelectionManager()
    // endregion
    
    // region UI state
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    // Scan Progress
    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: StateFlow<ScanProgress?> = _scanProgress.asStateFlow()
    
    // Current Project
    private val _currentProject = MutableStateFlow<ProjectEntity?>(null)
    val currentProject: StateFlow<ProjectEntity?> = _currentProject.asStateFlow()
    
    // Asset List
    private val _assets = MutableStateFlow<List<UEAsset>>(emptyList())
    val assets: StateFlow<List<UEAsset>> = _assets.asStateFlow()
    
    // Filtered Assets
    private val _filteredAssets = MutableStateFlow<List<UEAsset>>(emptyList())
    val filteredAssets: StateFlow<List<UEAsset>> = _filteredAssets.asStateFlow()
    
    // Project List (obtained via Repository)
    val projects = projectRepository.getAllProjects()
        .map { it.map { entity -> entity.toModel() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    // Current Report
    private val _currentReport = MutableStateFlow<AnalysisReport?>(null)
    val currentReport: StateFlow<AnalysisReport?> = _currentReport.asStateFlow()
    // endregion
    
    // region Search and filter state
    // Search Keyword
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // Filter by Type
    private val _filterType = MutableStateFlow<AssetType?>(null)
    val filterType: StateFlow<AssetType?> = _filterType.asStateFlow()
    
    // Sort Order
    private val _sortOrder = MutableStateFlow(SortOrder.NAME)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()
    
    // Show Orphan Only
    private val _showOrphanOnly = MutableStateFlow(false)
    val showOrphanOnly: StateFlow<Boolean> = _showOrphanOnly.asStateFlow()
    
    // Orphan Risk Level Filter
    private val _orphanRiskLevelFilter = MutableStateFlow<OrphanRiskLevel?>(null)
    val orphanRiskLevelFilter: StateFlow<OrphanRiskLevel?> = _orphanRiskLevelFilter.asStateFlow()
    // endregion
    
    // region Selection state
    // Batch Selection State
    private val _selectedAssetIds = mutableStateListOf<String>()
    val selectedAssetIds: List<String> = _selectedAssetIds
    
    // Selection Mode State
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
    // endregion
    
    // region ScanConfig state
    // ScanConfig Flow
    val scanConfigFlow = scanConfigManager.scanConfigFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScanConfig.DEFAULT)
    // endregion
    
    // region Private variables
    // Scan Coroutine Job
    private var scanJob: Job? = null
    
    // Filter operation mutex for thread safety
    private val filterMutex = Mutex()
    
    // Search Debounce Job
    private var searchJob: Job? = null
    // endregion
    
    // region Scan related methods
    /**
     * Scan Project
     */
    fun scanProject(uri: Uri) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.value = UiState.Scanning
            _scanProgress.value = ScanProgress(scannedCount = 0, totalCount = 0, currentFile = "")
            
            try {
                // SAF Permission Persistence Check
                val hasPermission = context.contentResolver.persistedUriPermissions
                    .any { it.uri == uri && it.isReadPermission }
                if (!hasPermission) {
                    _scanProgress.value = null
                    _uiState.value = UiState.Error("Storage permission expired. Please select the project folder again.", ErrorReason.PERMISSION_DENIED)
                    return@launch
                }
                
                // Scanning with progress callback
                val scanResult = parser.scanProject(
                    projectUri = uri,
                    onProgress = { count: Int, total: Int, name: String ->
                        _scanProgress.value = ScanProgress(scannedCount = count, totalCount = total, currentFile = name)
                    }
                )
                
                // Save Project
                // Use Repository for direct query instead of loading all projects then filtering
                val existingProject = projectRepository.getProjectByName(scanResult.projectName)
                
                val projectId = if (existingProject != null) {
                    // Update existing project
                    assetRepository.deleteAssetsByProject(existingProject.id)
                    projectRepository.insertProject(existingProject.copy(
                        totalAssets = scanResult.totalAssets,
                        totalSize = scanResult.totalSize,
                        lastScanned = System.currentTimeMillis()
                    ))
                    existingProject.id
                } else {
                    UUID.randomUUID().toString()
                }
                
                val projectEntity = ProjectEntity(
                    id = projectId,
                    name = scanResult.projectName,
                    path = scanResult.projectPath,
                    totalAssets = scanResult.totalAssets,
                    totalSize = scanResult.totalSize,
                    lastScanned = System.currentTimeMillis()
                )
                projectRepository.insertProject(projectEntity)
                _currentProject.value = projectEntity
                
                // Save all assets (not just orphan assets)
                val assetEntities = scanResult.allAssets.map { asset ->
                    AssetEntity(
                        id = asset.id,
                        name = asset.name,
                        path = asset.path,
                        type = asset.type.name,
                        size = asset.size,
                        dependencies = Json.encodeToString(asset.dependencies),
                        references = Json.encodeToString(asset.references),
                        isOrphan = asset.isOrphan,
                        orphanRiskLevel = asset.orphanRiskLevel.name,
                        lastModified = asset.lastModified,
                        projectId = projectId
                    )
                }
                assetRepository.insertAssets(assetEntities)
                
                // Update asset list (use all assets)
                _assets.value = scanResult.allAssets
                
                // Generate report
                val report = analyzer.generateReport(
                    scanResult.projectPath,
                    scanResult.projectName,
                    scanResult.allAssets  // Generate report using all assets
                )
                _currentReport.value = report
                
                // Clear progress
                _scanProgress.value = null
                
                _uiState.value = UiState.Success(scanResult)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _scanProgress.value = null
                _uiState.value = UiState.Idle
            } catch (e: java.io.IOException) {
                _scanProgress.value = null
                _uiState.value = UiState.Error("File read failed: ${e.message}", ErrorReason.IO_ERROR)
            } catch (e: Exception) {
                _scanProgress.value = null
                _uiState.value = UiState.Error(e.message ?: "Scan failed", ErrorReason.UNKNOWN)
            }
        }
    }
    
    /**
     * Scan project with config
     */
    fun scanProjectWithConfig(uri: Uri, config: ScanConfig) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.value = UiState.Scanning
            _scanProgress.value = ScanProgress(scannedCount = 0, totalCount = 0, currentFile = "")
            
            try {
                // SAF Permission Persistence Check
                val hasPermission = context.contentResolver.persistedUriPermissions
                    .any { it.uri == uri && it.isReadPermission }
                if (!hasPermission) {
                    _scanProgress.value = null
                    _uiState.value = UiState.Error("Storage permission expired. Please select the project folder again.", ErrorReason.PERMISSION_DENIED)
                    return@launch
                }
                
                // Scanning with progress callback (with ScanConfig)
                val scanResult = parser.scanProject(
                    projectUri = uri,
                    onProgress = { count: Int, total: Int, name: String ->
                        _scanProgress.value = ScanProgress(scannedCount = count, totalCount = total, currentFile = name)
                    },
                    scanConfig = config
                )
                
                // Save Project
                val existingProject = projectRepository.getProjectByName(scanResult.projectName)
                
                val projectId = if (existingProject != null) {
                    assetRepository.deleteAssetsByProject(existingProject.id)
                    projectRepository.insertProject(existingProject.copy(
                        totalAssets = scanResult.totalAssets,
                        totalSize = scanResult.totalSize,
                        lastScanned = System.currentTimeMillis()
                    ))
                    existingProject.id
                } else {
                    UUID.randomUUID().toString()
                }
                
                val projectEntity = ProjectEntity(
                    id = projectId,
                    name = scanResult.projectName,
                    path = scanResult.projectPath,
                    totalAssets = scanResult.totalAssets,
                    totalSize = scanResult.totalSize,
                    lastScanned = System.currentTimeMillis()
                )
                projectRepository.insertProject(projectEntity)
                _currentProject.value = projectEntity
                
                // Save all assets
                val assetEntities = scanResult.allAssets.map { asset ->
                    AssetEntity(
                        id = asset.id,
                        name = asset.name,
                        path = asset.path,
                        type = asset.type.name,
                        size = asset.size,
                        dependencies = Json.encodeToString(asset.dependencies),
                        references = Json.encodeToString(asset.references),
                        isOrphan = asset.isOrphan,
                        orphanRiskLevel = asset.orphanRiskLevel.name,
                        lastModified = asset.lastModified,
                        projectId = projectId
                    )
                }
                assetRepository.insertAssets(assetEntities)
                
                // Update asset list
                _assets.value = scanResult.allAssets
                
                // Generate report
                val report = analyzer.generateReport(
                    scanResult.projectPath,
                    scanResult.projectName,
                    scanResult.allAssets
                )
                _currentReport.value = report
                
                // Clear progress
                _scanProgress.value = null
                
                _uiState.value = UiState.Success(scanResult)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _scanProgress.value = null
                _uiState.value = UiState.Idle
            } catch (e: java.io.IOException) {
                _scanProgress.value = null
                _uiState.value = UiState.Error("File read failed: ${e.message}", ErrorReason.IO_ERROR)
            } catch (e: Exception) {
                _scanProgress.value = null
                _uiState.value = UiState.Error(e.message ?: "Scan failed", ErrorReason.UNKNOWN)
            }
        }
    }
    
    /**
     * Cancel Scan
     */
    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _scanProgress.value = null
        _uiState.value = UiState.Idle
    }
    
    /**
     * Load Project
     */
    fun loadProject(projectId: String) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            _currentProject.value = project
            
            val assetEntities = assetRepository.getAssetsByProject(projectId).first()
            val loadedAssets = assetEntities.map { it.toModel() }
            _assets.value = loadedAssets
            
            // Regenerate Report
            val report = analyzer.generateReport(
                project?.path ?: "",
                project?.name ?: "",
                loadedAssets
            )
            _currentReport.value = report
            
            _uiState.value = UiState.Success(ScanResult(
                projectPath = project?.path ?: "",
                projectName = project?.name ?: "",
                totalAssets = loadedAssets.size,
                totalSize = loadedAssets.sumOf { it.size },
                assetsByType = loadedAssets.groupingBy { it.type }.eachCount(),
                allAssets = loadedAssets,
                orphanAssets = loadedAssets.filter { it.isOrphan }
            ))
            
            applyFilters()
        }
    }
    
    /**
     * Delete Project
     */
    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            assetRepository.deleteAssetsByProject(projectId)
            projectRepository.deleteProjectById(projectId)
        }
    }
    // endregion
    
    // region ScanConfig methods
    /**
     * Update ScanConfig
     */
    fun updateScanConfig(config: ScanConfig) {
        viewModelScope.launch {
            scanConfigManager.saveScanConfig(config)
        }
    }
    
    /**
     * Update Ignored Directories
     */
    fun updateIgnoredDirectories(directories: Set<String>) {
        viewModelScope.launch {
            scanConfigManager.updateIgnoredDirectories(directories)
        }
    }
    
    /**
     * Update Ignored Extensions
     */
    fun updateIgnoredExtensions(extensions: Set<String>) {
        viewModelScope.launch {
            scanConfigManager.updateIgnoredExtensions(extensions)
        }
    }
    // endregion
    
    // region Search and filter methods
    /**
     * Search Assets (with 300ms debounce)
     */
    fun searchAssets(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            applyFilters()
        }
    }
    
    /**
     * Filter by Type
     */
    fun filterByType(type: AssetType?) {
        _filterType.value = type
        applyFilters()
    }
    
    /**
     * Set Sort Order
     */
    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        applyFilters()
    }
    
    /**
     * Set Show Orphan Only
     */
    fun setShowOrphanOnly(show: Boolean) {
        _showOrphanOnly.value = show
        applyFilters()
    }
    
    /**
     * Set Orphan Risk Level Filter
     */
    fun setOrphanRiskLevelFilter(level: OrphanRiskLevel?) {
        _orphanRiskLevelFilter.value = level
        applyFilters()
    }
    
    /**
     * Apply Filters and Sort
     * Use Mutex for thread safety, preventing concurrent access conflicts
     */
    private fun applyFilters() {
        viewModelScope.launch {
            filterMutex.withLock {
                val result = AssetFilter.filter(
                    assets = _assets.value,
                    searchQuery = _searchQuery.value,
                    filterType = _filterType.value,
                    showOrphanOnly = _showOrphanOnly.value,
                    orphanRiskLevel = _orphanRiskLevelFilter.value,
                    sortOrder = _sortOrder.value
                )
                
                _filteredAssets.value = result
            }
        }
    }
    // endregion
    
    // region Report methods
    /**
     * Export Report
     */
    fun exportReport(uri: Uri): Boolean {
        val report = _currentReport.value ?: return false
        return reportGenerator.exportReport(report, uri)
    }
    
    /**
     * Share Report
     */
    fun shareReport() = _currentReport.value?.let { 
        reportGenerator.shareReport(it) 
    }
    // endregion
    
    // region Selection related methods
    /**
     * Toggle Selection Mode
     */
    fun toggleSelectionMode() {
        if (_isSelectionMode.value) {
            // Clear selection when exiting selection mode
            clearSelection()
        }
        _isSelectionMode.value = !_isSelectionMode.value
    }
    
    /**
     * Exit Selection Mode
     */
    fun exitSelectionMode() {
        clearSelection()
        _isSelectionMode.value = false
    }
    
    /**
     * Toggle Asset Selection
     */
    fun toggleAssetSelection(assetId: String) {
        if (_selectedAssetIds.contains(assetId)) {
            _selectedAssetIds.remove(assetId)
        } else {
            _selectedAssetIds.add(assetId)
        }
    }
    
    /**
     * Clear All Selection
     */
    fun clearSelection() {
        _selectedAssetIds.clear()
    }
    
    /**
     * Select All Currently Displayed Assets
     */
    fun selectAll() {
        _selectedAssetIds.clear()
        _selectedAssetIds.addAll(_filteredAssets.value.map { it.id })
    }
    
    /**
     * Get Selected Asset List
     */
    fun getSelectedAssets(): List<UEAsset> {
        return _filteredAssets.value.filter { _selectedAssetIds.contains(it.id) }
    }
    
    /**
     * Export Selected Assets as Markdown
     */
    fun exportSelectedAssets(): String {
        val selectedAssets = getSelectedAssets()
        if (selectedAssets.isEmpty()) {
            return "# Selected Assets\n\nNo assets selected"
        }
        
        val totalSize = selectedAssets.sumOf { it.size }
        val builder = StringBuilder()
        builder.appendLine("# Selected Assets")
        builder.appendLine()
        builder.appendLine("**Total**: ${selectedAssets.size} assets")
        builder.appendLine("**Total Size**: ${formatFileSize(totalSize)}")
        builder.appendLine()
        builder.appendLine("---")
        builder.appendLine()
        builder.appendLine("| Name | Type | Size | Path |")
        builder.appendLine("|------|------|------|------|")
        
        selectedAssets.sortedBy { it.name.lowercase() }.forEach { asset ->
            val name = asset.name
            val type = asset.type.displayName
            val size = formatFileSize(asset.size)
            val path = asset.path
            builder.appendLine("| $name | $type | $size | $path |")
        }
        
        builder.appendLine()
        builder.appendLine("---")
        builder.appendLine("* Generated by UE5 Asset Analyzer *")
        
        return builder.toString()
    }
    // endregion
    
    // region Utility methods
    /**
     * Format File Size
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
    // endregion
}

// region Data classes and extensions
/**
 * UI State
 */
sealed class UiState {
    object Idle : UiState()
    object Scanning : UiState()
    data class Success(val result: ScanResult) : UiState()
    data class Error(
        val message: String,
        val reason: ErrorReason = ErrorReason.UNKNOWN
    ) : UiState()
}

enum class ErrorReason {
    PERMISSION_DENIED,   // SAF Permission Expired
    PROJECT_EMPTY,       // Project has no .uasset files
    PARSE_FAILED,        // Parse Failed
    IO_ERROR,           // IO Error
    UNKNOWN             // Unknown error
}

/**
 * Scan Progress
 */
data class ScanProgress(
    val scannedCount: Int,
    val totalCount: Int = 0,
    val currentFile: String
)

/**
 * Sort Order
 */
enum class SortOrder(val displayName: String) {
    NAME("By Name"),
    SIZE_DESC("By Size"),
    REFS_DESC("By References"),
    TYPE("By Type")
}

/**
 * Extension Function: Entity to Model
 */
fun ProjectEntity.toModel() = ProjectInfo(
    id = id,
    name = name,
    path = path,
    totalAssets = totalAssets,
    totalSize = totalSize,
    lastScanned = lastScanned
)

fun AssetEntity.toModel() = UEAsset(
    id = id,
    name = name,
    path = path,
    // Use entries.find instead of valueOf to prevent unknown types throwing exceptions
    type = AssetType.entries.find { it.name == type } ?: AssetType.UNKNOWN,
    size = size,
    dependencies = Json.decodeFromString(dependencies),
    references = Json.decodeFromString(references),
    isOrphan = isOrphan,
    // Use entries.find instead of valueOf to prevent unknown risk levels throwing exceptions
    orphanRiskLevel = OrphanRiskLevel.entries.find { it.name == orphanRiskLevel } ?: OrphanRiskLevel.NONE,
    lastModified = lastModified
)

/**
 * Project Info (Simplified)
 */
data class ProjectInfo(
    val id: String,
    val name: String,
    val path: String,
    val totalAssets: Int,
    val totalSize: Long,
    val lastScanned: Long
)
// endregion
