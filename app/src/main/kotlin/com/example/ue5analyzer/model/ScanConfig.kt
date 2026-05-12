package com.example.ue5analyzer.model

/**
 * 自定义扫描配置
 * 用于控制扫描过程中的过滤规则
 */
data class ScanConfig(
    val ignoredDirectories: Set<String> = setOf(
        "Intermediate",
        "Saved",
        "DerivedDataCache",
        ".git",
        "Build"
    ),
    val ignoredExtensions: Set<String> = emptySet(),
    val maxFileSize: Long = 0, // 0 = no limit
    val enabled: Boolean = true
) {
    companion object {
        val DEFAULT = ScanConfig()
    }
}
