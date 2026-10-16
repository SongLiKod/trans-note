package com.transnote.traffic.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.transnote.traffic.ui.theme.TransNoteTheme
import com.transnote.traffic.utils.CoordinateUtils
import com.transnote.traffic.utils.GaodeTileSource
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

/** 地图打点数据 */
data class MapPoint(
    val latitude: Double,
    val longitude: Double,
    val title: String = "",
    val isTransfer: Boolean = false,
    /** 稳定标识（站点 id），用于点击站点后在地图上高亮跟随 */
    val key: Long = 0L,
    /** 站点自定义颜色（HEX）；空表示跟随线路颜色 */
    val colorHex: String = "",
    /** 站点标记名称（多选），显示在选中站点的地图气泡中 */
    val markers: List<String> = emptyList()
)

/** 地图内搜索用的站点（默认限定当前城市） */
data class MapSearchItem(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val subtitle: String = "",
    val markers: List<String> = emptyList()
)

/** 搜索聚焦点使用的合成 key */
private const val FOCUS_KEY = -1000L
private const val CHINA_CENTER_LAT = 35.0
private const val CHINA_CENTER_LNG = 105.0

/**
 * 创建受生命周期管理的 [MapView]，避免 osmdroid 因缺少 onResume 而不加载瓦片。
 */
@Composable
private fun rememberLifecycleMapView(config: MapView.() -> Unit = {}): MapView {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(GaodeTileSource())
            setMultiTouchControls(true)
            setUseDataConnection(true)
            setTilesScaledToDpi(true)
            minZoomLevel = 3.0
            maxZoomLevel = 18.0
            // 给一个合法的初始 zoom / 中心，避免 osmdroid 在投影 zoom 为 NaN 时
            // zoomToBoundingBox 内部死循环（osmdroid bug #2028）导致主线程卡死。
            controller.setZoom(4.0)
            controller.setCenter(GeoPoint(CHINA_CENTER_LAT, CHINA_CENTER_LNG))
            config()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // observer 不会补发已经发生过的 ON_RESUME，需在页面已 RESUMED 时立即启动瓦片加载
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }
    return mapView
}

/**
 * 线路轨迹绘制层：平滑连线 + 站点圆点。
 * - 普通站点：小圆点（线路色 / 站点自定义色）；
 * - 换乘站点：较大圆点（警示色）；
 * - 选中 / 搜索聚焦站点：大圆点（强调色）并显示站名气泡与标记图标。
 *
 * 连线粗细、圆点大小、连线与圆点颜色均来自线路的「线路图样式」设置。
 */
private class TrackOverlay(
    private val stations: List<MapPoint>,
    private val lineColor: Int,
    private val dotColor: Int,
    private val transferColor: Int,
    private val highlightColor: Int,
    private val labelTextColor: Int,
    private val labelBgColor: Int,
    private val railWidthDp: Int,
    private val dotSizeDp: Int,
    private val iconBitmaps: Map<String, Bitmap>,
    private val drawTrack: Boolean,
    private val onTapStation: ((Long) -> Unit)?
) : Overlay() {

    var highlightKey: Long? = null

    /**
     * 站点坐标预转换：WGS-84（本地存储）→ GCJ-02（高德瓦片坐标系）。
     * 否则圆点/连线会与地图实际位置偏移数百米，导致「列表点的站点」和「地图上的点」不重合。
     */
    private val geoPoints: List<GeoPoint> = stations.map {
        val (lat, lng) = CoordinateUtils.wgs84ToGcj02(it.latitude, it.longitude)
        GeoPoint(lat, lng)
    }

    /** 站点圆点颜色：优先使用站点自定义色，其次换乘警示色，最后线路圆点色 */
    private fun dotColorOf(station: MapPoint): Int {
        val hex = station.colorHex.trim()
        if (hex.isNotEmpty()) {
            return runCatching {
                Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
            }.getOrDefault(dotColor)
        }
        return if (station.isTransfer) transferColor else dotColor
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || stations.isEmpty()) return
        val density = mapView.resources.displayMetrics.density
        val projection = mapView.projection
        val pts = geoPoints.map { projection.toPixels(it, null) }

        // ---- 轨迹线（粗细来自线路图样式设置）----
        if (drawTrack && pts.size >= 2) {
            val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lineColor
                style = Paint.Style.STROKE
                strokeWidth = railWidthDp.coerceIn(1, 12) * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                alpha = 225
            }
            val path = Path().apply {
                moveTo(pts.first().x.toFloat(), pts.first().y.toFloat())
                for (i in 1 until pts.size) lineTo(pts[i].x.toFloat(), pts[i].y.toFloat())
            }
            canvas.drawPath(path, track)
        }

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelBgColor
            style = Paint.Style.FILL
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(lineColor, PorterDuff.Mode.SRC_IN)
        }
        val maxTextWidth = canvas.width * 0.7f
        val labelText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelTextColor
            textSize = 12f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        // 圆点基准半径来自「圆点大小」设置（直径 dp）
        val baseRadius = dotSizeDp.coerceIn(8, 32) / 2f * density
        val iconSize = 14f * density
        val iconGap = 3f * density

        pts.forEachIndexed { i, p ->
            val station = stations[i]
            val selected = highlightKey != null && station.key == highlightKey
            val radius = when {
                selected -> baseRadius * 1.7f
                station.isTransfer -> baseRadius * 1.25f
                else -> baseRadius
            }
            canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), radius + 3f * density, ring)
            dot.color = when {
                selected -> highlightColor
                else -> dotColorOf(station)
            }
            canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), radius, dot)

            if (selected && station.title.isNotBlank()) {
                // 气泡：第一行为站名，第二行为站点标记「通用图标」（不写文字）
                val titleLine = TextUtils.ellipsize(
                    station.title, labelText, maxTextWidth, TextUtils.TruncateAt.END
                ).toString()
                val icons = station.markers.mapNotNull { iconBitmaps[markerIconKey(it)] }
                val iconsWidth = if (icons.isEmpty()) 0f
                else icons.size * iconSize + (icons.size - 1) * iconGap
                val padH = 6f * density
                val padV = 4f * density
                val lineGap = if (icons.isEmpty()) 0f else 3f * density
                val contentWidth = maxOf(labelText.measureText(titleLine), iconsWidth)
                val contentHeight = labelText.textSize + lineGap +
                    (if (icons.isEmpty()) 0f else iconSize)
                val halfWidth = contentWidth / 2f + padH
                val top = p.y - radius - 10f * density - contentHeight - padV * 2
                val rect = RectF(
                    p.x - halfWidth,
                    top,
                    p.x + halfWidth,
                    top + contentHeight + padV * 2
                )
                canvas.drawRoundRect(rect, 6f * density, 6f * density, labelBg)
                val baseline = top + padV + labelText.textSize - labelText.descent()
                canvas.drawText(titleLine, p.x.toFloat(), baseline, labelText)
                if (icons.isNotEmpty()) {
                    var x = p.x - iconsWidth / 2f
                    val iconTop = top + padV + labelText.textSize + lineGap
                    icons.forEach { bitmap ->
                        canvas.drawBitmap(
                            bitmap, null,
                            RectF(x, iconTop, x + iconSize, iconTop + iconSize),
                            iconPaint
                        )
                        x += iconSize + iconGap
                    }
                }
            }
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        if (onTapStation == null || stations.isEmpty()) return false
        val threshold = 28f * mapView.resources.displayMetrics.density
        val projection = mapView.projection
        var bestKey: Long? = null
        var bestDist = Float.MAX_VALUE
        stations.forEachIndexed { i, station ->
            val p = projection.toPixels(geoPoints[i], null)
            val dist = hypot(e.x - p.x, e.y - p.y)
            if (dist < threshold && dist < bestDist) {
                bestDist = dist
                bestKey = station.key
            }
        }
        val key = bestKey ?: return false
        onTapStation.invoke(key)
        return true
    }
}

/**
 * 挂起直到 [MapView] 完成首次布局。
 * 用布局监听而非轮询帧，确保「先离屏预取、后滚入可视区」的地图也能在布局完成后正确定位相机。
 */
private suspend fun awaitMapLayout(mapView: MapView) = suspendCancellableCoroutine<Unit> { cont ->
    fun ready() = mapView.isLayoutOccurred() && mapView.width > 0 && mapView.height > 0
    if (ready()) {
        cont.resume(Unit)
        return@suspendCancellableCoroutine
    }
    var listener: View.OnLayoutChangeListener? = null
    listener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
        if (ready()) {
            v.removeOnLayoutChangeListener(listener)
            if (cont.isActive) cont.resume(Unit)
        }
    }
    mapView.addOnLayoutChangeListener(listener)
    cont.invokeOnCancellation { listener?.let { mapView.removeOnLayoutChangeListener(it) } }
}

/** 选点地图上的标记点（白边圆点） */
private class PickerDotOverlay(private val color: Int) : Overlay() {
    var point: GeoPoint? = null

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val p = point ?: return
        val density = mapView.resources.displayMetrics.density
        val px = mapView.projection.toPixels(p, null)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(px.x.toFloat(), px.y.toFloat(), 9f * density, ring)
        canvas.drawCircle(px.x.toFloat(), px.y.toFloat(), 6f * density, fill)
    }
}

/**
 * 共享地图画布：绘制线路轨迹与站点，并处理相机跟随。
 * 瓦片加载需要网络，业务数据本身全部离线。
 */
@Composable
private fun TrackMapCanvas(
    points: List<MapPoint>,
    drawTrack: Boolean,
    highlightKey: Long?,
    onTapStation: ((Long) -> Unit)?,
    defaultCenter: Pair<Double, Double>?,
    lineColorArgb: Int,
    modifier: Modifier = Modifier,
    dotColorArgb: Int = lineColorArgb,
    railWidthDp: Int = 4,
    dotSizeDp: Int = 14
) {
    val lineColor = lineColorArgb
    val transferColor = TransNoteTheme.colors.warning.toArgb()
    val highlightColor = TransNoteTheme.colors.danger.toArgb()
    val labelTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val labelBgColor = MaterialTheme.colorScheme.surface.toArgb()
    // 标记图标：栅格化为位图供原生 Canvas 绘制（地图气泡内不写文字）
    val iconBitmaps = rememberMarkerIconBitmaps()

    val mapView = rememberLifecycleMapView()

    // 站点集合签名：仅在站点内容真正变化时才重新定位相机，
    // 避免因每次重组产生的新 List 实例（引用不同）而反复触发相机动画。
    val pointsSignature = points.joinToString(";") { "${it.key}#${it.latitude},${it.longitude}" }
    val density = LocalDensity.current

    // 相机定位 / 自动缩放放在 LaunchedEffect 中，只在目标站点或站点集合变化时执行一次。
    // 绝不能放在 AndroidView.update 内：该 update 会在列表预取（PrefetchScheduler）与
    // 重排时被反复调用，导致 zoomToBoundingBox/animateTo 反复执行，主线程 99% 占用而 ANR。
    LaunchedEffect(highlightKey, pointsSignature, defaultCenter) {
        // osmdroid bug #2028：地图首次完成布局前，投影的 zoom 为 NaN，
        // zoomToBoundingBox 会在 Projection.getCloserPixel 中 `while(pPixel >= limit) pPixel -= NaN`
        // 造成主线程死循环（应用无响应）。必须等布局完成后再操作相机。
        awaitMapLayout(mapView)

        // 双保险：即便已完成布局，zoom 仍可能非法（尚未设置过缩放），
        // 直接调用 zoomToBoundingBox 会触发死循环——先落到一个合法 zoom。
        if (!mapView.zoomLevelDouble.isFinite()) {
            mapView.controller.setZoom(12.0)
        }

        val target = highlightKey?.let { key -> points.firstOrNull { it.key == key } }
        when {
            target != null -> {
                val (lat, lng) = CoordinateUtils.wgs84ToGcj02(target.latitude, target.longitude)
                mapView.controller.animateTo(GeoPoint(lat, lng), 16.0, 400L)
            }
            points.isNotEmpty() -> {
                val geo = points.map {
                    val (la, ln) = CoordinateUtils.wgs84ToGcj02(it.latitude, it.longitude)
                    GeoPoint(la, ln)
                }
                runCatching {
                    if (geo.size == 1) {
                        mapView.controller.setZoom(16.0)
                        mapView.controller.setCenter(geo.first())
                    } else if (mapView.zoomLevelDouble.isFinite()) {
                        mapView.zoomToBoundingBox(
                            BoundingBox.fromGeoPoints(geo),
                            false,
                            (46 * density.density).toInt()
                        )
                    }
                }
            }
            else -> {
                val base = defaultCenter ?: (CHINA_CENTER_LAT to CHINA_CENTER_LNG)
                val (lat, lng) = CoordinateUtils.wgs84ToGcj02(base.first, base.second)
                mapView.controller.setCenter(GeoPoint(lat, lng))
                mapView.controller.setZoom(if (defaultCenter != null) 13.0 else 4.0)
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlays.clear()

            val overlay = TrackOverlay(
                stations = points,
                lineColor = lineColor,
                dotColor = dotColorArgb,
                transferColor = transferColor,
                highlightColor = highlightColor,
                labelTextColor = labelTextColor,
                labelBgColor = labelBgColor,
                railWidthDp = railWidthDp,
                dotSizeDp = dotSizeDp,
                iconBitmaps = iconBitmaps,
                drawTrack = drawTrack,
                onTapStation = onTapStation
            )
            overlay.highlightKey = highlightKey
            map.overlays.add(overlay)
            map.invalidate()
        }
    )
}

/**
 * 站点轨迹地图（TechDoc 5.4）：绘制站点标记与线路轨迹，换乘站点高亮。
 * 支持点击站点高亮跟随、全屏预览与站名搜索（默认限定当前城市）。
 */
@Composable
fun StationTrackMap(
    points: List<MapPoint>,
    modifier: Modifier = Modifier,
    drawTrack: Boolean = true,
    selectedKey: Long? = null,
    onSelectKey: ((Long) -> Unit)? = null,
    defaultCenter: Pair<Double, Double>? = null,
    searchItems: List<MapSearchItem> = emptyList(),
    allowFullscreen: Boolean = true,
    emptyHint: String = "暂无坐标点，请为站点设置地图位置",
    lineColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    /** 站点圆点默认颜色（线路图样式；默认跟随线路颜色） */
    dotColor: androidx.compose.ui.graphics.Color = lineColor,
    /** 连线粗细（dp） */
    railWidthDp: Int = 4,
    /** 站点圆点大小（dp，直径） */
    dotSizeDp: Int = 14
) {
    var fullscreen by remember { mutableStateOf(false) }
    val lineArgb = lineColor.toArgb()
    val dotArgb = dotColor.toArgb()

    Box(modifier = modifier.clip(MaterialTheme.shapes.medium)) {
        TrackMapCanvas(
            points = points,
            drawTrack = drawTrack,
            highlightKey = selectedKey,
            onTapStation = onSelectKey,
            defaultCenter = defaultCenter,
            lineColorArgb = lineArgb,
            modifier = Modifier.fillMaxSize(),
            dotColorArgb = dotArgb,
            railWidthDp = railWidthDp,
            dotSizeDp = dotSizeDp
        )

        if (points.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(12.dp)
            ) {
                Text(
                    text = emptyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = TransNoteTheme.colors.hintText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        if (allowFullscreen && (points.isNotEmpty() || searchItems.isNotEmpty())) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                IconButton(onClick = { fullscreen = true }) {
                    Icon(
                        Icons.Filled.Fullscreen,
                        contentDescription = "全屏查看",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (fullscreen) {
        FullscreenTrackMapDialog(
            points = points,
            selectedKey = selectedKey,
            onSelectKey = onSelectKey,
            defaultCenter = defaultCenter,
            searchItems = searchItems,
            lineColorArgb = lineArgb,
            dotColorArgb = dotArgb,
            railWidthDp = railWidthDp,
            dotSizeDp = dotSizeDp,
            onDismiss = { fullscreen = false }
        )
    }
}

/** 全屏地图：支持站名搜索（默认限定当前城市）并聚焦到该站点 */
@Composable
private fun FullscreenTrackMapDialog(
    points: List<MapPoint>,
    selectedKey: Long?,
    onSelectKey: ((Long) -> Unit)?,
    defaultCenter: Pair<Double, Double>?,
    searchItems: List<MapSearchItem>,
    lineColorArgb: Int,
    dotColorArgb: Int = lineColorArgb,
    railWidthDp: Int = 4,
    dotSizeDp: Int = 14,
    onDismiss: () -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var focus by remember { mutableStateOf<MapPoint?>(null) }

    val results = remember(keyword, searchItems) {
        val q = keyword.trim()
        if (q.isEmpty()) emptyList()
        else searchItems.filter { it.name.contains(q, ignoreCase = true) }.take(30)
    }

    val merged = remember(points, focus) {
        val f = focus
        if (f == null || points.any { it.key == f.key }) points else points + f
    }
    val highlightKey = focus?.key ?: selectedKey

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                TrackMapCanvas(
                    points = merged,
                    drawTrack = true,
                    highlightKey = highlightKey,
                    onTapStation = { key ->
                        if (key >= 0) {
                            focus = null
                            onSelectKey?.invoke(key)
                        }
                    },
                    defaultCenter = defaultCenter,
                    lineColorArgb = lineColorArgb,
                    modifier = Modifier.fillMaxSize(),
                    dotColorArgb = dotColorArgb,
                    railWidthDp = railWidthDp,
                    dotSizeDp = dotSizeDp
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp
                    ) {
                        OutlinedTextField(
                            value = keyword,
                            onValueChange = { keyword = it },
                            placeholder = { Text("搜索当前城市的站名") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (keyword.isNotEmpty()) {
                                    IconButton(onClick = { keyword = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "清除")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (results.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                                items(results) { item ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                focus = MapPoint(
                                                    latitude = item.latitude,
                                                    longitude = item.longitude,
                                                    title = item.name,
                                                    key = FOCUS_KEY,
                                                    markers = item.markers
                                                )
                                                keyword = ""
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                        if (item.subtitle.isNotBlank()) {
                                            Text(
                                                item.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TransNoteTheme.colors.hintText,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (item.markers.isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                            MarkerIconRow(
                                                names = item.markers,
                                                accent = MaterialTheme.colorScheme.primary,
                                                max = 4
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                ) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

/**
 * 地图选点（PRD 3.2 / 3.3：标记地图经纬度）。单击地图即选中该点坐标。
 * [defaultCenter] 为当前城市中心（WGS-84），用于未选点时的默认定位。
 */
@Composable
fun LocationPickerMap(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onPick: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
    defaultCenter: Pair<Double, Double>? = null
) {
    val hasInitial = CoordinateUtils.isValid(initialLatitude, initialLongitude)
    var current by remember {
        mutableStateOf(
            if (hasInitial) initialLatitude!! to initialLongitude!! else null
        )
    }
    val start = remember {
        val wgs = when {
            hasInitial -> initialLatitude!! to initialLongitude!!
            defaultCenter != null -> defaultCenter
            else -> CHINA_CENTER_LAT to CHINA_CENTER_LNG
        }
        CoordinateUtils.wgs84ToGcj02(wgs.first, wgs.second)
    }
    val zoom = if (hasInitial || defaultCenter != null) 15.0 else 4.0
    val primary = MaterialTheme.colorScheme.primary.toArgb()

    val mapView = rememberLifecycleMapView {
        controller.setZoom(zoom)
        controller.setCenter(GeoPoint(start.first, start.second))
    }
    val dotOverlay = remember { PickerDotOverlay(primary) }

    Box(modifier = modifier.clip(MaterialTheme.shapes.medium)) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.overlays.clear()
                // 已选点标记（页面上方绘制）
                current?.let { (lat, lng) ->
                    if (dotOverlay.point == null) {
                        val (gLat, gLng) = CoordinateUtils.wgs84ToGcj02(lat, lng)
                        dotOverlay.point = GeoPoint(gLat, gLng)
                    }
                }
                map.overlays.add(dotOverlay)
                map.overlays.add(
                    MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            if (p != null) {
                                // 高德瓦片返回 GCJ-02，转回 WGS-84 存储
                                val (lat, lng) = CoordinateUtils.gcj02ToWgs84(p.latitude, p.longitude)
                                current = lat to lng
                                dotOverlay.point = p
                                onPick(lat, lng)
                                map.invalidate()
                            }
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    })
                )
                map.invalidate()
            }
        )

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Text(
                text = "点击地图选择坐标",
                style = MaterialTheme.typography.bodySmall,
                color = TransNoteTheme.colors.hintText,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
