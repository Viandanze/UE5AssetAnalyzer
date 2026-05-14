package com.example.ue5analyzer.domain.analyzer

import com.example.ue5analyzer.model.*
import com.example.ue5analyzer.util.FormatUtils

/**
 * Advanced Asset Analyzer
 * Provides deep asset analysis including circular dependency detection,
 * health scoring with detailed breakdown, and optimization suggestions.
 * 
 * This class complements the basic AssetAnalyzer in Analyzer.kt by
 * providing more detailed analysis results.
 */

// ========== Circular Dependency Models ==========

/**
 * Represents a circular dependency chain detected in assets
 */
data class CircularDependency(
    val id: String,
    val assets: List<String>,
    val severity: CircularSeverity = CircularSeverity.MEDIUM
)

/**
 * Severity level for circular dependencies
 */
enum class CircularSeverity {
    LOW,      // Indirect cycle, affects build time only
    MEDIUM,   // Direct parent-child cycle
    HIGH      // Deep nested cycle affecting runtime
}

// ========== Health Score Models ==========

/**
 * Detailed health score breakdown
 */
data class HealthScore(
    val totalScore: Int,
    val orphanScore: Int,
    val dependencyScore: Int,
    val sizeScore: Int,
    val details: HealthScoreDetail
) {
    companion object {
        fun fromScore(total: Int, orphan: Int, dep: Int, size: Int, assets: List<UEAsset>): HealthScore {
            return HealthScore(
                totalScore = total,
                orphanScore = orphan,
                dependencyScore = dep,
                sizeScore = size,
                details = HealthScoreDetail(
                    orphanCount = assets.count { it.isOrphan },
                    totalAssets = assets.size,
                    orphanPercentage = if (assets.isNotEmpty()) 
                        assets.count { it.isOrphan } * 100f / assets.size else 0f,
                    avgReferences = if (assets.isNotEmpty()) 
                        assets.sumOf { it.references.size }.toFloat() / assets.size else 0f,
                    largeAssetCount = assets.count { it.size > 10 * 1024 * 1024 },
                    circularDepCount = 0  // Will be set by caller
                )
            )
        }
    }
}

/**
 * Detailed breakdown of health metrics
 */
data class HealthScoreDetail(
    val orphanCount: Int,
    val totalAssets: Int,
    val orphanPercentage: Float,
    val avgReferences: Float,
    val largeAssetCount: Int,
    val circularDepCount: Int
)

// ========== Optimization Suggestion Models ==========

/**
 * Optimization suggestion with priority and category
 */
data class OptimizationSuggestion(
    val category: IssueCategory,
    val title: String,
    val description: String,
    val affectedAssets: List<String>,
    val potentialSavings: Long,  // Bytes that could be saved
    val priority: SuggestionPriority,
    val action: SuggestionAction
)

/**
 * Issue category for classification
 */
enum class IssueCategory {
    ORPHAN,           // Orphaned assets
    CIRCULAR_DEP,     // Circular dependencies
    LARGE_ASSET,      // Oversized assets
    DUPLICATE,        // Potential duplicates
    MISSING_DEP,      // Missing dependencies
    UNUSED_REF,       // Unused references
    PERFORMANCE,      // Performance concerns
    ORGANIZATION      // Organization issues
}

/**
 * Categorized issues grouped by category
 */
data class CategorizedIssues(
    val category: IssueCategory,
    val count: Int,
    val issues: List<OptimizationSuggestion>,
    val totalImpact: Long  // Total bytes impacted
)

// ========== Dependency Statistics Models ==========

/**
 * Statistics about dependencies in the project
 */
data class DependencyStats(
    val totalDependencies: Int,
    val uniqueDependencies: Int,
    val avgDependenciesPerAsset: Float,
    val mostDependentAssets: List<UEAsset>,
    val mostReferencedAssets: List<UEAsset>,
    val dependencyChains: Map<String, List<String>>,  // Asset -> its dependencies
    val circularDependencies: List<CircularDependency>
)

// ========== Advanced Asset Analyzer ==========

/**
 * Advanced asset analyzer providing detailed analysis results
 */
class AdvancedAssetAnalyzer {
    
    /**
     * Analyze assets and generate comprehensive health report
     */
    fun analyzeHealth(assets: List<UEAsset>): HealthScore {
        if (assets.isEmpty()) {
            return HealthScore(
                totalScore = 100,
                orphanScore = 50,
                dependencyScore = 50,
                sizeScore = 0,
                details = HealthScoreDetail(
                    orphanCount = 0,
                    totalAssets = 0,
                    orphanPercentage = 0f,
                    avgReferences = 0f,
                    largeAssetCount = 0,
                    circularDepCount = 0
                )
            )
        }
        
        val orphanRatio = assets.count { it.isOrphan }.toFloat() / assets.size
        val avgRefs = assets.sumOf { it.references.size }.toFloat() / assets.size
        val largeCount = assets.count { it.size > 10 * 1024 * 1024 }
        
        // Orphan score: 0-50 points
        val orphanScore = ((1 - orphanRatio) * 50).toInt()
        
        // Dependency score: 0-50 points based on average references
        val depScore = (minOf(avgRefs / 5f, 1f) * 50).toInt()
        
        // Size penalty: -10 points if too many large assets
        val sizeScore = if (largeCount > assets.size * 0.1f) -10 else 0
        
        val total = orphanScore + depScore + sizeScore
        
        return HealthScore(
            totalScore = total.coerceIn(0, 100),
            orphanScore = orphanScore,
            dependencyScore = depScore,
            sizeScore = sizeScore,
            details = HealthScoreDetail(
                orphanCount = assets.count { it.isOrphan },
                totalAssets = assets.size,
                orphanPercentage = orphanRatio * 100,
                avgReferences = avgRefs,
                largeAssetCount = largeCount,
                circularDepCount = 0
            )
        )
    }
    
    /**
     * Detect all circular dependencies in the asset graph
     */
    fun detectCircularDependencies(assets: List<UEAsset>): List<CircularDependency> {
        val cycles = mutableListOf<CircularDependency>()
        val assetMap = assets.associateBy { it.id }
        val visited = mutableSetOf<String>()
        
        assets.forEach { startAsset ->
            if (startAsset.id in visited) return@forEach
            
            val stack = ArrayDeque<Pair<String, List<String>>>()
            stack.push(startAsset.id to emptyList())
            
            while (stack.isNotEmpty()) {
                val (currentId, path) = stack.pop()
                
                if (currentId in path) {
                    val cycleStart = path.indexOf(currentId)
                    if (cycleStart >= 0) {
                        val cycleIds = path.subList(cycleStart, path.size) + currentId
                        val cycleNames = cycleIds.mapNotNull { assetMap[it]?.name }
                        if (cycleNames.isNotEmpty() && cycleNames.size > 2) {
                            val severity = when {
                                cycleNames.size > 5 -> CircularSeverity.HIGH
                                cycleNames.size > 3 -> CircularSeverity.MEDIUM
                                else -> CircularSeverity.LOW
                            }
                            cycles.add(CircularDependency(
                                id = cycleNames.joinToString("->"),
                                assets = cycleNames,
                                severity = severity
                            ))
                        }
                    }
                    continue
                }
                
                if (currentId in visited) continue
                visited.add(currentId)
                
                val asset = assetMap[currentId] ?: continue
                val newPath = path + currentId
                
                for (i in asset.dependencies.indices.reversed()) {
                    stack.push(asset.dependencies[i] to newPath)
                }
            }
        }
        
        // Deduplicate cycles
        val seen = mutableSetOf<String>()
        return cycles.filter { cycle ->
            val key = cycle.assets.sorted().joinToString(",")
            if (key !in seen) {
                seen.add(key)
                true
            } else false
        }
    }
    
    /**
     * Generate dependency statistics
     */
    fun calculateDependencyStats(assets: List<UEAsset>): DependencyStats {
        val circularDeps = detectCircularDependencies(assets)
        val totalDeps = assets.sumOf { it.dependencies.size }
        val avgDeps = if (assets.isNotEmpty()) totalDeps.toFloat() / assets.size else 0f
        
        val dependencyChains = assets.associate { it.id to it.dependencies }
        
        return DependencyStats(
            totalDependencies = totalDeps,
            uniqueDependencies = assets.flatMap { it.dependencies }.distinct().size,
            avgDependenciesPerAsset = avgDeps,
            mostDependentAssets = assets.sortedByDescending { it.dependencies.size }.take(10),
            mostReferencedAssets = assets.sortedByDescending { it.references.size }.take(10),
            dependencyChains = dependencyChains,
            circularDependencies = circularDeps
        )
    }
    
    /**
     * Generate optimization suggestions based on analysis
     */
    fun generateSuggestions(assets: List<UEAsset>): List<OptimizationSuggestion> {
        val suggestions = mutableListOf<OptimizationSuggestion>()
        
        // Orphan asset suggestions
        val orphans = assets.filter { it.isOrphan }
        if (orphans.isNotEmpty()) {
            suggestions.add(OptimizationSuggestion(
                category = IssueCategory.ORPHAN,
                title = "Orphaned Assets Detected",
                description = "${orphans.size} assets are not referenced by any other asset and may be unused",
                affectedAssets = orphans.map { it.name },
                potentialSavings = orphans.sumOf { it.size },
                priority = SuggestionPriority.HIGH,
                action = SuggestionAction.REVIEW_OR_DELETE
            ))
        }
        
        // Circular dependency suggestions
        val circularDeps = detectCircularDependencies(assets)
        if (circularDeps.isNotEmpty()) {
            val affectedAssets = circularDeps.flatMap { it.assets }.distinct()
            suggestions.add(OptimizationSuggestion(
                category = IssueCategory.CIRCULAR_DEP,
                title = "Circular Dependencies Found",
                description = "${circularDeps.size} circular dependency chains detected",
                affectedAssets = affectedAssets,
                potentialSavings = 0,
                priority = SuggestionPriority.MEDIUM,
                action = SuggestionAction.BREAK_CYCLE
            ))
        }
        
        // Large asset suggestions
        val largeAssets = assets.filter { it.size > 10 * 1024 * 1024 }
        if (largeAssets.isNotEmpty()) {
            suggestions.add(OptimizationSuggestion(
                category = IssueCategory.LARGE_ASSET,
                title = "Large Assets May Impact Performance",
                description = "${largeAssets.size} assets exceed 10MB",
                affectedAssets = largeAssets.map { it.name },
                potentialSavings = largeAssets.sumOf { it.size } / 2,  // Assume 50% compression possible
                priority = SuggestionPriority.MEDIUM,
                action = SuggestionAction.OPTIMIZE_TEXTURE
            ))
        }
        
        // Low reference suggestions (potentially unused)
        val lowRefAssets = assets.filter { it.references.size <= 1 && !it.isOrphan && it.type != AssetType.LEVEL }
        if (lowRefAssets.size > assets.size * 0.2f) {
            suggestions.add(OptimizationSuggestion(
                category = IssueCategory.UNUSED_REF,
                title = "Potentially Unused Assets",
                description = "${lowRefAssets.size} assets have minimal references",
                affectedAssets = lowRefAssets.take(20).map { it.name },
                potentialSavings = lowRefAssets.sumOf { it.size },
                priority = SuggestionPriority.LOW,
                action = SuggestionAction.REVIEW_USAGE
            ))
        }
        
        return suggestions.sortedBy { it.priority.ordinal }
    }
    
    /**
     * Group issues by category
     */
    fun categorizeIssues(assets: List<UEAsset>): List<CategorizedIssues> {
        val suggestions = generateSuggestions(assets)
        val grouped = suggestions.groupBy { it.category }
        
        return grouped.map { (category, issues) ->
            CategorizedIssues(
                category = category,
                count = issues.sumOf { it.affectedAssets.size },
                issues = issues,
                totalImpact = issues.sumOf { it.potentialSavings }
            )
        }.sortedByDescending { it.count }
    }
}

/**
 * Suggestion priority levels
 */
enum class SuggestionPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Suggested actions for optimization
 */
enum class SuggestionAction {
    REVIEW_OR_DELETE,
    BREAK_CYCLE,
    OPTIMIZE_TEXTURE,
    COMPRESS_ASSET,
    CONSOLIDATE_DUPLICATES,
    REBUILD_REFERENCE,
    REVIEW_USAGE
}
