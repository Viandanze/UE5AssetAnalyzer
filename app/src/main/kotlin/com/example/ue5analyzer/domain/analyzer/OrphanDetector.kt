package com.example.ue5analyzer.domain.analyzer

import com.example.ue5analyzer.model.*
import com.example.ue5analyzer.util.FormatUtils

/**
 * Orphan Asset Detector
 * Analyzes and detects orphaned (unused) assets in UE5 projects,
 * providing statistics and cleanup suggestions.
 */

/**
 * Statistics about orphaned assets
 */
data class OrphanStats(
    val totalOrphans: Int,
    val byType: Map<AssetType, Int>,
    val byRiskLevel: Map<OrphanRiskLevel, Int>,
    val totalSize: Long,
    val orphanedAssets: List<UEAsset>,
    val criticalAssets: List<UEAsset>  // High risk orphans
)

/**
 * Cleanup suggestion for orphan assets
 */
data class CleanupSuggestion(
    val asset: UEAsset,
    val reason: String,
    val safetyLevel: CleanupSafety,
    val alternativeAssets: List<String> = emptyList(),
    val estimatedSavings: Long
)

/**
 * Safety level for cleanup operations
 */
enum class CleanupSafety {
    SAFE,       // Can be deleted immediately
    REVIEW,     // Should be reviewed before deletion
    CAUTION     // May have hidden dependencies
}

/**
 * Orphan Detector
 */
class OrphanDetector {
    
    companion object {
        // Assets that are commonly orphans but may still be needed
        private val LOW_RISK_TYPES = setOf(
            AssetType.MATERIAL_FUNCTION,
            AssetType.CURVE,
            AssetType.DATA_TABLE
        )
        
        // Assets that should never be automatically deleted
        private val PROTECTED_TYPES = setOf(
            AssetType.LEVEL,
            AssetType.BLUEPRINT,
            AssetType.INTERFACE
        )
    }
    
    /**
     * Analyze orphan assets and generate statistics
     */
    fun analyzeOrphans(assets: List<UEAsset>): OrphanStats {
        val orphans = assets.filter { it.isOrphan }
        
        val byType = orphans.groupingBy { it.type }.eachCount()
        val byRiskLevel = orphans.groupingBy { it.orphanRiskLevel }.eachCount()
        val totalSize = orphans.sumOf { it.size }
        
        // Critical assets: HIGH risk orphans that are NOT protected types
        val criticalAssets = orphans.filter { 
            it.orphanRiskLevel == OrphanRiskLevel.HIGH && 
            it.type !in PROTECTED_TYPES 
        }
        
        return OrphanStats(
            totalOrphans = orphans.size,
            byType = byType,
            byRiskLevel = byRiskLevel,
            totalSize = totalSize,
            orphanedAssets = orphans.sortedByDescending { it.size },
            criticalAssets = criticalAssets.sortedByDescending { it.size }
        )
    }
    
    /**
     * Generate cleanup suggestions for orphan assets
     */
    fun generateCleanupSuggestions(assets: List<UEAsset>): List<CleanupSuggestion> {
        val orphans = assets.filter { it.isOrphan }
        val assetMap = assets.associateBy { it.id }
        
        return orphans.map { orphan ->
            val safety = determineCleanupSafety(orphan, assets)
            val reason = generateOrphanReason(orphan, assets)
            val alternatives = findAlternativeAssets(orphan, assets)
            
            CleanupSuggestion(
                asset = orphan,
                reason = reason,
                safetyLevel = safety,
                alternativeAssets = alternatives,
                estimatedSavings = orphan.size
            )
        }.sortedBy { it.safetyLevel.ordinal }
    }
    
    /**
     * Determine if an orphan asset can be safely deleted
     */
    private fun determineCleanupSafety(asset: UEAsset, allAssets: List<UEAsset>): CleanupSafety {
        return when {
            // Protected types should never be auto-deleted
            asset.type in PROTECTED_TYPES -> CleanupSafety.CAUTION
            
            // High risk with size > 5MB needs review
            asset.orphanRiskLevel == OrphanRiskLevel.HIGH && 
            asset.size > 5 * 1024 * 1024 -> CleanupSafety.REVIEW
            
            // Low risk types that are small can be safely deleted
            asset.orphanRiskLevel == OrphanRiskLevel.LOW && 
            asset.size < 1024 * 1024 -> CleanupSafety.SAFE
            
            // Large assets need review
            asset.size > 10 * 1024 * 1024 -> CleanupSafety.REVIEW
            
            // Default to safe for small, unused assets
            asset.orphanRiskLevel == OrphanRiskLevel.HIGH -> CleanupSafety.REVIEW
            
            else -> CleanupSafety.SAFE
        }
    }
    
    /**
     * Generate reason why an asset became orphan
     */
    private fun generateOrphanReason(asset: UEAsset, allAssets: List<UEAsset>): String {
        // Check if asset references others that are also orphans
        val orphanDeps = asset.dependencies.filter { depId ->
            allAssets.find { it.id == depId }?.isOrphan == true
        }
        
        return when {
            orphanDeps.isNotEmpty() -> 
                "References ${orphanDeps.size} other orphaned assets (may be part of unused system)"
            
            asset.dependencies.isEmpty() && asset.references.isEmpty() ->
                "No dependencies or references found - may be a template or test asset"
            
            asset.references.isEmpty() ->
                "No assets reference this asset - consider if it's still needed"
            
            else -> "Dependencies exist but no reverse references found"
        }
    }
    
    /**
     * Find alternative assets that might replace the orphan
     */
    private fun findAlternativeAssets(orphan: UEAsset, allAssets: List<UEAsset>): List<String> {
        return allAssets
            .filter { 
                it.id != orphan.id && 
                it.type == orphan.type && 
                !it.isOrphan &&
                it.references.isNotEmpty()
            }
            .take(3)
            .map { it.name }
    }
    
    /**
     * Generate batch cleanup plan
     */
    fun generateCleanupPlan(assets: List<UEAsset>): CleanupPlan {
        val stats = analyzeOrphans(assets)
        val suggestions = generateCleanupSuggestions(assets)
        
        val safeToDelete = suggestions.filter { it.safetyLevel == CleanupSafety.SAFE }
        val reviewNeeded = suggestions.filter { it.safetyLevel == CleanupSafety.REVIEW }
        val caution = suggestions.filter { it.safetyLevel == CleanupSafety.CAUTION }
        
        return CleanupPlan(
            totalOrphans = stats.totalOrphans,
            safeToDelete = safeToDelete,
            reviewNeeded = reviewNeeded,
            caution = caution,
            potentialSpaceSavings = stats.totalSize,
            safeSpaceSavings = safeToDelete.sumOf { it.estimatedSavings }
        )
    }
    
    /**
     * Check if removing an orphan would break any dependencies
     */
    fun validateOrphanRemoval(orphanId: String, allAssets: List<UEAsset>): RemovalValidation {
        val orphan = allAssets.find { it.id == orphanId }
        
        if (orphan == null) {
            return RemovalValidation(
                canRemove = false,
                blockedBy = emptyList(),
                warnings = listOf("Asset not found")
            )
        }
        
        // Check if any asset depends on this orphan
        val dependents = allAssets.filter { asset ->
            orphanId in asset.dependencies
        }
        
        return RemovalValidation(
            canRemove = dependents.isEmpty(),
            blockedBy = dependents.map { it.name },
            warnings = if (dependents.isNotEmpty()) {
                listOf("Asset is referenced by ${dependents.size} other assets")
            } else emptyList()
        )
    }
}

/**
 * Batch cleanup plan
 */
data class CleanupPlan(
    val totalOrphans: Int,
    val safeToDelete: List<CleanupSuggestion>,
    val reviewNeeded: List<CleanupSuggestion>,
    val caution: List<CleanupSuggestion>,
    val potentialSpaceSavings: Long,
    val safeSpaceSavings: Long
)

/**
 * Validation result for orphan removal
 */
data class RemovalValidation(
    val canRemove: Boolean,
    val blockedBy: List<String>,
    val warnings: List<String>
)
