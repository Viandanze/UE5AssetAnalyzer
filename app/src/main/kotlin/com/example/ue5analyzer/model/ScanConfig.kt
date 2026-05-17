package com.example.ue5analyzer.model

/**
 * Custom Scan Configuration
 * Used to control filtering rules during scanning
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
