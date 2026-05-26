package com.example.ue5analyzer.domain.analyzer

import com.example.ue5analyzer.model.AssetType
import com.example.ue5analyzer.model.CategorizedIssues
import com.example.ue5analyzer.model.CircularDependency
import com.example.ue5analyzer.model.CleanupSuggestion
import com.example.ue5analyzer.model.DependencySeverity
import com.example.ue5analyzer.model.DependencyStats
import com.example.ue5analyzer.model.HealthGrade
import com.example.ue5analyzer.model.HealthScore
import com.example.ue5analyzer.model.HealthScoreDetail
import com.example.ue5analyzer.model.IssueCategory
import com.example.ue5analyzer.model.OptimizationImpact
import com.example.ue5analyzer.model.OptimizationSuggestion
import com.example.ue5analyzer.model.OrphanRiskLevel
import com.example.ue5analyzer.model.OrphanStats
import com.example.ue5analyzer.model.UEAsset
import com.example.ue5analyzer.model.formatFileSize

/**
 * Advanced Asset Analyzer
 * Provides in-depth analysis: health scoring, circular dependency detection,
 * categorized issues, optimization suggestions, and statistics.
 */
class AdvancedAssetAnalyzer {

    companion object {
        private const val LARGE_ASSET_THRESHOLD = 5 * 1024 * 1024L  // 5 MB
        private const val DEEP_DEPENDENCY_THRESHOLD = 10
        private const val LOW_REFERENCE_THRESHOLD = 1
    }

    private val dependencyAnalyzer = DependencyAnalyzer()

    // ============================================================
    // Health Score
    // ============================================================

    /**
     * Calculate comprehensive health score with detailed breakdown
     */
    fun calculateDetailedHealthScore(assets: List<UEAsset>): HealthScore {
        if (assets.isEmpty()) {
            return HealthScore(
                totalScore = 100,
                grade = HealthGrade.EXCELLENT,
                details = emptyList()
            )
        }

        val details = mutableListOf<HealthScoreDetail>()

        // 1. Orphan ratio (0-30 points)
        val orphanCount = assets.count { it.isOrphan }
        val orphanRatio = orphanCount.toFloat() / assets.size
        val orphanScore = ((1f - orphanRatio) * 30).toInt()
        details.add(
            HealthScoreDetail(
                category = "Orphan Assets",
                score = orphanScore,
                maxScore = 30,
                description = if (orphanCount == 0) "No orphan assets detected"
                else "$orphanCount orphan assets (${
                    String.format(
                        "%.1f%%",
                        orphanRatio * 100
                    )
                } of total)"
            )
        )

        // 2. Reference connectivity (0-25 points)
        val avgReferences = assets.sumOf { it.references.size }.toFloat() / assets.size
        val refScore = minOf(avgReferences / 5f, 1f).times(25).toInt()
        details.add(
            HealthScoreDetail(
                category = "Reference Connectivity",
                score = refScore,
                maxScore = 25,
                description = "Average ${String.format("%.1f", avgReferences)} references per asset"
            )
        )

        // 3. Circular dependencies (0-20 points)
        val cycles = findCircularDependencies(assets)
        val cyclePenalty = minOf(cycles.size * 5, 20)
        val cycleScore = 20 - cyclePenalty
        details.add(
            HealthScoreDetail(
                category = "Circular Dependencies",
                score = cycleScore,
                maxScore = 20,
                description = if (cycles.isEmpty()) "No circular dependencies found"
                else "${cycles.size} circular dependency chain(s) detected"
            )
        )

        // 4. Large asset ratio (0-15 points)
        val largeCount = assets.count { it.size > LARGE_ASSET_THRESHOLD }
        val largeRatio = largeCount.toFloat() / assets.size
        val largeScore = ((1f - largeRatio) * 15).toInt()
        details.add(
            HealthScoreDetail(
                category = "Asset Size Distribution",
                score = largeScore,
                maxScore = 15,
                description = if (largeCount == 0) "All assets within size threshold"
                else "$largeCount assets exceed ${LARGE_ASSET_THRESHOLD / (1024 * 1024)}MB"
            )
        )

        // 5. Dependency depth (0-10 points)
        val graph = dependencyAnalyzer.buildDependencyGraph(assets)
        val maxDepth = if (graph.isEmpty()) 0 else graph.values.maxOf { it.depth }
        val depthScore = if (maxDepth <= DEEP_DEPENDENCY_THRESHOLD) 10
        else maxOf(10 - (maxDepth - DEEP_DEPENDENCY_THRESHOLD), 0)
        details.add(
            HealthScoreDetail(
                category = "Dependency Depth",
                score = depthScore,
                maxScore = 10,
                description = "Maximum dependency depth: $maxDepth"
            )
        )

        val totalScore = details.sumOf { it.score }
        return HealthScore(
            totalScore = totalScore,
            grade = HealthGrade.fromScore(totalScore),
            details = details
        )
    }

    // ============================================================
    // Circular Dependencies
    // ============================================================

    /**
     * Find circular dependencies and return structured results
     */
    fun findCircularDependencies(assets: List<UEAsset>): List<CircularDependency> {
        val assetMap = assets.associateBy { it.id }
        val rawCycles = dependencyAnalyzer.findCircularDependencies(assets)

        return rawCycles.map { cycleIds ->
            val names = cycleIds.mapNotNull { assetMap[it]?.name }
            val severity = when {
                cycleIds.size <= 2 -> DependencySeverity.LOW
                cycleIds.size <= 4 -> DependencySeverity.MEDIUM
                else -> DependencySeverity.HIGH
            }
            CircularDependency(
                assetIds = cycleIds,
                assetNames = names,
                severity = severity
            )
        }.sortedByDescending { it.severity.ordinal }
    }

    // ============================================================
    // Categorized Issues
    // ============================================================

    /**
     * Categorize all issues found in the project
     */
    fun categorizeIssues(assets: List<UEAsset>): List<CategorizedIssues> {
        val issues = mutableListOf<CategorizedIssues>()

        // Orphan assets
        val orphans = assets.filter { it.isOrphan }
        if (orphans.isNotEmpty()) {
            issues.add(
                CategorizedIssues(
                    category = IssueCategory.ORPHAN_ASSET,
                    issues = orphans,
                    description = "${orphans.size} assets have no references and may be unused",
                    severity = if (orphans.size > assets.size * 0.3) OptimizationImpact.HIGH
                    else OptimizationImpact.MEDIUM
                )
            )
        }

        // Circular dependencies
        val cycles = findCircularDependencies(assets)
        if (cycles.isNotEmpty()) {
            val cycleAssetIds = cycles.flatMap { it.assetIds }.distinct()
            val cycleAssets = cycleAssetIds.mapNotNull { assets.find { a -> a.id == it } }
            issues.add(
                CategorizedIssues(
                    category = IssueCategory.CIRCULAR_DEPENDENCY,
                    issues = cycleAssets,
                    description = "${cycles.size} circular dependency chain(s) detected",
                    severity = if (cycles.any { it.severity == DependencySeverity.HIGH }) OptimizationImpact.HIGH
                    else OptimizationImpact.MEDIUM
                )
            )
        }

        // Large assets
        val largeAssets = assets.filter { it.size > LARGE_ASSET_THRESHOLD }
        if (largeAssets.isNotEmpty()) {
            issues.add(
                CategorizedIssues(
                    category = IssueCategory.LARGE_ASSET,
                    issues = largeAssets,
                    description = "${largeAssets.size} assets exceed ${LARGE_ASSET_THRESHOLD / (1024 * 1024)}MB",
                    severity = OptimizationImpact.LOW
                )
            )
        }

        // Deep dependency chains
        val graph = dependencyAnalyzer.buildDependencyGraph(assets)
        val deepAssets = graph.values
            .filter { it.depth > DEEP_DEPENDENCY_THRESHOLD }
            .map { node -> assets.find { a -> a.id == node.assetId } }
            .filterNotNull()
        if (deepAssets.isNotEmpty()) {
            issues.add(
                CategorizedIssues(
                    category = IssueCategory.DEEP_DEPENDENCY,
                    issues = deepAssets,
                    description = "${deepAssets.size} assets have dependency depth > $DEEP_DEPENDENCY_THRESHOLD",
                    severity = OptimizationImpact.MEDIUM
                )
            )
        }

        // Low reference assets (referenced by only 0 or 1 asset, excluding orphans)
        val lowRefAssets = assets.filter {
            it.references.size <= LOW_REFERENCE_THRESHOLD && !it.isOrphan && it.type != AssetType.LEVEL
        }
        if (lowRefAssets.isNotEmpty()) {
            issues.add(
                CategorizedIssues(
                    category = IssueCategory.LOW_REFERENCE,
                    issues = lowRefAssets,
                    description = "${lowRefAssets.size} assets have very few references",
                    severity = OptimizationImpact.LOW
                )
            )
        }

        return issues.sortedByDescending { it.severity.ordinal }
    }

    // ============================================================
    // Optimization Suggestions
    // ============================================================

    /**
     * Generate optimization suggestions based on analysis
     */
    fun generateOptimizationSuggestions(assets: List<UEAsset>): List<OptimizationSuggestion> {
        val suggestions = mutableListOf<OptimizationSuggestion>()

        // Suggestion: Remove orphan assets
        val orphans = assets.filter { it.isOrphan }
        if (orphans.isNotEmpty()) {
            val wastedSpace = orphans.sumOf { it.size }
            suggestions.add(
                OptimizationSuggestion(
                    title = "Remove Orphan Assets",
                    description = "Found ${orphans.size} assets with no references. Consider removing them to reduce project size.",
                    category = IssueCategory.ORPHAN_ASSET,
                    impact = if (orphans.size > 10) OptimizationImpact.HIGH else OptimizationImpact.MEDIUM,
                    affectedAssets = orphans.map { it.name },
                    estimatedSavings = formatSize(wastedSpace)
                )
            )
        }

        // Suggestion: Break circular dependencies
        val cycles = findCircularDependencies(assets)
        if (cycles.isNotEmpty()) {
            val highSeverityCycles = cycles.filter { it.severity == DependencySeverity.HIGH }
            suggestions.add(
                OptimizationSuggestion(
                    title = "Break Circular Dependencies",
                    description = "Found ${cycles.size} circular dependency chain(s). " +
                            "${highSeverityCycles.size} of them are high severity. " +
                            "Circular dependencies can cause loading issues and increase build time.",
                    category = IssueCategory.CIRCULAR_DEPENDENCY,
                    impact = if (highSeverityCycles.isNotEmpty()) OptimizationImpact.HIGH else OptimizationImpact.MEDIUM,
                    affectedAssets = cycles.flatMap { it.assetNames }.distinct()
                )
            )
        }

        // Suggestion: Optimize large assets
        val largeAssets =
            assets.filter { it.size > LARGE_ASSET_THRESHOLD }.sortedByDescending { it.size }
        if (largeAssets.isNotEmpty()) {
            suggestions.add(
                OptimizationSuggestion(
                    title = "Optimize Large Assets",
                    description = "${largeAssets.size} assets exceed ${LARGE_ASSET_THRESHOLD / (1024 * 1024)}MB. " +
                            "Consider using LODs, texture compression, or reducing resolution.",
                    category = IssueCategory.LARGE_ASSET,
                    impact = OptimizationImpact.MEDIUM,
                    affectedAssets = largeAssets.take(10)
                        .map { "${it.name} (${it.formatFileSize()})" },
                    estimatedSavings = "Varies by optimization method"
                )
            )
        }

        // Suggestion: Consolidate low-reference assets
        val lowRefAssets =
            assets.filter { it.references.size <= 1 && !it.isOrphan && it.type != AssetType.LEVEL }
        if (lowRefAssets.size > 5) {
            suggestions.add(
                OptimizationSuggestion(
                title = "Review Low-Reference Assets",
                description = "${lowRefAssets.size} assets are referenced by at most 1 other asset. " +
                        "Review if they can be consolidated or merged.",
                category = IssueCategory.LOW_REFERENCE,
                impact = OptimizationImpact.LOW,
                affectedAssets = lowRefAssets.take(10).map { it.name }
            ))
        }

        return suggestions.sortedByDescending { it.impact.ordinal }
    }

    // ============================================================
    // Statistics
    // ============================================================

    /**
     * Calculate dependency statistics
     */
    fun calculateDependencyStats(assets: List<UEAsset>): DependencyStats {
        if (assets.isEmpty()) {
            return DependencyStats(
                totalDependencies = 0,
                avgDependenciesPerAsset = 0.0,
                maxDependencyDepth = 0,
                circularDependencyCount = 0,
                mostDependedOnAsset = null,
                mostDependentAsset = null
            )
        }

        val totalDeps = assets.sumOf { it.dependencies.size }
        val avgDeps = totalDeps.toDouble() / assets.size

        val graph = dependencyAnalyzer.buildDependencyGraph(assets)
        val maxDepth = if (graph.isEmpty()) 0 else graph.values.maxOf { it.depth }

        val cycles = findCircularDependencies(assets)

        // Most depended-on asset (most references pointing to it)
        val mostDependedOn = assets.maxByOrNull { it.references.size }?.name

        // Most dependent asset (most dependencies going out)
        val mostDependent = assets.maxByOrNull { it.dependencies.size }?.name

        return DependencyStats(
            totalDependencies = totalDeps,
            avgDependenciesPerAsset = String.format("%.2f", avgDeps).toDouble(),
            maxDependencyDepth = maxDepth,
            circularDependencyCount = cycles.size,
            mostDependedOnAsset = mostDependedOn,
            mostDependentAsset = mostDependent
        )
    }

    /**
     * Calculate orphan asset statistics
     */
    fun calculateOrphanStats(assets: List<UEAsset>): OrphanStats {
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

    // ============================================================
    // Cleanup Suggestions
    // ============================================================

    /**
     * Generate cleanup suggestions for each orphan asset
     */
    fun generateCleanupSuggestions(assets: List<UEAsset>): List<CleanupSuggestion> {
        return assets
            .filter { it.isOrphan }
            .sortedByDescending { it.size }
            .map { asset ->
                CleanupSuggestion(
                    asset = asset,
                    reason = when (asset.orphanRiskLevel) {
                        OrphanRiskLevel.HIGH -> "No references found. Safe to delete if not used in code or Blueprints."
                        OrphanRiskLevel.LOW -> "Only 1 reference found. Verify before deletion."
                        OrphanRiskLevel.NONE -> "Has multiple references but flagged as orphan."
                    },
                    riskLevel = asset.orphanRiskLevel,
                    estimatedSpaceSaving = asset.size,
                    safeToDelete = asset.orphanRiskLevel == OrphanRiskLevel.HIGH &&
                            asset.type != AssetType.LEVEL
                )
            }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}
