package com.transnote.traffic.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.transnote.traffic.AppContainer
import com.transnote.traffic.data.database.BusStationEntity
import com.transnote.traffic.data.database.BusStationWithTransfers
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
import com.transnote.traffic.ui.components.TimetableContent
import com.transnote.traffic.ui.components.TransferTag
import com.transnote.traffic.ui.components.TransNoteCard
import com.transnote.traffic.ui.components.TransNoteTopBar
import com.transnote.traffic.ui.components.ThemedDivider
import com.transnote.traffic.ui.theme.LineColors
import com.transnote.traffic.ui.theme.TransNoteTheme
import com.transnote.traffic.utils.CoordinateUtils
import com.transnote.traffic.utils.StationNameParser
import com.transnote.traffic.viewmodel.BusLineDetailViewModel
import com.transnote.traffic.viewmodel.containerViewModelFactory

/**
 * 公交线路详情页（PRD 3.2）：
 * 一键切换上行/下行，以线路图（一条竖线 + 站点圆点）展示站点，
 * 地图绘制站点轨迹；点击「编辑」后才可增删站点、拖拽排序。
 */
@Composable
fun BusLineDetailScreen(
    container: AppContainer,
    lineId: Long,
    onBack: () -> Unit,
    onEditLine: () -> Unit,
    onOpenStation: (Long) -> Unit,
    onOpenEntrance: (Long, Long) -> Unit
) {
    val viewModel: BusLineDetailViewModel = viewModel(
        factory = containerViewModelFactory { BusLineDetailViewModel(it.repository, lineId) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lineColor = LineColors.line(state.line?.colorHex, MaterialTheme.colorScheme.primary)
    var editMode by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var newStationName by remember { mutableStateOf("") }
    var showBatchAdd by remember { mutableStateOf(false) }
    var confirmDeleteLine by remember { mutableStateOf(false) }
    var pendingDeleteStation by remember { mutableStateOf<BusStationEntity?>(null) }
    // 点击站点后在下方地图上高亮跟随
    var selectedKey by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(state.direction) { selectedKey = null }

    Scaffold(
        topBar = {
            TransNoteTopBar(
                title = state.line?.lineName ?: "公交线路",
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
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        TabRow(
                            selectedTabIndex = state.direction,
                            containerColor = TransNoteTheme.colors.navBar
                        ) {
                            Tab(
                                selected = state.direction == BusStationEntity.DIRECTION_UP,
                                onClick = { viewModel.setDirection(BusStationEntity.DIRECTION_UP) },
                                text = { Text("上行") }
                            )
                            Tab(
                                selected = state.direction == BusStationEntity.DIRECTION_DOWN,
                                onClick = { viewModel.setDirection(BusStationEntity.DIRECTION_DOWN) },
                                text = { Text("下行") }
                            )
                        }
                        val destination = state.line?.let { line ->
                            if (state.direction == BusStationEntity.DIRECTION_UP) line.upDestination
                            else line.downDestination
                        }.orEmpty()
                        if (destination.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
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
                val timetable = state.line?.let { line ->
                    if (state.direction == BusStationEntity.DIRECTION_UP) line.upTimetable
                    else line.downTimetable
                }.orEmpty()
                // 未配置时刻表时不显示该板块
                if (timetable.isNotBlank()) {
                    SectionTitle("时刻表")
                    TransNoteCard { TimetableContent(timetable) }
                }
            }

            item {
                val transferEntrances = state.stations
                    .flatMap { it.entrances }
                    .distinctBy { it.metroStationId to it.name }
                if (transferEntrances.isNotEmpty()) {
                    SectionTitle("沿线可换乘地铁")
                    TransNoteCard {
                        Column {
                            transferEntrances.forEachIndexed { i, entrance ->
                                if (i > 0) {
                                    Spacer(Modifier.height(6.dp))
                                    ThemedDivider()
                                    Spacer(Modifier.height(6.dp))
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenEntrance(entrance.metroStationId, entrance.id) }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.DirectionsSubway,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        state.mapContext.metroStationLabels[entrance.metroStationId]
                                            ?: "地铁站",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        entrance.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TransNoteTheme.colors.hintText
                                    )
                                }
                            }
                        }
                    }
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
                                isTransfer = s.isMetroTransfer,
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
                    lineColor = lineColor
                )
            }

            if (editMode) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LabeledTextField(
                            label = "新增站点",
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
                        Text("批量添加站点（批量编辑）")
                    }
                }
            }

            item {
                SectionTitle("站点列表（${state.stations.size}）")
                Text(
                    if (editMode) "编辑中：点击站点可修改，长按可拖拽排序"
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
                    DragReorderColumn(
                        items = state.stations,
                        onMove = viewModel::reorder
                    ) { index, wrapper, isDragging ->
                        BusStationTimelineItem(
                            index = index,
                            total = state.stations.size,
                            wrapper = wrapper,
                            editMode = true,
                            isDragging = isDragging,
                            isSelected = false,
                            lineColor = lineColor,
                            lineColorHex = state.line?.colorHex.orEmpty(),
                            markers = state.mapContext.markers,
                            metroStationLabels = state.mapContext.metroStationLabels,
                            onClick = { onOpenStation(wrapper.station.id) },
                            onSelect = {},
                            onDelete = { pendingDeleteStation = wrapper.station },
                            onOpenEntrance = onOpenEntrance
                        )
                    }
                } else {
                    Column {
                        state.stations.forEachIndexed { index, wrapper ->
                            BusStationTimelineItem(
                                index = index,
                                total = state.stations.size,
                                wrapper = wrapper,
                                editMode = false,
                                isDragging = false,
                                isSelected = selectedKey == wrapper.station.id,
                                lineColor = lineColor,
                                lineColorHex = state.line?.colorHex.orEmpty(),
                                markers = state.mapContext.markers,
                                metroStationLabels = state.mapContext.metroStationLabels,
                                onClick = { onOpenStation(wrapper.station.id) },
                                onSelect = { selectedKey = wrapper.station.id },
                                onDelete = {},
                                onOpenEntrance = onOpenEntrance
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
            title = { Text("批量添加站点") },
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
            title = "删除线路",
            text = "将删除「${state.line?.lineName.orEmpty()}」及其全部站点，且不可恢复。",
            confirmText = "删除",
            danger = true,
            onConfirm = { viewModel.deleteLine(onBack) },
            onDismiss = { confirmDeleteLine = false }
        )
    }

    pendingDeleteStation?.let { station ->
        ConfirmDialog(
            title = "删除站点",
            text = "确定删除站点「${station.name}」？",
            confirmText = "删除",
            danger = true,
            onConfirm = { viewModel.deleteStation(station) },
            onDismiss = { pendingDeleteStation = null }
        )
    }
}

/**
 * 公交站点行（线路图圆点样式）。
 * 只读模式下点击可在地图上定位高亮；编辑模式下显示拖拽手柄与删除按钮。
 * 站点圆点使用站点自身颜色（未设置则跟随线路颜色），并展示站点标记（多选）。
 */
@Composable
private fun BusStationTimelineItem(
    index: Int,
    total: Int,
    wrapper: BusStationWithTransfers,
    editMode: Boolean,
    isDragging: Boolean,
    isSelected: Boolean,
    lineColor: Color,
    lineColorHex: String,
    markers: List<StationMarkerEntity>,
    metroStationLabels: Map<Long, String>,
    onClick: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onOpenEntrance: (Long, Long) -> Unit
) {
    val station = wrapper.station
    val markerNames = markers.filter { it.id in station.markerIds }.map { it.name }
    StationTimelineRow(
        index = index,
        total = total,
        isTransfer = station.isMetroTransfer,
        isDragging = isDragging,
        isSelected = isSelected,
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
            if (station.isMetroTransfer) {
                Spacer(Modifier.width(8.dp))
                TransferTag()
            }
        }
        // 站点标记（多选），显示在线路上
        if (markerNames.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            MarkerIconRow(names = markerNames, accent = lineColor)
        }
        val sub = buildList {
            if (station.note.isNotBlank()) add(station.note)
            if (CoordinateUtils.isValid(station.latitude, station.longitude)) add("已标注地图点位")
            if (station.imagePaths.isNotEmpty()) add("${station.imagePaths.size} 张图片")
        }.joinToString(" · ")
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = TransNoteTheme.colors.hintText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 关联地铁信息：换乘的地铁线路 / 站点 / 出入口
        wrapper.entrances.forEach { entrance ->
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    onOpenEntrance(entrance.metroStationId, entrance.id)
                }
            ) {
                Icon(
                    Icons.Filled.DirectionsSubway,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "换乘 ${metroStationLabels[entrance.metroStationId] ?: "地铁站"} · ${entrance.name}口",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

