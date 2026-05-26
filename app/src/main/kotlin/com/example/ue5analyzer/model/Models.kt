package com.example.ue5analyzer.model

/**
 * UE5 Asset Type Enumeration
 */
enum class AssetType(val displayName: String, val prefix: String) {
    BLUEPRINT("Blueprint", "BP_"),
    STATIC_MESH("Static Mesh", "SM_"),
    SKELETAL_MESH("Skeletal Mesh", "SK_"),
    MATERIAL("Material", "M_"),
    MATERIAL_INSTANCE("Material Instance", "MI_"),
    MATERIAL_FUNCTION("Material Function", "MF_"),
    TEXTURE("Texture", "T_"),
    SOUND("Sound", "S_"),
    PARTICLE_SYSTEM("Particle System", "P_"),
    ANIMATION("Animation", "A_"),
    LEVEL("Level", "LVL_"),
    WIDGET("UMG Widget", "WBP_"),
    ENUM("Enumeration", "E_"),
    STRUCT("Struct", "F_"),
    INTERFACE("Interface", "I_"),
    DATA_TABLE("Data Table", "DT_"),
    CURVE("Curve", "CR_"),
    FUNCTION("Function", "FN_"),
    WORLD_PARTITION("World Partition", "WP_"),
    UNKNOWN("Unknown", "");

    companion object {
        fun fromName(name: String): AssetType {
            // First match with explicit prefix
            val matched =
                values().filter { it.prefix.isNotEmpty() }.find { name.startsWith(it.prefix) }
            return matched ?: UNKNOWN
        }
    }
}

/**
 * Orphan Asset Risk Level
 */
enum class OrphanRiskLevel {
    NONE,   // No risk (references >= 2)
    LOW,    // Low risk (references = 1)
    HIGH    // High risk (references = 0, non-level)
}

/**
 * Issue Category for categorized analysis
 */
enum class IssueCategory {
    ORPHAN_ASSET,        // Unused assets with no references
    CIRCULAR_DEPENDENCY, // Circular dependency chains
    LARGE_ASSET,         // Assets exceeding size threshold
    DEEP_DEPENDENCY,     // Assets with excessively deep dependency chains
    LOW_REFERENCE,       // Assets with very few references (potential cleanup candidates)
    REDUNDANT_DUPLICATE  // Potentially duplicated assets
}

/**
 * UE5 Asset Data Model
 */
data class UEAsset(
    val id: String,
    val name: String,
    val path: String,
    val type: AssetType,
    val size: Long,
    val dependencies: List<String> = emptyList(),
    val references: List<String> = emptyList(),
    val isOrphan: Boolean = false,
    val orphanRiskLevel: OrphanRiskLevel = OrphanRiskLevel.NONE,
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Format file size as human-readable string
 */
fun UEAsset.formatFileSize(): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024))
        else -> String.format("%.1f GB", size / (1024.0 * 1024 * 1024))
    }
}

/**
 * Circular Dependency representation
 */
data class CircularDependency(
    val assetIds: List<String>,
    val assetNames: List<String>,
    val severity: DependencySeverity = DependencySeverity.MEDIUM
)

/**
 * Dependency severity level
 */
enum class DependencySeverity {
    LOW,      // Indirect cycle, minor impact
    MEDIUM,   // Direct cycle, moderate impact
    HIGH      // Critical cycle, major impact on build/load
}

/**
 * Health Score Detail breakdown
 */
data class HealthScoreDetail(
    val category: String,
    val score: Int,
    val maxScore: Int,
    val description: String
)

/**
 * Health Score with detailed breakdown
 */
data class HealthScore(
    val totalScore: Int,
    val maxScore: Int = 100,
    val grade: HealthGrade,
    val details: List<HealthScoreDetail>
)

/**
 * Health grade based on score ranges
 */
enum class HealthGrade {
    EXCELLENT,  // 90-100
    GOOD,       // 75-89
    FAIR,       // 60-74
    POOR,       // 40-59
    CRITICAL;   // 0-39

    companion object {
        fun fromScore(score: Int): HealthGrade = when {
            score >= 90 -> EXCELLENT
            score >= 75 -> GOOD
            score >= 60 -> FAIR
            score >= 40 -> POOR
            else -> CRITICAL
        }
    }
}

/**
 * Optimization Suggestion
 */
data class OptimizationSuggestion(
    val title: String,
    val description: String,
    val category: IssueCategory,
    val impact: OptimizationImpact,
    val affectedAssets: List<String> = emptyList(),
    val estimatedSavings: String = ""
)

/**
 * Optimization impact level
 */
enum class OptimizationImpact {
    LOW,       // Minor improvement
    MEDIUM,    // Noticeable improvement
    HIGH,      // Significant improvement
    CRITICAL   // Must-fix, blocking issue
}

/**
 * Categorized Issues container
 */
data class CategorizedIssues(
    val category: IssueCategory,
    val issues: List<UEAsset>,
    val description: String,
    val severity: OptimizationImpact = OptimizationImpact.MEDIUM
)

/**
 * Dependency Statistics
 */
data class DependencyStats(
    val totalDependencies: Int,
    val avgDependenciesPerAsset: Double,
    val maxDependencyDepth: Int,
    val circularDependencyCount: Int,
    val mostDependedOnAsset: String?,
    val mostDependentAsset: String?
)

/**
 * Orphan Asset Statistics
 */
data class OrphanStats(
    val totalOrphans: Int,
    val totalAssets: Int,
    val orphanRatio: Float,
    val orphansByType: Map<AssetType, Int>,
    val orphansByRiskLevel: Map<OrphanRiskLevel, Int>,
    val estimatedWastedSpace: Long
)

/**
 * Cleanup Suggestion
 */
data class CleanupSuggestion(
    val asset: UEAsset,
    val reason: String,
    val riskLevel: OrphanRiskLevel,
    val estimatedSpaceSaving: Long,
    val safeToDelete: Boolean
)

/**
 * Project Scan Result
 */
data class ScanResult(
    val projectPath: String,
    val projectName: String,
    val totalAssets: Int,
    val totalSize: Long,
    val assetsByType: Map<AssetType, Int>,
    val allAssets: List<UEAsset>,    // All scanned assets
    val orphanAssets: List<UEAsset>, // Orphan assets (subset of allAssets)
    val scanTime: Long = System.currentTimeMillis()
)

/**
 * Dependency Graph Node
 */
data class DependencyNode(
    val assetId: String,
    val assetName: String,
    val assetType: AssetType,
    val dependencies: List<String>,
    val depth: Int = 0
)

/**
 * Analysis Report
 */
data class AnalysisReport(
    val projectPath: String,
    val projectName: String,
    val totalAssets: Int,
    val totalSize: Long,
    val orphanCount: Int,
    val assetsByType: Map<AssetType, Int>,
    val largestAssets: List<UEAsset>,
    val mostReferenced: List<UEAsset>,
    val orphanAssets: List<UEAsset>,
    val generatedAt: Long = System.currentTimeMillis(),
    val healthScore: Int = 0,
    val circularDependencies: List<List<String>> = emptyList()
)
