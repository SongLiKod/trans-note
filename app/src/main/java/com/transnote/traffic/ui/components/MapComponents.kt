package com.transnote.traffic.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** 地图打点数据 */
data class MapPoint(
    val latitude: Double,
    val longitude: Double,
    val title: String = "",
    val isTransfer: Boolean = false
)

/**
 * 站点轨迹地图（TechDoc 5.4）：绘制站点标记与线路轨迹，换乘站点高亮。
 * 仅供地图瓦片加载需要网络，业务数据本身全部离线。
 */
@Composable
fun StationTrackMap(
    points: List<MapPoint>,
    modifier: Modifier = Modifier,
    drawTrack: Boolean = true,
    emptyHint: String = "暂无坐标点，请为站点设置地图位置"
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val warning = TransNoteTheme.colors.warning.toArgb()

    val mapView = remember {
        MapView(context).apply {
            setTileSource(GaodeTileSource())
            setMultiTouchControls(true)
            setUseDataConnection(true)
            setTilesScaledToDpi(true)
            minZoomLevel = 3.0
            maxZoomLevel = 18.0
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
        // observer 不会补发已经发生过的 ON_RESUME，需在页面已 RESUMED 时立即启动瓦片加载，
        // 否则地图一直停留在灰色无瓦片状态。
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Box(modifier = modifier.clip(MaterialTheme.shapes.medium)) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.overlays.clear()
                // 高德瓦片为 GCJ-02，站点存的是 WGS-84，显示前统一转换
                val geoPoints = points.map { p ->
                    val (lat, lng) = CoordinateUtils.wgs84ToGcj02(p.latitude, p.longitude)
                    GeoPoint(lat, lng)
                }

                if (drawTrack && geoPoints.size >= 2) {
                    val polyline = Polyline(map).apply {
                        setPoints(geoPoints)
                        outlinePaint.color = primary
                        outlinePaint.strokeWidth = 8f
                        outlinePaint.isAntiAlias = true
                    }
                    map.overlays.add(polyline)
                }

                points.forEach { point ->
                    val (lat, lng) = CoordinateUtils.wgs84ToGcj02(point.latitude, point.longitude)
                    val marker = Marker(map).apply {
                        position = GeoPoint(lat, lng)
                        title = point.title
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    // 换乘站点使用警示色高亮标记
                    if (point.isTransfer) {
                        runCatching { marker.icon?.mutate()?.setTint(warning) }
                    }
                    map.overlays.add(marker)
                }

                runCatching {
                    if (geoPoints.size >= 2) {
                        map.zoomToBoundingBox(BoundingBox.fromGeoPoints(geoPoints), true, 96)
                    } else if (geoPoints.size == 1) {
                        map.controller.setZoom(16.0)
                        map.controller.setCenter(geoPoints.first())
                    }
                }
                map.invalidate()
            }
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
    }
}

/**
 * 地图选点（PRD 3.2 / 3.3：标记地图经纬度）。单击地图即选中该点坐标。
 */
@Composable
fun LocationPickerMap(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onPick: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val primary = MaterialTheme.colorScheme.primary.toArgb()

    var current by remember {
        mutableStateOf(
            if (initialLatitude != null && initialLongitude != null) initialLatitude to initialLongitude
            else null
        )
    }
    val initialPoint = remember {
        val base = if (initialLatitude != null && initialLongitude != null) {
            initialLatitude to initialLongitude
        } else {
            39.9087 to 116.3975
        }
        val (lat, lng) = CoordinateUtils.wgs84ToGcj02(base.first, base.second)
        GeoPoint(lat, lng)
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(GaodeTileSource())
            setMultiTouchControls(true)
            setUseDataConnection(true)
            setTilesScaledToDpi(true)
            minZoomLevel = 3.0
            maxZoomLevel = 18.0
            controller.setZoom(15.0)
            controller.setCenter(initialPoint)
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
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Box(modifier = modifier.clip(MaterialTheme.shapes.medium)) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.overlays.clear()
                map.overlays.add(
                    MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            if (p != null) {
                                // 高德瓦片返回 GCJ-02，转回 WGS-84 存储
                                val (lat, lng) = CoordinateUtils.gcj02ToWgs84(p.latitude, p.longitude)
                                current = lat to lng
                                onPick(lat, lng)
                            }
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    })
                )

                current?.let { (lat, lng) ->
                    val (gLat, gLng) = CoordinateUtils.wgs84ToGcj02(lat, lng)
                    val marker = Marker(map).apply {
                        position = GeoPoint(gLat, gLng)
                        title = "已选位置"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    runCatching { marker.icon?.mutate()?.setTint(primary) }
                    map.overlays.add(marker)
                }
                map.invalidate()
            }
        )

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(8.dp),
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
