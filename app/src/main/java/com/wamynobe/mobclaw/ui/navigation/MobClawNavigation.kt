package com.wamynobe.mobclaw.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wamynobe.mobclaw.ui.screens.DashboardScreen
import com.wamynobe.mobclaw.ui.screens.OverlayScreen
import com.wamynobe.mobclaw.ui.screens.ProvidersScreen
import com.wamynobe.mobclaw.ui.screens.TaskHistoryScreen
import com.wamynobe.mobclaw.ui.state.ProviderStore
import com.wamynobe.mobclaw.ui.theme.MobClawColors

/**
 * Bottom navigation tabs matching the Stitch design.
 */
enum class MobClawTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    STATUS("status", "Status", Icons.Filled.MonitorHeart),
    TASKS("tasks", "Tasks", Icons.Filled.Terminal),
    LLM("llm", "LLM", Icons.Filled.SettingsSuggest),
    OVERLAY("overlay", "Overlay", Icons.Filled.Layers),
}

@Composable
fun MobClawNavigation(
    providerStore: ProviderStore,
    onExecuteTask: (String) -> Unit,
    onStopAgent: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onMobMockLogin: () -> Unit,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        containerColor = MobClawColors.Background,
        bottomBar = {
            NavigationBar(
                containerColor = MobClawColors.SurfaceContainerLowest,
                contentColor = MobClawColors.OnSurface,
            ) {
                MobClawTab.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MobClawColors.Primary,
                            selectedTextColor = MobClawColors.Primary,
                            unselectedIconColor = MobClawColors.OnSurfaceVariant.copy(alpha = 0.5f),
                            unselectedTextColor = MobClawColors.OnSurfaceVariant.copy(alpha = 0.5f),
                            indicatorColor = MobClawColors.PrimaryContainer.copy(alpha = 0.15f),
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MobClawTab.STATUS.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300))
            },
        ) {
            composable(MobClawTab.STATUS.route) {
                DashboardScreen(
                    onExecuteTask = onExecuteTask,
                    onOpenAccessibility = onOpenAccessibility,
                    onOpenOverlayPermission = onOpenOverlayPermission,
                )
            }
            composable(MobClawTab.TASKS.route) {
                TaskHistoryScreen(onExecuteTask = onExecuteTask)
            }
            composable(MobClawTab.LLM.route) {
                ProvidersScreen(
                    providerStore = providerStore,
                    onMobMockLogin = onMobMockLogin,
                )
            }
            composable(MobClawTab.OVERLAY.route) {
                OverlayScreen(
                    onStopAgent = onStopAgent,
                    onOpenAccessibility = onOpenAccessibility,
                    onOpenOverlayPermission = onOpenOverlayPermission,
                )
            }
        }
    }
}
