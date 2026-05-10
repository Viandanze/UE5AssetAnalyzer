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
import com.example.ue5analyzer.ui.navigation.Screen
import com.example.ue5analyzer.ui.navigation.bottomNavItems
import com.example.ue5analyzer.ui.screens.AssetDetailScreen
import com.example.ue5analyzer.ui.screens.ReportScreen
import com.example.ue5analyzer.ui.screens.ProjectListScreen
import com.example.ue5analyzer.ui.screens.ScanScreen
import com.example.ue5analyzer.ui.screens.StatsScreen
import com.example.ue5analyzer.ui.viewmodel.MainViewModel

/**
 * 主屏幕 - 底部导航容器
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    // 判断是否显示底部导航栏
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
                                    // 弹出到导航图的起始目的地
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    // 避免创建多个相同的目的地
                                    launchSingleTop = true
                                    // 恢复之前保存的状态
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
            // 扫描页面
            composable(Screen.Scan.route) {
                ScanScreen(
                    viewModel = viewModel,
                    onAssetClick = { asset ->
                        navController.navigate(Screen.AssetDetail.createRoute(asset.id))
                    },
                    onHistoryClick = {
                        navController.navigate(Screen.ProjectList.route)
                    }
                )
            }
            
            // 统计页面
            composable(Screen.Stats.route) {
                StatsScreen(viewModel = viewModel)
            }
            
            // 报告页面
            composable(Screen.Report.route) {
                ReportScreen(viewModel = viewModel)
            }
            
            // 资源详情页面
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
                        // 限制导航栈深度，使用 singleTop 模式避免重复
                        navController.navigate(Screen.AssetDetail.createRoute(clickedAssetId)) {
                            // 使用 singleTop 模式，同一资源不重复入栈
                            launchSingleTop = true
                        }
                    }
                )
            }
            
            // 历史项目列表页面
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
