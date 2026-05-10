package com.example.ue5analyzer.model

/**
 * UE5 资源类型枚举
 */
enum class AssetType(val displayName: String, val prefix: String) {
    BLUEPRINT("蓝图", "BP_"),
    STATIC_MESH("静态网格", "SM_"),
    SKELETAL_MESH("骨骼网格", "SK_"),
    MATERIAL("材质", "M_"),
    MATERIAL_INSTANCE("材质实例", "MI_"),
    TEXTURE("贴图", "T_"),
    SOUND("音效", "S_"),
    PARTICLE_SYSTEM("粒子系统", "P_"),
    ANIMATION("动画", "A_"),
    LEVEL("关卡", "LVL_"),
    WIDGET("UMG控件", "WBP_"),
    ENUM("枚举", "E_"),
    STRUCT("结构体", "F_"),
    INTERFACE("接口", "I_"),
    DATA_TABLE("数据表", "DT_"),
    CURVE("曲线", "CR_"),
    UNKNOWN("未知", "");

    companion object {
        fun fromName(name: String): AssetType {
            // 先匹配有明确前缀的
            val matched = values().filter { it.prefix.isNotEmpty() }.find { name.startsWith(it.prefix) }
            return matched ?: UNKNOWN
        }
    }
}

/**
 * 孤立资源风险等级
 */
enum class OrphanRiskLevel {
    NONE,   // 无风险（引用数>=2）
    LOW,    // 低风险（引用数=1）
    HIGH    // 高风险（引用数=0且非关卡）
}

/**
 * UE5 资源数据模型
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
 * 项目扫描结果
 */
data class ScanResult(
    val projectPath: String,
    val projectName: String,
    val totalAssets: Int,
    val totalSize: Long,
    val assetsByType: Map<AssetType, Int>,
    val allAssets: List<UEAsset>,    // 所有扫描到的资源
    val orphanAssets: List<UEAsset>, // 孤立资源（allAssets的子集）
    val scanTime: Long = System.currentTimeMillis()
)

/**
 * 依赖图节点
 */
data class DependencyNode(
    val assetId: String,
    val assetName: String,
    val assetType: AssetType,
    val dependencies: List<String>,
    val depth: Int = 0
)

/**
 * 分析报告
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
