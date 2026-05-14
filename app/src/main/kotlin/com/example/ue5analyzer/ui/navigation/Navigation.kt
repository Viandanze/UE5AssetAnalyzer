package com.example.ue5analyzer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 导航目标
 */
sealed class Screen(val route: String) {
    object Scan : Screen("scan")
    object Stats : Screen("stats")
    object Report : Screen("report")
    object AssetDetail : Screen("asset/{assetId}") {
        fun createRoute(assetId: String) = "asset/$assetId"
    }
    object ProjectList : Screen("projects")
}

/**
 * 底部导航项
 */
data class BottomNavItem(
    val screen: Screen,
    val title: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(
        screen = Screen.Scan,
        title = "扫描",
        icon = Icons.Default.FolderOpen
    ),
    BottomNavItem(
        screen = Screen.Stats,
        title = "统计",
        icon = Icons.Default.Analytics
    ),
    BottomNavItem(
        screen = Screen.Report,
        title = "报告",
        icon = Icons.Default.Description
    )
)
