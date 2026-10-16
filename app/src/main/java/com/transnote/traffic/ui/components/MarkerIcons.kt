package com.transnote.traffic.ui.components

import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 地铁「M」图标（与主流地图 App 一致，如高德/谷歌地图地铁站用 M 表示）。
 * 用单一路径绘制字母 M，渲染时按主题色着色。
 */
val MetroMIcon: ImageVector = ImageVector.Builder(
    name = "MetroM",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(4f, 20f)
        lineTo(4f, 4f)
        lineTo(7f, 4f)
        lineTo(12f, 13f)
        lineTo(17f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 20f)
        lineTo(17f, 20f)
        lineTo(17f, 10f)
        lineTo(13.5f, 16.5f)
        lineTo(10.5f, 16.5f)
        lineTo(7f, 10f)
        lineTo(7f, 20f)
        close()
    }
}.build()

/**
 * 火车站 / 高铁图标：中国铁路路徽（12306 官网图标样式）。
 * 整体为火车头正面：上半部为「人」字、下半部为钢轨断面「工」字，外加圆形外框。
 * 用描边绘制，着色时整体按前景色渲染。
 */
val RailwayIcon: ImageVector = ImageVector.Builder(
    name = "Railway",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        // 外圆
        moveTo(12f, 2f)
        arcTo(10f, 10f, 0f, false, true, 12f, 22f)
        arcTo(10f, 10f, 0f, false, true, 12f, 2f)
        close()
        // 「人」（火车头顶部）
        moveTo(5.8f, 12.6f)
        lineTo(12f, 4.8f)
        lineTo(18.2f, 12.6f)
        // 「工」（钢轨断面）
        moveTo(8.2f, 12.6f)
        lineTo(15.8f, 12.6f)
        moveTo(12f, 12.6f)
        lineTo(12f, 17.6f)
        moveTo(8.6f, 17.6f)
        lineTo(15.4f, 17.6f)
    }
}.build()

/**
 * 站点标记图标目录（通用图标）。
 *
 * PRD：线路上展示标记时不再写文字，改用通用图标（地铁用 M 图标，火车用铁路徽标），
 * 与主流地图 App 一致，紧凑位置也能看清。自定义标记统一使用通用定位图标。
 */
val markerIconCatalog: List<Pair<String, ImageVector>> = listOf(
    "metro" to MetroMIcon,
    "train" to RailwayIcon,
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
