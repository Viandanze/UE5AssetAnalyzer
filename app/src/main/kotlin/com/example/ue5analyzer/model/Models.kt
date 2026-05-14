package com.example.ue5analyzer.model

import com.example.ue5analyzer.util.FormatUtils

/**
 * UE5 Asset Type Enum
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
    ENUM("Enum", "E_"),
    STRUCT("Struct", "F_"),
    INTERFACE("Interface", "I_"),
    DATA_TABLE("Data Table", "DT_"),
    CURVE("Curve", "CR_"),
    FUNCTION("Function", "Func_"),
    WORLD_PARTITION("World Partition", "WP_"),
    UNKNOWN("Unknown", "");

    companion object {
        fun fromName(name: String): AssetType {
            // Match by explicit prefix first
            val matched = values().filter { it.prefix.isNotEmpty() }
                .find { name.startsWith(it.prefix) }
            return matched ?: UNKNOWN
        }
        
        fun fromClassName(className: String): AssetType {
            return when (className) {
                "Blueprint", "BlueprintCore" -> BLUEPRINT
                "StaticMesh", "StaticMeshActor" -> STATIC_MESH
                "SkeletalMesh", "SkeletalMeshActor" -> SKELETAL_MESH
                "Material", "MaterialInstance" -> MATERIAL
                "MaterialInstanceConstant" -> MATERIAL_INSTANCE
                "MaterialFunction" -> MATERIAL_FUNCTION
                "Texture2D", "Texture", "MediaTexture" -> TEXTURE
                "SoundCue", "SoundWave", "AudioComponent" -> SOUND
                "ParticleSystem", "NiagaraSystem" -> PARTICLE_SYSTEM
                "AnimSequence", "AnimMontage", "AnimBlueprint" -> ANIMATION
                "Level", "World", "PersistentLevel" -> LEVEL
                "WidgetBlueprint", "UserWidget", "Widget" -> WIDGET
                "UserDefinedEnum" -> ENUM
                "UserDefinedStruct" -> STRUCT
                "BlueprintInterface" -> INTERFACE
                "DataTable" -> DATA_TABLE
                "CurveFloat", "CurveVector", "CurveLinearColor" -> CURVE
                "Function", "K2Node" -> FUNCTION
                "WorldPartition" -> WORLD_PARTITION
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Orphan risk level
 */
enum class OrphanRiskLevel {
    NONE,   // No risk (reference count >= 2)
    LOW,    // Low risk (reference count = 1)
    HIGH    // High risk (reference count = 0, non-level asset)
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
) {
    /**
     * Extension function to format file size
     */
    fun formatFileSize(): String = FormatUtils.formatFileSize(size)
}

/**
 * Project scan result
 */
data class ScanResult(
    val projectPath: String,
    val projectName: String,
    val totalAssets: Int,
    val totalSize: Long,
    val assetsByType: Map<AssetType, Int>,
    val allAssets: List<UEAsset>,
    val orphanAssets: List<UEAsset>,
    val scanTime: Long = System.currentTimeMillis()
)

/**
 * Dependency graph node
 */
data class DependencyNode(
    val assetId: String,
    val assetName: String,
    val assetType: AssetType,
    val dependencies: List<String>,
    val depth: Int = 0
)

/**
 * Analysis report
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
