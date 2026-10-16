package com.transnote.traffic.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.transnote.traffic.ui.theme.TransNoteTheme
import kotlin.math.abs
import kotlinx.coroutines.launch

/** 拖拽手柄固定高度 */
private val HandleHeight = 44.dp
/** 迷你档正文区高度（不含手柄） */
private val MiniContentHeight = 120.dp

/**
 * 底部可拖拽折叠的轨迹地图容器（公交 / 地铁线路详情共用）。
 *
 * 四档高度锚点：隐藏（仅保留手柄，地图不渲染）/ 迷你 / 半屏 / 展开；
 * 拖动手柄调整高度并在松手时吸附到最近一档，点击手柄循环切换下一档。
 *
 * 列表通过 [listContent] 提供，会收到一个 `weight(1f)` 修饰符用于占满剩余空间；
 * 面板大小变化时（含隐藏 / 展开）不会因列表点击而改变——由调用方控制 [selectedKey]。
 */
@Composable
fun CollapsibleTrackMapLayout(
    points: List<MapPoint>,
    modifier: Modifier = Modifier,
    selectedKey: Long? = null,
    onSelectKey: ((Long) -> Unit)? = null,
    defaultCenter: Pair<Double, Double>? = null,
    searchItems: List<MapSearchItem> = emptyList(),
    emptyHint: String = "暂无坐标点，请为站点设置地图位置",
    lineColor: Color = MaterialTheme.colorScheme.primary,
    listContent: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val handlePx = with(density) { HandleHeight.toPx() }
        // 四档：隐藏（仅手柄）/ 迷你 / 半屏 / 展开
        val hiddenPx = handlePx
        val minPx = handlePx + with(density) { MiniContentHeight.toPx() }
        val maxPx = with(density) { (maxHeight - 64.dp).coerceAtLeast(280.dp).toPx() }
        val halfPx = ((minPx + maxPx) / 2f).coerceAtLeast(minPx)
        val anchors = listOf(hiddenPx, minPx, halfPx, maxPx)

        var panelHeightPx by remember { mutableFloatStateOf(Float.NaN) }
        val scope = rememberCoroutineScope()
        val currentPx = if (panelHeightPx.isFinite() && panelHeightPx > 0f) {
            panelHeightPx.coerceIn(hiddenPx, maxPx)
        } else minPx
        LaunchedEffect(maxPx) { if (!panelHeightPx.isFinite()) panelHeightPx = minPx }

        fun animateTo(target: Float) {
            scope.launch {
                animate(currentPx, target, animationSpec = tween(260)) { v, _ ->
                    panelHeightPx = v
                }
            }
        }

        fun nextAnchor() {
            val idx = anchors.indexOfFirst { it >= currentPx - 1f }
                .let { if (it < 0) anchors.lastIndex else it }
            animateTo(anchors[(idx + 1) % anchors.size])
        }

        Column(modifier = Modifier.fillMaxSize()) {
            listContent(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { currentPx.toDp() })
            ) {
                TrackMapDragHandle(
                    collapsed = currentPx <= hiddenPx + 1f,
                    onCycle = { nextAnchor() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HandleHeight)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                panelHeightPx = (currentPx - delta).coerceIn(hiddenPx, maxPx)
                            },
                            onDragStopped = {
                                val target = anchors.minByOrNull { abs(it - currentPx) } ?: minPx
                                animateTo(target)
                            }
                        )
                )
                if (currentPx > hiddenPx + 1f) {
                    StationTrackMap(
                        points = points,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        selectedKey = selectedKey,
                        onSelectKey = onSelectKey,
                        defaultCenter = defaultCenter,
                        searchItems = searchItems,
                        emptyHint = emptyHint,
                        lineColor = lineColor
                    )
                }
            }
        }
    }
}

/** 底部地图拖拽手柄：拖动调整高度，点击循环切换档位 */
@Composable
private fun TrackMapDragHandle(
    collapsed: Boolean,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onCycle).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
        )
        Spacer(Modifier.size(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "轨迹地图",
                style = MaterialTheme.typography.labelMedium,
                color = TransNoteTheme.colors.hintText
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (collapsed) Icons.Filled.KeyboardArrowUp
                else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (collapsed) "展开地图" else "收起地图",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
