package com.transnote.traffic.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import com.transnote.traffic.viewmodel.HomeTab
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
    val topLevelRoutes = setOf(Routes.HOME_BUS, Routes.HOME_METRO, Routes.SETTINGS)
    val showBottomBar = currentRoute in topLevelRoutes

    // 底部导航：公交线路 / 地铁线路 / 设置（苹果悬浮胶囊风格）
    val navItems = listOf(
        BottomNavItem(Routes.HOME_BUS, "公交线路", Icons.Filled.DirectionsBus),
        BottomNavItem(Routes.HOME_METRO, "地铁线路", Icons.Filled.DirectionsSubway),
        BottomNavItem(Routes.SETTINGS, "设置", Icons.Filled.Settings)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME_BUS,
            modifier = Modifier.fillMaxSize(),
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
            composable(Routes.HOME_BUS) {
                HomeScreen(
                    container = container,
                    tab = HomeTab.BUS,
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenBusLine = { navController.navigate(Routes.busLine(it)) },
                    onOpenMetroLine = { navController.navigate(Routes.metroLine(it)) },
                    onCreateLine = { type, id, cityId ->
                        val typeKey = if (type == LineType.METRO) "METRO" else "BUS"
                        navController.navigate(Routes.editLine(typeKey, id, cityId))
                    }
                )
            }

            composable(Routes.HOME_METRO) {
                HomeScreen(
                    container = container,
                    tab = HomeTab.METRO,
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenBusLine = { navController.navigate(Routes.busLine(it)) },
                    onOpenMetroLine = { navController.navigate(Routes.metroLine(it)) },
                    onCreateLine = { type, id, cityId ->
                        val typeKey = if (type == LineType.METRO) "METRO" else "BUS"
                        navController.navigate(Routes.editLine(typeKey, id, cityId))
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
                    },
                    navArgument("cityId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) { entry ->
                val type = if (entry.arguments?.getString("type") == "METRO") LineType.METRO
                else LineType.BUS
                val id = entry.arguments?.getLong("id") ?: 0L
                val cityId = entry.arguments?.getLong("cityId") ?: 0L
                val ocrLines by entry.savedStateHandle
                    .getStateFlow<ArrayList<String>?>("ocr_result", null)
                    .collectAsStateWithLifecycle()
                LineEditScreen(
                    container = container,
                    lineType = type,
                    lineId = id,
                    cityId = cityId,
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
                    onOpenLine = { navController.navigate(Routes.busLine(it)) },
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
                    onOpenLine = { navController.navigate(Routes.metroLine(it)) },
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

        if (showBottomBar) {
            FloatingBottomNav(
                items = navItems,
                currentRoute = currentRoute,
                onSelect = { route ->
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/** 底部导航项 */
private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * 苹果悬浮风格的底部导航：居中的圆角胶囊，图标 + 文字，选中高亮；背景透明，浮于内容之上。
 */
@Composable
private fun FloatingBottomNav(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = TransNoteTheme.colors.navBar,
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val selected = currentRoute == item.route
                    val tint = if (selected) MaterialTheme.colorScheme.primary
                    else TransNoteTheme.colors.hintText
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelect(item.route) }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(item.icon, contentDescription = item.label, tint = tint)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = tint
                        )
                    }
                }
            }
        }
    }
}
