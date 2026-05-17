package com.example.ue5analyzer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation Targets
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
 * Bottom Navigation Items
 */
data class BottomNavItem(
    val screen: Screen,
    val title: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(
        screen = Screen.Scan,
        title = "Scan",
        icon = Icons.Default.FolderOpen
    ),
    BottomNavItem(
        screen = Screen.Stats,
        title = "Stats",
        icon = Icons.Default.Analytics
    ),
    BottomNavItem(
        screen = Screen.Report,
        title = "Report",
        icon = Icons.Default.Description
    )
)
