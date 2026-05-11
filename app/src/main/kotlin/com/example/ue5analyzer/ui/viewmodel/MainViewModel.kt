package com.example.ue5analyzer.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ue5analyzer.data.database.*
import com.example.ue5analyzer.data.parser.UEProjectParser
import com.example.ue5analyzer.domain.analyzer.AssetAnalyzer
import com.example.ue5analyzer.domain.report.ReportGenerator
import com.example.ue5analyzer.model.AssetType
import com.example.ue5analyzer.model.OrphanRiskLevel
import com.example.ue5analyzer.model.AnalysisReport
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

/**
 * 主 ViewModel
 * TODO: 考虑拆分为多个 ViewModel（如 ScanViewModel、FilterViewModel）以降低职责耦合度
 * 当前设计将扫描、过滤、排序逻辑集中在此，便于管理但可能导致维护困难
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context = application
    
    // 数据库（单例）
    private val db = AppDatabase.getDatabase(context)
    
    // 解析器
    private val parser = UEProjectParser(context)
    private val analyzer = AssetAnalyzer()
    private val reportGenerator = ReportGenerator(context)
    
    // UI 状态
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
    
    // 项目列表
    val projects = db.projectDao().getAllProjects()
        .map { it.map { entity -> entity.toModel() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    // 当前报告
    private val _currentReport = MutableStateFlow<AnalysisReport?>(null)
    val currentReport: StateFlow<AnalysisReport?> = _currentReport.asStateFlow()
    
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
    
    // 扫描协程任务
    private var scanJob: Job? = null
    
    // 过滤操作互斥锁，确保线程安全
    private val filterMutex = Mutex()
    
    // 批量选择状态
    private val _selectedAssetIds = mutableStateListOf<String>()
    val selectedAssetIds: List<String> = _selectedAssetIds
    
    // 选择模式状态
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
    
    /**
     * 设置孤立风险等级筛选
     */
    fun setOrphanRiskLevelFilter(level: OrphanRiskLevel?) {
        _orphanRiskLevelFilter.value = level
        applyFilters()
    }
    
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
                val scanResult = parser.scanProject(uri) { count, total, name ->
                    _scanProgress.value = ScanProgress(scannedCount = count, totalCount = total, currentFile = name)
                }
                
                // 保存项目
                // 使用 getProjectByName 直接查询，避免加载所有项目后过滤
                val existingProject = db.projectDao().getProjectByName(scanResult.projectName)
                
                val projectId = if (existingProject != null) {
                    // 更新已有项目
                    db.assetDao().deleteAssetsByProject(existingProject.id)
                    db.projectDao().insertProject(existingProject.copy(
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
                db.projectDao().insertProject(projectEntity)
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
                db.assetDao().insertAssets(assetEntities)
                
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
            val project = db.projectDao().getProjectById(projectId)
            _currentProject.value = project
            
            val assetEntities = db.assetDao().getAssetsByProject(projectId).first()
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
            db.assetDao().deleteAssetsByProject(projectId)
            db.projectDao().deleteProjectById(projectId)
        }
    }
    
    /**
     * 搜索资产（带300ms防抖）
     */
    private var searchJob: Job? = null
    
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
     * 应用过滤条件和排序
     * 使用 Mutex 确保线程安全，防止并发访问冲突
     */
    private fun applyFilters() {
        viewModelScope.launch {
            filterMutex.withLock {
                var result = _assets.value
                
                // 搜索过滤（支持名称和路径搜索）
                if (_searchQuery.value.isNotEmpty()) {
                    val query = _searchQuery.value
                    result = result.filter { 
                        it.name.contains(query, ignoreCase = true) || 
                        it.path.contains(query, ignoreCase = true) 
                    }
                }
                
                // 类型过滤
                _filterType.value?.let { type ->
                    result = result.filter { it.type == type }
                }
                
                // 孤立资源过滤
                if (_showOrphanOnly.value) {
                    result = result.filter { it.isOrphan }
                }
                
                // 孤立风险等级筛选
                _orphanRiskLevelFilter.value?.let { level ->
                    result = result.filter { it.orphanRiskLevel == level }
                }
                
                // 排序
                result = when (_sortOrder.value) {
                    SortOrder.NAME -> result.sortedBy { it.name.lowercase() }
                    SortOrder.SIZE_DESC -> result.sortedByDescending { it.size }
                    SortOrder.REFS_DESC -> result.sortedByDescending { it.references.size }
                    SortOrder.TYPE -> result.sortedBy { it.type.name }
                }
                
                _filteredAssets.value = result
            }
        }
    }
    
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
}

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
