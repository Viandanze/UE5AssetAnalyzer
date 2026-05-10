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

/**
 * 主 ViewModel
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
                // 带进度回调的扫描
                val scanResult = parser.scanProject(uri) { count, total, name ->
                    _scanProgress.value = ScanProgress(scannedCount = count, totalCount = total, currentFile = name)
                }
                
                // 保存项目
                // 先查询是否已有同名项目
                val existingProject = db.projectDao().getAllProjects().first()
                    .find { it.name == scanResult.projectName }
                
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
            } catch (e: Exception) {
                _scanProgress.value = null
                _uiState.value = UiState.Error(e.message ?: "扫描失败")
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
     */
    private fun applyFilters() {
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
}

/**
 * UI 状态
 */
sealed class UiState {
    object Idle : UiState()
    object Scanning : UiState()
    data class Success(val result: ScanResult) : UiState()
    data class Error(val message: String) : UiState()
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
    type = AssetType.valueOf(type),
    size = size,
    dependencies = Json.decodeFromString(dependencies),
    references = Json.decodeFromString(references),
    isOrphan = isOrphan,
    orphanRiskLevel = OrphanRiskLevel.valueOf(orphanRiskLevel),
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
