package com.example.ue5analyzer.data.filter

import com.example.ue5analyzer.model.AssetType
import com.example.ue5analyzer.model.OrphanRiskLevel
import com.example.ue5analyzer.model.UEAsset
import com.example.ue5analyzer.ui.viewmodel.SortOrder

/**
 * 资产过滤工具类
 * 负责资产的搜索、筛选、排序逻辑
 */
object AssetFilter {
    
    /**
     * 过滤资产列表
     */
    fun filter(
        assets: List<UEAsset>,
        searchQuery: String,
        filterType: AssetType?,
        showOrphanOnly: Boolean,
        orphanRiskLevel: OrphanRiskLevel?,
        sortOrder: SortOrder
    ): List<UEAsset> {
        var result = assets
        
        // 搜索过滤（支持名称和路径搜索）
        if (searchQuery.isNotEmpty()) {
            val query = searchQuery
            result = result.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.path.contains(query, ignoreCase = true) 
            }
        }
        
        // 类型过滤
        if (filterType != null) {
            result = result.filter { it.type == filterType }
        }
        
        // 孤立资源过滤
        if (showOrphanOnly) {
            result = result.filter { it.isOrphan }
        }
        
        // 孤立风险等级筛选
        if (orphanRiskLevel != null) {
            result = result.filter { it.orphanRiskLevel == orphanRiskLevel }
        }
        
        // 排序
        result = when (sortOrder) {
            SortOrder.NAME -> result.sortedBy { it.name.lowercase() }
            SortOrder.SIZE_DESC -> result.sortedByDescending { it.size }
            SortOrder.REFS_DESC -> result.sortedByDescending { it.references.size }
            SortOrder.TYPE -> result.sortedBy { it.type.name }
        }
        
        return result
    }
}
