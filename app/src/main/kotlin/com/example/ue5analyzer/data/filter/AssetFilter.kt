package com.example.ue5analyzer.data.filter

import com.example.ue5analyzer.model.AssetType
import com.example.ue5analyzer.model.OrphanRiskLevel
import com.example.ue5analyzer.model.UEAsset
import com.example.ue5analyzer.ui.viewmodel.SortOrder

/**
 * Asset Filter Utilities
 * Responsible for asset search, filter, and sort logic
 */
object AssetFilter {
    
    /**
     * Filter Asset List
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
        
        // Search filter (supports name and path search)
        if (searchQuery.isNotEmpty()) {
            val query = searchQuery
            result = result.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.path.contains(query, ignoreCase = true) 
            }
        }
        
        // Type filter
        if (filterType != null) {
            result = result.filter { it.type == filterType }
        }
        
        // Orphan asset filter
        if (showOrphanOnly) {
            result = result.filter { it.isOrphan }
        }
        
        // Orphan Risk Level Filter
        if (orphanRiskLevel != null) {
            result = result.filter { it.orphanRiskLevel == orphanRiskLevel }
        }
        
        // Sort
        result = when (sortOrder) {
            SortOrder.NAME -> result.sortedBy { it.name.lowercase() }
            SortOrder.SIZE_DESC -> result.sortedByDescending { it.size }
            SortOrder.REFS_DESC -> result.sortedByDescending { it.references.size }
            SortOrder.TYPE -> result.sortedBy { it.type.name }
        }
        
        return result
    }
}
