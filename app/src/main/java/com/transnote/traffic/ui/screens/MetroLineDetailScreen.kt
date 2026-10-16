package com.transnote.traffic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.transnote.traffic.AppContainer
import com.transnote.traffic.data.database.MetroStationWithEntrances
import com.transnote.traffic.data.database.StationMarkerEntity
import com.transnote.traffic.ui.components.ConfirmDialog
import com.transnote.traffic.ui.components.DragReorderColumn
import com.transnote.traffic.ui.components.LabeledTextField
import com.transnote.traffic.ui.components.MapPoint
import com.transnote.traffic.ui.components.MapSearchItem
import com.transnote.traffic.ui.components.MarkerIconRow
import com.transnote.traffic.ui.components.SectionTitle
import com.transnote.traffic.ui.components.StationTimelineRow
import com.transnote.traffic.ui.components.StationTrackMap
import com.transnote.traffic.ui.components.Tag
import com.transnote.traffic.ui.components.TimetableContent
import com.transnote.traffic.ui.components.TransNoteCard
import com.transnote.traffic.ui.components.TransNoteTopBar
import com.transnote.traffic.ui.theme.LineColors
import com.transnote.traffic.ui.theme.TransNoteTheme
import com.transnote.traffic.utils.CoordinateUtils
import com.transnote.traffic.utils.StationNameParser
import com.transnote.traffic.viewmodel.MetroLineDetailViewModel
import com.transnote.traffic.viewmodel.containerViewModelFactory
import kotlinx.coroutines.launch

/** 线路轨迹地图在列表中的固定项下标，用于点击站点时把地图滚动到可见位置 */
private const val MAP_ITEM_INDEX = 2

/**
 * 地铁线路详情页（PRD 3.3）：以线路图（竖线 + 站点圆点）展示站点与出入口数量，
 * 地图绘制轨迹；点击「编辑」后才可增删站点、拖拽排序。
 */
@Composable
fun MetroLineDetailScreen(
    container: AppContainer,
    lineId: Long,
    onBack: () -> Unit,
    onEditLine: () -> Unit,
    onOpenStation: (Long) -> Unit
) {
    val viewModel: MetroLineDetailViewModel = viewModel(
        factory = containerViewModelFactory { MetroLineDetailViewModel(it.repository, lineId) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lineColor = LineColors.line(state.line?.colorHex, MaterialTheme.colorScheme.primary)
    val showStationIndex by container.themeManager.showStationIndex
        .collectAsStateWithLifecycle(initialValue = true)
    var editMode by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var newStationName by remember { mutableStateOf("") }
    var showBatchAdd by remember { mutableStateOf(false) }
    var confirmDeleteLine by remember { mutableStateOf(false) }
    var pendingDeleteStation by remember { mutableStateOf<Pair<Long, String>?>(null) }
    // 点击站点后在地图上高亮跟随
    var selectedKey by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val selectStation: (Long) -> Unit = { id ->
        selectedKey = id
        scope.launch { listState.animateScrollToItem(MAP_ITEM_INDEX) }
    }

    Scaffold(
        topBar = {
            TransNoteTopBar(
                title = state.line?.lineName ?: "地铁线路",
                onBack = onBack,
                actions = {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "收藏",
                            tint = if (state.line?.isFavorite == true) TransNoteTheme.colors.warning
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = viewModel::togglePinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = "置顶",
                            tint = if (state.line?.isPinned == true) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { editMode = !editMode }) {
                        Icon(
                            if (editMode) Icons.Filled.Check else Icons.Filled.Edit,
                            contentDescription = if (editMode) "完成" else "编辑站点",
                            tint = if (editMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("编辑线路信息") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = { menuExpanded = false; onEditLine() }
                            )
                            DropdownMenuItem(
                                text = { Text("删除线路", color = TransNoteTheme.colors.danger) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = TransNoteTheme.colors.danger
                                    )
                                },
                                onClick = { menuExpanded = false; confirmDeleteLine = true }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TransNoteCard {
                    Column {
                        val info = state.line?.let { line ->
                            buildList {
                                if (line.firstBus.isNotBlank() || line.lastBus.isNotBlank()) {
                                    add("首末班 ${line.firstBus.ifBlank { "--" }}~${line.lastBus.ifBlank { "--" }}")
                                }
                                if (line.fare.isNotBlank()) add("票价 ${line.fare}")
                            }.joinToString(" · ")
                        }.orEmpty()
                        if (info.isNotBlank()) {
                            Text(
                                info,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        val destination = state.line?.destination.orEmpty()
                        if (destination.isNotBlank()) {
                            if (info.isNotBlank()) Spacer(Modifier.height(8.dp))
                            Text(
                                "开往 $destination",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                val timetable = state.line?.timetable.orEmpty()
                // 未配置时刻表时不显示该板块
                if (timetable.isNotBlank()) {
                    SectionTitle("时刻表")
                    TransNoteCard { TimetableContent(timetable) }
                }
            }

            item {
                SectionTitle("线路轨迹")
                StationTrackMap(
                    points = state.stations.mapNotNull { wrapper ->
                        val s = wrapper.station
                        if (CoordinateUtils.isValid(s.latitude, s.longitude)) {
                            MapPoint(
                                latitude = s.latitude!!,
                                longitude = s.longitude!!,
                                title = s.name,
                                isTransfer = wrapper.entrances.isNotEmpty(),
                                key = s.id,
                                colorHex = s.colorHex,
                                markers = state.mapContext.markerNames(s.markerIds)
                            )
                        } else null
                    },
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    selectedKey = selectedKey,
                    onSelectKey = { selectedKey = it },
                    defaultCenter = state.mapContext.cityCenter,
                    searchItems = state.mapContext.searchStations.map {
                        MapSearchItem(it.name, it.latitude, it.longitude, it.subtitle, it.markers)
                    },
                    emptyHint = "为地铁站点设置地图坐标后将在此绘制轨迹",
                    lineColor = lineColor
                )
            }

            if (editMode) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LabeledTextField(
                            label = "新增地铁站点",
                            value = newStationName,
                            onValueChange = { newStationName = it },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            viewModel.addStation(newStationName)
                            newStationName = ""
                        }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "添加站点",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                item {
                    TextButton(onClick = { showBatchAdd = true }) {
                        Text("批量添加站点")
                    }
                }
            }

            item {
                SectionTitle("地铁站点（${state.stations.size}）")
                Text(
                    if (editMode) "编辑中：点击站点可修改出入口，长按可拖拽排序"
                    else "只读展示；点击站点可在地图上定位高亮，点右上角「编辑」后可增删、拖拽或修改",
                    style = MaterialTheme.typography.bodySmall,
                    color = TransNoteTheme.colors.hintText
                )
            }

            item {
                if (state.stations.isEmpty()) {
                    Text(
                        if (editMode) "暂无站点，请在上方添加" else "暂无站点，点右上角「编辑」后可添加站点",
                        style = MaterialTheme.typography.bodySmall,
                        color = TransNoteTheme.colors.hintText,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else if (editMode) {
                    DragReorderColumn(items = state.stations, onMove = viewModel::reorder) { index, wrapper, isDragging ->
                        MetroStationTimelineItem(
                            index = index,
                            total = state.stations.size,
                            wrapper = wrapper,
                            editMode = true,
                            isDragging = isDragging,
                            isSelected = false,
                            showIndex = showStationIndex,
                            lineColor = lineColor,
                            lineColorHex = state.line?.colorHex.orEmpty(),
                            markers = state.mapContext.markers,
                            onClick = { onOpenStation(wrapper.station.id) },
                            onSelect = {},
                            onDelete = { pendingDeleteStation = wrapper.station.id to wrapper.station.name }
                        )
                    }
                } else {
                    Column {
                        state.stations.forEachIndexed { index, wrapper ->
                            MetroStationTimelineItem(
                                index = index,
                                total = state.stations.size,
                                wrapper = wrapper,
                                editMode = false,
                                isDragging = false,
                                isSelected = selectedKey == wrapper.station.id,
                                showIndex = showStationIndex,
                                lineColor = lineColor,
                                lineColorHex = state.line?.colorHex.orEmpty(),
                                markers = state.mapContext.markers,
                                onClick = { onOpenStation(wrapper.station.id) },
                                onSelect = { selectStation(wrapper.station.id) },
                                onDelete = {}
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showBatchAdd) {
        var batchText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBatchAdd = false },
            shape = MaterialTheme.shapes.large,
            title = { Text("批量添加地铁站点") },
            text = {
                Column {
                    Text(
                        "每行一个站点，或用 、 → 逗号分隔",
                        style = MaterialTheme.typography.bodySmall,
                        color = TransNoteTheme.colors.hintText
                    )
                    Spacer(Modifier.height(8.dp))
                    LabeledTextField(
                        label = "站点列表",
                        value = batchText,
                        onValueChange = { batchText = it },
                        singleLine = false,
                        minLines = 4,
                        placeholder = "火车站\n人民广场\n市图书馆"
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addStations(StationNameParser.parse(batchText))
                    showBatchAdd = false
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showBatchAdd = false }) {
                    Text("取消", color = TransNoteTheme.colors.hintText)
                }
            }
        )
    }

    if (confirmDeleteLine) {
        ConfirmDialog(
            title = "删除地铁线路",
            text = "将删除「${state.line?.lineName.orEmpty()}」及其全部站点与出入口，且不可恢复。",
            confirmText = "删除",
            danger = true,
            onConfirm = { viewModel.deleteLine(onBack) },
            onDismiss = { confirmDeleteLine = false }
        )
    }

    pendingDeleteStation?.let { (id, name) ->
        ConfirmDialog(
            title = "删除地铁站点",
            text = "确定删除站点「$name」及其全部出入口？",
            confirmText = "删除",
            danger = true,
            onConfirm = { viewModel.deleteStation(id) },
            onDismiss = { pendingDeleteStation = null }
        )
    }
}

/** 地铁站点行（线路图圆点样式）。只读模式下点击可在地图定位高亮。 */
@Composable
private fun MetroStationTimelineItem(
    index: Int,
    total: Int,
    wrapper: MetroStationWithEntrances,
    editMode: Boolean,
    isDragging: Boolean,
    isSelected: Boolean,
    showIndex: Boolean,
    lineColor: Color,
    lineColorHex: String,
    markers: List<StationMarkerEntity>,
    onClick: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val station = wrapper.station
    val markerNames = markers.filter { it.id in station.markerIds }.map { it.name }
    StationTimelineRow(
        index = index,
        total = total,
        isTransfer = wrapper.entrances.isNotEmpty(),
        isDragging = isDragging,
        isSelected = isSelected,
        showIndex = showIndex,
        lineColor = lineColor,
        pointColor = LineColors.station(station.colorHex, lineColorHex, lineColor),
        onClick = if (editMode) onClick else onSelect,
        trailing = {
            if (editMode) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = "拖拽排序",
                    tint = TransNoteTheme.colors.hintText
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除站点",
                        tint = TransNoteTheme.colors.danger
                    )
                }
            }
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                station.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (wrapper.entrances.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Tag(
                    "可换乘",
                    TransNoteTheme.colors.transferTagBg,
                    MaterialTheme.colorScheme.primary,
                    icon = Icons.Filled.Login
                )
            }
        }
        // 站点标记（多选），显示在线路上
        if (markerNames.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            MarkerIconRow(names = markerNames, accent = lineColor)
        }
        Text(
            "出入口 ${wrapper.entrances.size} 个" +
                if (wrapper.entrances.isNotEmpty()) {
                    "：" + wrapper.entrances.joinToString("、") { it.name }
                } else "",
            style = MaterialTheme.typography.bodySmall,
            color = TransNoteTheme.colors.hintText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
