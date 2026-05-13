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
 * 主 ViewModel
 * 采用 region 分区组织代码，将不同职责的逻辑分离到不同区域
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    // region 依赖注入
    private val context = application
    
    // 数据库（单例）
    private val db = AppDatabase.getDatabase(context)
    
    // Repository 层（单例，封装数据库和网络的单点访问）
    private val projectRepository = ProjectRepository(db)
    private val assetRepository = AssetRepository(db)
    
    // 解析器
    private val parser = UEProjectParser(context)
    private val analyzer = AssetAnalyzer()
    private val reportGenerator = ReportGenerator(context)
    
    // ScanConfig 管理器
    val scanConfigManager = ScanConfigManager(context)
    
    // 选择管理器
    private val selectionManager = SelectionManager()
    // endregion
    
    // region UI 状态
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    // 扫描进度
    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: StateFlow<ScanProgress?> = _scanProgress.asStateFlow()
    
    // 当前项目
    private val _currentProject = MutableStateFlow<ProjectEntity?>(null)
    val currentProject: StateFlow<ProjectEntity?> = _currentProject.asStateFlow()
    
    // 资产列表
    private val _assets = MutableStateFlow<List<UEAsset>>(emptyList())
    val assets: StateFlow<List<UEAsset>> = _assets.asStateFlow()
    
    // 过滤后的资产
    private val _filteredAssets = MutableStateFlow<List<UEAsset>>(emptyList())
    val filteredAssets: StateFlow<List<UEAsset>> = _filteredAssets.asStateFlow()
    
    // 项目列表（通过 Repository 获取）
    val projects = projectRepository.getAllProjects()
        .map { it.map { entity -> entity.toModel() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    // 当前报告
    private val _currentReport = MutableStateFlow<AnalysisReport?>(null)
    val currentReport: StateFlow<AnalysisReport?> = _currentReport.asStateFlow()
    // endregion
    
    // region 搜索与筛选状态
    // 搜索关键词
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // 筛选类型
    private val _filterType = MutableStateFlow<AssetType?>(null)
    val filterType: StateFlow<AssetType?> = _filterType.asStateFlow()
    
    // 排序方式
    private val _sortOrder = MutableStateFlow(SortOrder.NAME)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()
    
    // 只看孤立资源
    private val _showOrphanOnly = MutableStateFlow(false)
    val showOrphanOnly: StateFlow<Boolean> = _showOrphanOnly.asStateFlow()
    
    // 孤立风险等级筛选
    private val _orphanRiskLevelFilter = MutableStateFlow<OrphanRiskLevel?>(null)
    val orphanRiskLevelFilter: StateFlow<OrphanRiskLevel?> = _orphanRiskLevelFilter.asStateFlow()
    // endregion
    
    // region 选择状态
    // 批量选择状态
    private val _selectedAssetIds = mutableStateListOf<String>()
    val selectedAssetIds: List<String> = _selectedAssetIds
    
    // 选择模式状态
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
    // endregion
    
    // region ScanConfig 状态
    // ScanConfig Flow
    val scanConfigFlow = scanConfigManager.scanConfigFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScanConfig.DEFAULT)
    // endregion
    
    // region 私有变量
    // 扫描协程任务
    private var scanJob: Job? = null
    
    // 过滤操作互斥锁，确保线程安全
    private val filterMutex = Mutex()
    
    // 搜索防抖任务
    private var searchJob: Job? = null
    // endregion
    
    // region 扫描相关方法
    /**
     * 扫描项目
     */
    fun scanProject(uri: Uri) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.value = UiState.Scanning
            _scanProgress.value = ScanProgress(scannedCount = 0, totalCount = 0, currentFile = "")
            
            try {
                // SAF 权限持久化检查
                val hasPermission = context.contentResolver.persistedUriPermissions
                    .any { it.uri == uri && it.isReadPermission }
                if (!hasPermission) {
                    _scanProgress.value = null
                    _uiState.value = UiState.Error("存储权限已过期，请重新选择项目文件夹", ErrorReason.PERMISSION_DENIED)
                    return@launch
                }
                
                // 带进度回调的扫描
                val scanResult = parser.scanProject(
                    projectUri = uri,
                    onProgress = { count: Int, total: Int, name: String ->
                        _scanProgress.value = ScanProgress(scannedCount = count, totalCount = total, currentFile = name)
                    }
                )
                
                // 保存项目
                // 使用 Repository 直接查询，避免加载所有项目后过滤
                val existingProject = projectRepository.getProjectByName(scanResult.projectName)
                
                val projectId = if (existingProject != null) {
                    // 更新已有项目
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
                
                // 保存所有资产（而非只存孤立资产）
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
                
                // 更新资产列表（使用所有资产）
                _assets.value = scanResult.allAssets
                
                // 生成报告
                val report = analyzer.generateReport(
                    scanResult.projectPath,
                    scanResult.projectName,
                    scanResult.allAssets  // 使用所有资产生成报告
                )
                _currentReport.value = report
                
                // 清除进度
                _scanProgress.value = null
                
                _uiState.value = UiState.Success(scanResult)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _scanProgress.value = null
                _uiState.value = UiState.Idle
            } catch (e: java.io.IOException) {
                _scanProgress.value = null
                _uiState.value = UiState.Error("文件读取失败: ${e.message}", ErrorReason.IO_ERROR)
            } catch (e: Exception) {
                _scanProgress.value = null
                _uiState.value = UiState.Error(e.message ?: "扫描失败", ErrorReason.UNKNOWN)
            }
        }
    }
    
    /**
     * 带配置扫描项目
     */
    fun scanProjectWithConfig(uri: Uri, config: ScanConfig) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.value = UiState.Scanning
            _scanProgress.value = ScanProgress(scannedCount = 0, totalCount = 0, currentFile = "")
            
            try {
                // SAF 权限持久化检查
                val hasPermission = context.contentResolver.persistedUriPermissions
                    .any { it.uri == uri && it.isReadPermission }
                if (!hasPermission) {
                    _scanProgress.value = null
                    _uiState.value = UiState.Error("存储权限已过期，请重新选择项目文件夹", ErrorReason.PERMISSION_DENIED)
                    return@launch
                }
                
                // 带进度回调的扫描（传入 ScanConfig）
                val scanResult = parser.scanProject(
                    projectUri = uri,
                    onProgress = { count: Int, total: Int, name: String ->
                        _scanProgress.value = ScanProgress(scannedCount = count, totalCount = total, currentFile = name)
                    },
                    scanConfig = config
                )
                
                // 保存项目
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
                
                // 保存所有资产
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
                
                // 更新资产列表
                _assets.value = scanResult.allAssets
                
                // 生成报告
                val report = analyzer.generateReport(
                    scanResult.projectPath,
                    scanResult.projectName,
                    scanResult.allAssets
                )
                _currentReport.value = report
                
                // 清除进度
                _scanProgress.value = null
                
                _uiState.value = UiState.Success(scanResult)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _scanProgress.value = null
                _uiState.value = UiState.Idle
            } catch (e: java.io.IOException) {
                _scanProgress.value = null
                _uiState.value = UiState.Error("文件读取失败: ${e.message}", ErrorReason.IO_ERROR)
            } catch (e: Exception) {
                _scanProgress.value = null
                _uiState.value = UiState.Error(e.message ?: "扫描失败", ErrorReason.UNKNOWN)
            }
        }
    }
    
    /**
     * 取消扫描
     */
    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _scanProgress.value = null
        _uiState.value = UiState.Idle
    }
    
    /**
     * 加载项目
     */
    fun loadProject(projectId: String) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            _currentProject.value = project
            
            val assetEntities = assetRepository.getAssetsByProject(projectId).first()
            val loadedAssets = assetEntities.map { it.toModel() }
            _assets.value = loadedAssets
            
            // 重新生成报告
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
     * 删除项目
     */
    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            assetRepository.deleteAssetsByProject(projectId)
            projectRepository.deleteProjectById(projectId)
        }
    }
    // endregion
    
    // region ScanConfig 方法
    /**
     * 更新 ScanConfig
     */
    fun updateScanConfig(config: ScanConfig) {
        viewModelScope.launch {
            scanConfigManager.saveScanConfig(config)
        }
    }
    
    /**
     * 更新忽略目录
     */
    fun updateIgnoredDirectories(directories: Set<String>) {
        viewModelScope.launch {
            scanConfigManager.updateIgnoredDirectories(directories)
        }
    }
    
    /**
     * 更新忽略扩展名
     */
    fun updateIgnoredExtensions(extensions: Set<String>) {
        viewModelScope.launch {
            scanConfigManager.updateIgnoredExtensions(extensions)
        }
    }
    // endregion
    
    // region 搜索与筛选方法
    /**
     * 搜索资产（带300ms防抖）
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
     * 筛选类型
     */
    fun filterByType(type: AssetType?) {
        _filterType.value = type
        applyFilters()
    }
    
    /**
     * 设置排序方式
     */
    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        applyFilters()
    }
    
    /**
     * 设置是否只看孤立资源
     */
    fun setShowOrphanOnly(show: Boolean) {
        _showOrphanOnly.value = show
        applyFilters()
    }
    
    /**
     * 设置孤立风险等级筛选
     */
    fun setOrphanRiskLevelFilter(level: OrphanRiskLevel?) {
        _orphanRiskLevelFilter.value = level
        applyFilters()
    }
    
    /**
     * 应用过滤条件和排序
     * 使用 Mutex 确保线程安全，防止并发访问冲突
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
    
    // region 报告方法
    /**
     * 导出报告
     */
    fun exportReport(uri: Uri): Boolean {
        val report = _currentReport.value ?: return false
        return reportGenerator.exportReport(report, uri)
    }
    
    /**
     * 分享报告
     */
    fun shareReport() = _currentReport.value?.let { 
        reportGenerator.shareReport(it) 
    }
    // endregion
    
    // region 选择相关方法
    /**
     * 切换选择模式
     */
    fun toggleSelectionMode() {
        if (_isSelectionMode.value) {
            // 退出选择模式时清空选择
            clearSelection()
        }
        _isSelectionMode.value = !_isSelectionMode.value
    }
    
    /**
     * 退出选择模式
     */
    fun exitSelectionMode() {
        clearSelection()
        _isSelectionMode.value = false
    }
    
    /**
     * 切换资源选择状态
     */
    fun toggleAssetSelection(assetId: String) {
        if (_selectedAssetIds.contains(assetId)) {
            _selectedAssetIds.remove(assetId)
        } else {
            _selectedAssetIds.add(assetId)
        }
    }
    
    /**
     * 清空所有选择
     */
    fun clearSelection() {
        _selectedAssetIds.clear()
    }
    
    /**
     * 全选当前显示的资源
     */
    fun selectAll() {
        _selectedAssetIds.clear()
        _selectedAssetIds.addAll(_filteredAssets.value.map { it.id })
    }
    
    /**
     * 获取选中的资源对象列表
     */
    fun getSelectedAssets(): List<UEAsset> {
        return _filteredAssets.value.filter { _selectedAssetIds.contains(it.id) }
    }
    
    /**
     * 导出选中资源列表为 Markdown 格式
     */
    fun exportSelectedAssets(): String {
        val selectedAssets = getSelectedAssets()
        if (selectedAssets.isEmpty()) {
            return "# 选中资源列表\n\n未选择任何资源"
        }
        
        val totalSize = selectedAssets.sumOf { it.size }
        val builder = StringBuilder()
        builder.appendLine("# 选中资源列表")
        builder.appendLine()
        builder.appendLine("**总计**: ${selectedAssets.size} 个资源")
        builder.appendLine("**总大小**: ${formatFileSize(totalSize)}")
        builder.appendLine()
        builder.appendLine("---")
        builder.appendLine()
        builder.appendLine("| 名称 | 类型 | 大小 | 路径 |")
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
        builder.appendLine("* 由 UE5 Asset Analyzer 生成 *")
        
        return builder.toString()
    }
    // endregion
    
    // region 工具方法
    /**
     * 格式化文件大小
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

// region 数据类和扩展
/**
 * UI 状态
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
    PERMISSION_DENIED,   // SAF 权限过期
    PROJECT_EMPTY,       // 项目没有 .uasset 文件
    PARSE_FAILED,        // 解析失败
    IO_ERROR,           // IO 错误
    UNKNOWN             // 未知错误
}

/**
 * 扫描进度
 */
data class ScanProgress(
    val scannedCount: Int,
    val totalCount: Int = 0,
    val currentFile: String
)

/**
 * 排序方式
 */
enum class SortOrder(val displayName: String) {
    NAME("按名称"),
    SIZE_DESC("按大小"),
    REFS_DESC("按引用数"),
    TYPE("按类型")
}

/**
 * 扩展函数：实体转模型
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
    // 使用 entries.find 替代 valueOf，防止未知类型抛异常
    type = AssetType.entries.find { it.name == type } ?: AssetType.UNKNOWN,
    size = size,
    dependencies = Json.decodeFromString(dependencies),
    references = Json.decodeFromString(references),
    isOrphan = isOrphan,
    // 使用 entries.find 替代 valueOf，防止未知风险等级抛异常
    orphanRiskLevel = OrphanRiskLevel.entries.find { it.name == orphanRiskLevel } ?: OrphanRiskLevel.NONE,
    lastModified = lastModified
)

/**
 * 项目信息（简化版）
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
