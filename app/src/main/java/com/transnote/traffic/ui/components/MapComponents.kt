package com.transnote.traffic.ui.components

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
    val key: Long = 0L
)

/** 地图内搜索用的站点（默认限定当前城市） */
data class MapSearchItem(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val subtitle: String = ""
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
 * - 普通站点：小圆点（线路色）；
 * - 换乘站点：较大圆点（警示色）；
 * - 选中 / 搜索聚焦站点：大圆点（强调色）并显示站名气泡。
 */
private class TrackOverlay(
    private val stations: List<MapPoint>,
    private val lineColor: Int,
    private val transferColor: Int,
    private val highlightColor: Int,
    private val labelTextColor: Int,
    private val labelBgColor: Int,
    private val drawTrack: Boolean,
    private val onTapStation: ((Long) -> Unit)?
) : Overlay() {

    var highlightKey: Long? = null

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || stations.isEmpty()) return
        val density = mapView.resources.displayMetrics.density
        val projection = mapView.projection
        val pts = stations.map { projection.toPixels(GeoPoint(it.latitude, it.longitude), null) }

        // ---- 轨迹线 ----
        if (drawTrack && pts.size >= 2) {
            val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lineColor
                style = Paint.Style.STROKE
                strokeWidth = 5f * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                alpha = 205
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
        val labelText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelTextColor
            textSize = 12f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        pts.forEachIndexed { i, p ->
            val station = stations[i]
            val selected = highlightKey != null && station.key == highlightKey
            val radius = when {
                selected -> 10f * density
                station.isTransfer -> 7.5f * density
                else -> 6f * density
            }
            canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), radius + 3f * density, ring)
            dot.color = when {
                selected -> highlightColor
                station.isTransfer -> transferColor
                else -> lineColor
            }
            canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), radius, dot)

            if (selected && station.title.isNotBlank()) {
                val halfWidth = labelText.measureText(station.title) / 2f
                val padH = 6f * density
                val padV = 4f * density
                val textHeight = labelText.textSize
                val top = p.y - radius - 10f * density - textHeight - padV * 2
                val rect = RectF(
                    p.x - halfWidth - padH,
                    top,
                    p.x + halfWidth + padH,
                    top + textHeight + padV * 2
                )
                canvas.drawRoundRect(rect, 6f * density, 6f * density, labelBg)
                canvas.drawText(
                    station.title,
                    p.x.toFloat(),
                    rect.bottom - padV - labelText.descent(),
                    labelText
                )
            }
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        if (onTapStation == null || stations.isEmpty()) return false
        val threshold = 28f * mapView.resources.displayMetrics.density
        val projection = mapView.projection
        var bestKey: Long? = null
        var bestDist = Float.MAX_VALUE
        stations.forEach { station ->
            val p = projection.toPixels(GeoPoint(station.latitude, station.longitude), null)
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
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary.toArgb()
    val transferColor = TransNoteTheme.colors.warning.toArgb()
    val highlightColor = TransNoteTheme.colors.danger.toArgb()
    val labelTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val labelBgColor = MaterialTheme.colorScheme.surface.toArgb()

    val mapView = rememberLifecycleMapView {
        val base = defaultCenter ?: points.firstOrNull()?.let { it.latitude to it.longitude }
        if (base != null) {
            val (lat, lng) = CoordinateUtils.wgs84ToGcj02(base.first, base.second)
            controller.setCenter(GeoPoint(lat, lng))
            controller.setZoom(13.0)
        } else {
            val (lat, lng) = CoordinateUtils.wgs84ToGcj02(CHINA_CENTER_LAT, CHINA_CENTER_LNG)
            controller.setCenter(GeoPoint(lat, lng))
            controller.setZoom(4.0)
        }
    }

    val lastFit = remember { arrayOfNulls<List<MapPoint>>(1) }
    val lastCameraKey = remember { arrayOfNulls<Long>(1) }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            val density = map.resources.displayMetrics.density
            map.overlays.clear()

            val overlay = TrackOverlay(
                stations = points,
                lineColor = lineColor,
                transferColor = transferColor,
                highlightColor = highlightColor,
                labelTextColor = labelTextColor,
                labelBgColor = labelBgColor,
                drawTrack = drawTrack,
                onTapStation = onTapStation
            )
            overlay.highlightKey = highlightKey
            map.overlays.add(overlay)

            val target = highlightKey?.let { key -> points.firstOrNull { it.key == key } }
            when {
                target != null -> {
                    if (lastCameraKey[0] != highlightKey) {
                        val (lat, lng) = CoordinateUtils.wgs84ToGcj02(target.latitude, target.longitude)
                        map.controller.animateTo(GeoPoint(lat, lng), 16.0, 400L)
                        lastCameraKey[0] = highlightKey
                    }
                }
                else -> {
                    lastCameraKey[0] = null
                    if (points.isNotEmpty() && lastFit[0] != points) {
                        val geo = points.map {
                            val (la, ln) = CoordinateUtils.wgs84ToGcj02(it.latitude, it.longitude)
                            GeoPoint(la, ln)
                        }
                        runCatching {
                            if (geo.size == 1) {
                                map.controller.setZoom(16.0)
                                map.controller.setCenter(geo.first())
                            } else {
                                map.zoomToBoundingBox(
                                    BoundingBox.fromGeoPoints(geo),
                                    true,
                                    (46 * density).toInt()
                                )
                            }
                        }
                        lastFit[0] = points
                    }
                }
            }
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
    emptyHint: String = "暂无坐标点，请为站点设置地图位置"
) {
    var fullscreen by remember { mutableStateOf(false) }

    Box(modifier = modifier.clip(MaterialTheme.shapes.medium)) {
        TrackMapCanvas(
            points = points,
            drawTrack = drawTrack,
            highlightKey = selectedKey,
            onTapStation = onSelectKey,
            defaultCenter = defaultCenter,
            modifier = Modifier.fillMaxSize()
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
                    modifier = Modifier.fillMaxSize()
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
                                                    key = FOCUS_KEY
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
