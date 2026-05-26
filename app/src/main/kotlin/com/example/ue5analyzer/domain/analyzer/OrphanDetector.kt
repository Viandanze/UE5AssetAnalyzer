package com.example.ue5analyzer.domain.analyzer

import com.example.ue5analyzer.model.AssetType
import com.example.ue5analyzer.model.CleanupSuggestion
import com.example.ue5analyzer.model.OrphanRiskLevel
import com.example.ue5analyzer.model.OrphanStats
import com.example.ue5analyzer.model.UEAsset

/**
 * Orphan Asset Detector
 * Identifies assets that have no inbound references and are potentially unused.
 * Distinguishes between true orphans (safe to delete) and assets that may be
 * referenced through code or Blueprints (requires manual verification).
 */
class OrphanDetector {

    companion object {
        // Asset types that should never be considered orphans
        // even if they have zero references
        private val PROTECTED_TYPES = setOf(
            AssetType.LEVEL,
            AssetType.WORLD_PARTITION
        )

        // Asset types commonly referenced through code/Blueprints
        // rather than explicit asset references
        private val CODE_REFERENCED_TYPES = setOf(
            AssetType.BLUEPRINT,
            AssetType.WIDGET,
            AssetType.ENUM,
            AssetType.STRUCT,
            AssetType.DATA_TABLE,
            AssetType.FUNCTION
        )
    }

    /**
     * Detect orphan assets in the project
     * @param assets All assets in the project
     * @return List of UEAsset with updated orphan flags and risk levels
     */
    fun detectOrphans(assets: List<UEAsset>): List<UEAsset> {
        // Build a set of all asset paths that are referenced by other assets
        val allReferencedPaths = assets.flatMap { it.dependencies }.toSet()
        val assetPathMap = assets.associateBy { it.path }

        return assets.map { asset ->
            val isReferenced = asset.path in allReferencedPaths || asset.references.isNotEmpty()
            val isProtected = asset.type in PROTECTED_TYPES

            val (isOrphan, riskLevel) = when {
                isProtected -> false to OrphanRiskLevel.NONE
                isReferenced -> false to OrphanRiskLevel.NONE
                asset.type in CODE_REFERENCED_TYPES -> {
                    // Likely referenced in code, low risk but flag for review
                    true to OrphanRiskLevel.LOW
                }

                else -> {
                    // True orphan: no references, not code-referenced type
                    true to OrphanRiskLevel.HIGH
                }
            }

            asset.copy(
                isOrphan = isOrphan,
                orphanRiskLevel = riskLevel
            )
        }
    }

    /**
     * Generate orphan statistics
     */
    fun getOrphanStats(assets: List<UEAsset>): OrphanStats {
        if (assets.isEmpty()) {
            return OrphanStats(
                totalOrphans = 0,
                totalAssets = 0,
                orphanRatio = 0f,
                orphansByType = emptyMap(),
                orphansByRiskLevel = emptyMap(),
                estimatedWastedSpace = 0L
            )
        }

        val orphans = assets.filter { it.isOrphan }

        return OrphanStats(
            totalOrphans = orphans.size,
            totalAssets = assets.size,
            orphanRatio = orphans.size.toFloat() / assets.size,
            orphansByType = orphans.groupingBy { it.type }.eachCount(),
            orphansByRiskLevel = orphans.groupingBy { it.orphanRiskLevel }.eachCount(),
            estimatedWastedSpace = orphans.sumOf { it.size }
        )
    }

    /**
     * Generate cleanup suggestions for orphan assets
     */
    fun getCleanupSuggestions(assets: List<UEAsset>): List<CleanupSuggestion> {
        return assets
            .filter { it.isOrphan }
            .sortedByDescending { it.size }
            .map { asset ->
                CleanupSuggestion(
                    asset = asset,
                    reason = buildReason(asset),
                    riskLevel = asset.orphanRiskLevel,
                    estimatedSpaceSaving = asset.size,
                    safeToDelete = isSafeToDelete(asset)
                )
            }
    }

    /**
     * Find potentially redundant duplicate assets
     * Assets with same type and similar size could be duplicates
     */
    fun findPotentialDuplicates(assets: List<UEAsset>): List<Pair<UEAsset, UEAsset>> {
        val duplicates = mutableListOf<Pair<UEAsset, UEAsset>>()
        val byType = assets.groupBy { it.type }

        for ((_, typeAssets) in byType) {
            for (i in typeAssets.indices) {
                for (j in (i + 1) until typeAssets.size) {
                    val a = typeAssets[i]
                    val b = typeAssets[j]
                    // Same type and similar size (within 5% tolerance) suggests potential duplicate
                    val sizeDiff = kotlin.math.abs(a.size - b.size)
                    val avgSize = (a.size + b.size) / 2.0
                    if (avgSize > 0 && sizeDiff / avgSize < 0.05 && a.name != b.name) {
                        duplicates.add(a to b)
                    }
                }
            }
        }

        return duplicates
    }

    /**
     * Build a human-readable reason for why an asset is flagged as orphan
     */
    private fun buildReason(asset: UEAsset): String {
        return when {
            asset.type in PROTECTED_TYPES ->
                "Protected asset type (${asset.type.displayName}), never considered orphan"

            asset.type in CODE_REFERENCED_TYPES ->
                "May be referenced in code or Blueprints. Verify before deletion."

            asset.orphanRiskLevel == OrphanRiskLevel.HIGH ->
                "No references found. Likely safe to delete."

            else ->
                "Low reference count. Review before deletion."
        }
    }

    /**
     * Determine if an orphan asset is safe to delete
     */
    private fun isSafeToDelete(asset: UEAsset): Boolean {
        return asset.orphanRiskLevel == OrphanRiskLevel.HIGH &&
                asset.type !in PROTECTED_TYPES &&
                asset.type !in CODE_REFERENCED_TYPES
    }
}
