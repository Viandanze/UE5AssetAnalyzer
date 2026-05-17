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
    UNKNOWN("Unknown", "");

    companion object {
        fun fromName(name: String): AssetType {
            // First match with explicit prefix
            val matched = values().filter { it.prefix.isNotEmpty() }.find { name.startsWith(it.prefix) }
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
