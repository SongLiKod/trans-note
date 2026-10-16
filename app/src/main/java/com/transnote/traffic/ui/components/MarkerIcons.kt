package com.transnote.traffic.ui.components

import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Train
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 站点标记图标目录（通用图标）。
 *
 * PRD：线路上展示标记时不再写文字，改用通用图标（例如地铁用 M 形地铁图标），
 * 与主流地图 App 一致，紧凑位置也能看清。自定义标记统一使用通用定位图标。
 */
val markerIconCatalog: List<Pair<String, ImageVector>> = listOf(
    "metro" to Icons.Filled.DirectionsSubway,
    "train" to Icons.Filled.Train,
    "plane" to Icons.Filled.Flight,
    "bus" to Icons.Filled.DirectionsBus,
    "taxi" to Icons.Filled.LocalTaxi,
    "park" to Icons.Filled.Park,
    "mall" to Icons.Filled.Storefront,
    "hospital" to Icons.Filled.LocalHospital,
    "school" to Icons.Filled.School,
    "hotel" to Icons.Filled.Hotel,
    "food" to Icons.Filled.Restaurant,
    "generic" to Icons.Filled.Place
)

/**
 * 按标记名称推断图标：关键字命中即用对应通用图标，未命中（自定义标记）使用通用定位图标。
 * 例如「地铁」→ M 形地铁图标，「火车站 / 高铁站」→ 火车图标，「飞机 / 机场」→ 飞机图标。
 */
fun markerIconKey(name: String): String {
    val n = name.trim()
    return when {
        n.contains("地铁") || n.contains("轨道") || n.contains("轻轨") -> "metro"
        n.contains("火车") || n.contains("高铁") || n.contains("动车") ||
            n.contains("车站") || n.contains("列车") -> "train"
        n.contains("飞机") || n.contains("机场") || n.contains("航站") || n.contains("航班") -> "plane"
        n.contains("公交") || n.contains("巴士") || n.contains("BRT") -> "bus"
        n.contains("出租") || n.contains("网约") || n.contains("打车") -> "taxi"
        n.contains("公园") || n.contains("景区") || n.contains("广场") -> "park"
        n.contains("商场") || n.contains("购物") || n.contains("超市") || n.contains("市场") -> "mall"
        n.contains("医院") || n.contains("门诊") || n.contains("卫生") -> "hospital"
        n.contains("学校") || n.contains("大学") || n.contains("学院") || n.contains("中学") -> "school"
        n.contains("酒店") || n.contains("宾馆") || n.contains("住宿") -> "hotel"
        n.contains("餐厅") || n.contains("美食") || n.contains("小吃") -> "food"
        else -> "generic"
    }
}

/** 标记名称 → 图标（Compose 直接使用） */
fun markerIconOf(name: String): ImageVector =
    markerIconCatalog.firstOrNull { it.first == markerIconKey(name) }?.second
        ?: Icons.Filled.Place

/**
 * 把图标目录栅格化为位图，供 osmdroid 的原生 Canvas 绘制标记图标使用
 * （地图气泡无法直接绘制 Compose 图标）。返回 key → Bitmap。
 */
@Composable
fun rememberMarkerIconBitmaps(sizeDp: Float = 14f): Map<String, Bitmap> {
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.dp.roundToPx() }.coerceAtLeast(1)
    // 固定长度目录，保证 Composable 调用顺序稳定
    val painters = markerIconCatalog.map { rememberVectorPainter(it.second) }
    return remember(sizePx) {
        markerIconCatalog.mapIndexed { index, entry ->
            entry.first to painters[index].rasterize(sizePx, density)
        }.toMap()
    }
}

/** 将 [Painter] 绘制到新建的位图上 */
private fun Painter.rasterize(sizePx: Int, density: Density): Bitmap {
    val image = ImageBitmap(sizePx, sizePx)
    val size = Size(sizePx.toFloat(), sizePx.toFloat())
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), size) {
        with(this@rasterize) { draw(size) }
    }
    return image.asAndroidBitmap()
}
