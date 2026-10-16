package com.transnote.traffic.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.transnote.traffic.AppContainer
import com.transnote.traffic.ui.navigation.Routes
import com.transnote.traffic.ui.screens.BusLineDetailScreen
import com.transnote.traffic.ui.screens.BusStationDetailScreen
import com.transnote.traffic.ui.screens.EntranceScreen
import com.transnote.traffic.ui.screens.HomeScreen
import com.transnote.traffic.ui.screens.LineEditScreen
import com.transnote.traffic.ui.screens.MetroLineDetailScreen
import com.transnote.traffic.ui.screens.MetroStationDetailScreen
import com.transnote.traffic.ui.screens.OcrScanScreen
import com.transnote.traffic.ui.screens.SearchScreen
import com.transnote.traffic.ui.screens.SettingsScreen
import com.transnote.traffic.ui.theme.TransNoteTheme
import com.transnote.traffic.utils.ThemeMode
import com.transnote.traffic.viewmodel.LineType

/**
 * APP 根容器：底部导航（线路 / 搜索 / 设置）+ 二级页面路由。
 * 页面切换使用左右滑动 + 淡入淡出动画（PRD 4 动画规范）。
 */
@Composable
fun TransNoteRoot(
    container: AppContainer,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevelRoutes = setOf(Routes.HOME, Routes.SEARCH, Routes.SETTINGS)
    val showBottomBar = currentRoute in topLevelRoutes

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = TransNoteTheme.colors.navBar) {
                    NavigationBarItem(
                        selected = currentRoute == Routes.HOME,
                        onClick = {
                            if (currentRoute != Routes.HOME) {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.HOME) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Filled.DirectionsBus, contentDescription = null) },
                        label = { Text("线路") },
                        colors = navItemColors()
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SEARCH,
                        onClick = {
                            if (currentRoute != Routes.SEARCH) {
                                navController.navigate(Routes.SEARCH) { launchSingleTop = true }
                            }
                        },
                        icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        label = { Text("搜索") },
                        colors = navItemColors()
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = {
                            if (currentRoute != Routes.SETTINGS) {
                                navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                            }
                        },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("设置") },
                        colors = navItemColors()
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(300),
                    initialOffsetX = { it }
                ) + fadeIn(tween(300))
            },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(300),
                    targetOffsetX = { -it / 4 }
                ) + fadeOut(tween(300))
            },
            popEnterTransition = {
                slideInHorizontally(
                    animationSpec = tween(300),
                    initialOffsetX = { -it / 4 }
                ) + fadeIn(tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(300),
                    targetOffsetX = { it }
                ) + fadeOut(tween(300))
            }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    container = container,
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenBusLine = { navController.navigate(Routes.busLine(it)) },
                    onOpenMetroLine = { navController.navigate(Routes.metroLine(it)) },
                    onCreateLine = { type, id ->
                        val typeKey = if (type == LineType.METRO) "METRO" else "BUS"
                        navController.navigate(Routes.editLine(typeKey, id))
                    }
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    container = container,
                    onOpenBusLine = { navController.navigate(Routes.busLine(it)) },
                    onOpenMetroLine = { navController.navigate(Routes.metroLine(it)) },
                    onOpenBusStation = { navController.navigate(Routes.busStation(it)) },
                    onOpenMetroStation = { navController.navigate(Routes.metroStation(it)) },
                    onOpenEntrance = { stationId, entranceId ->
                        navController.navigate(Routes.entrance(stationId, entranceId))
                    }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    themeMode = themeMode,
                    onThemeChange = onThemeChange
                )
            }

            composable(
                route = Routes.EDIT_LINE,
                arguments = listOf(
                    navArgument("type") { defaultValue = "BUS" },
                    navArgument("id") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) { entry ->
                val type = if (entry.arguments?.getString("type") == "METRO") LineType.METRO
                else LineType.BUS
                val id = entry.arguments?.getLong("id") ?: 0L
                val ocrLines by entry.savedStateHandle
                    .getStateFlow<ArrayList<String>?>("ocr_result", null)
                    .collectAsStateWithLifecycle()
                LineEditScreen(
                    container = container,
                    lineType = type,
                    lineId = id,
                    ocrLines = ocrLines,
                    onConsumeOcr = { entry.savedStateHandle["ocr_result"] = null },
                    onOpenOcr = { navController.navigate(Routes.OCR) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.OCR) {
                OcrScanScreen(
                    container = container,
                    onApply = { lines ->
                        navController.previousBackStackEntry?.savedStateHandle
                            ?.set("ocr_result", ArrayList(lines))
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.BUS_LINE,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                BusLineDetailScreen(
                    container = container,
                    lineId = id,
                    onBack = { navController.popBackStack() },
                    onEditLine = { navController.navigate(Routes.editLine("BUS", id)) },
                    onOpenStation = { navController.navigate(Routes.busStation(it)) },
                    onOpenEntrance = { stationId, entranceId ->
                        navController.navigate(Routes.entrance(stationId, entranceId))
                    }
                )
            }

            composable(
                route = Routes.METRO_LINE,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                MetroLineDetailScreen(
                    container = container,
                    lineId = id,
                    onBack = { navController.popBackStack() },
                    onEditLine = { navController.navigate(Routes.editLine("METRO", id)) },
                    onOpenStation = { navController.navigate(Routes.metroStation(it)) }
                )
            }

            composable(
                route = Routes.BUS_STATION,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                BusStationDetailScreen(
                    container = container,
                    stationId = id,
                    onBack = { navController.popBackStack() },
                    onOpenEntrance = { stationId, entranceId ->
                        navController.navigate(Routes.entrance(stationId, entranceId))
                    }
                )
            }

            composable(
                route = Routes.METRO_STATION,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                MetroStationDetailScreen(
                    container = container,
                    stationId = id,
                    onBack = { navController.popBackStack() },
                    onOpenEntrance = { stationId, entranceId ->
                        navController.navigate(Routes.entrance(stationId, entranceId))
                    }
                )
            }

            composable(
                route = Routes.ENTRANCE,
                arguments = listOf(
                    navArgument("stationId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument("id") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) { entry ->
                EntranceScreen(
                    container = container,
                    stationId = entry.arguments?.getLong("stationId") ?: 0L,
                    entranceId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = TransNoteTheme.colors.transferTagBg,
    unselectedIconColor = TransNoteTheme.colors.hintText,
    unselectedTextColor = TransNoteTheme.colors.hintText
)
