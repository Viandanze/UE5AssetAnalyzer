package com.example.ue5analyzer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ue5analyzer.data.manager.ThemePreferencesManager
import com.example.ue5analyzer.ui.navigation.Screen
import com.example.ue5analyzer.ui.navigation.bottomNavItems
import com.example.ue5analyzer.ui.screens.AssetDetailScreen
import com.example.ue5analyzer.ui.screens.ReportScreen
import com.example.ue5analyzer.ui.screens.ProjectListScreen
import com.example.ue5analyzer.ui.screens.ScanScreen
import com.example.ue5analyzer.ui.screens.StatsScreen
import com.example.ue5analyzer.ui.viewmodel.MainViewModel

/**
 * Main Screen - Bottom Navigation Container
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    themePreferencesManager: ThemePreferencesManager? = null
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    // Determine whether to show bottom navigation bar
    val showBottomBar = when (currentDestination?.route) {
        Screen.AssetDetail.route -> false
        Screen.ProjectList.route -> false
        else -> true
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title
                                )
                            },
                            label = { Text(item.title) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == item.screen.route
                            } == true,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    // Pop to the start destination of navigation graph
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    // Avoid creating multiple instances of the same destination
                                    launchSingleTop = true
                                    // Restore previously saved state
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Scan.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Scan screen
            composable(Screen.Scan.route) {
                ScanScreen(
                    viewModel = viewModel,
                    onAssetClick = { asset ->
                        navController.navigate(Screen.AssetDetail.createRoute(asset.id))
                    },
                    onHistoryClick = {
                        navController.navigate(Screen.ProjectList.route)
                    },
                    themePreferencesManager = themePreferencesManager
                )
            }
            
            // Stats screen
            composable(Screen.Stats.route) {
                StatsScreen(viewModel = viewModel)
            }
            
            // Report screen
            composable(Screen.Report.route) {
                ReportScreen(viewModel = viewModel)
            }
            
            // Asset Detail Screen
            composable(
                route = Screen.AssetDetail.route,
                arguments = listOf(
                    navArgument("assetId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val assetId = backStackEntry.arguments?.getString("assetId") ?: ""
                AssetDetailScreen(
                    assetId = assetId,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onAssetClick = { clickedAssetId ->
                        // Allow deep navigation, add each detail to back stack
                        navController.navigate(Screen.AssetDetail.createRoute(clickedAssetId))
                    }
                )
            }
            
            // History Projects list screen
            composable(Screen.ProjectList.route) {
                ProjectListScreen(
                    projects = viewModel.projects.collectAsState(initial = emptyList()).value,
                    onProjectClick = { projectId ->
                        viewModel.loadProject(projectId)
                        navController.navigate(Screen.Scan.route) {
                            popUpTo(Screen.Scan.route) { inclusive = true }
                        }
                    },
                    onProjectDelete = { projectId ->
                        viewModel.deleteProject(projectId)
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
