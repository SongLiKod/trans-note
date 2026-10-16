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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.transnote.traffic.data.database.BusStationEntity
import com.transnote.traffic.data.database.BusStationWithTransfers
import com.transnote.traffic.data.database.StationMarkerEntity
import com.transnote.traffic.repository.PassingBusLine
import com.transnote.traffic.repository.TransNoteRepository
import com.transnote.traffic.ui.components.ConfirmDialog
import com.transnote.traffic.utils.AmapBusLine
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
import kotlinx.coroutines.launch

/** 线路轨迹地图在列表中的固定项下标，用于点击站点时把地图滚动到可见位置 */
private const val MAP_ITEM_INDEX = 3

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
    val showStationIndex by container.themeManager.showStationIndex
        .collectAsStateWithLifecycle(initialValue = true)
    val showPassingLines by container.themeManager.showPassingLines
        .collectAsStateWithLifecycle(initialValue = true)
    var passingStation by remember { mutableStateOf<BusStationEntity?>(null) }
    // 从高德更新线路
    val amapKey by container.themeManager.amapKey.collectAsStateWithLifecycle(initialValue = "")
    val amapCandidates by viewModel.amapCandidates.collectAsStateWithLifecycle()
    val amapBusy by viewModel.amapBusy.collectAsStateWithLifecycle()
    val amapMessage by viewModel.amapMessage.collectAsStateWithLifecycle()
    var showAmapUpdate by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var editMode by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var newStationName by remember { mutableStateOf("") }
    var showBatchAdd by remember { mutableStateOf(false) }
    var confirmDeleteLine by remember { mutableStateOf(false) }
    var pendingDeleteStation by remember { mutableStateOf<BusStationEntity?>(null) }
    // 点击站点后在地图上高亮跟随
    var selectedKey by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 点击站点：高亮该站并把地图滚动到可见位置（地图居中显示该站）
    val selectStation: (Long) -> Unit = { id ->
        selectedKey = id
        scope.launch { listState.animateScrollToItem(MAP_ITEM_INDEX) }
    }

    LaunchedEffect(state.direction) { selectedKey = null }

    LaunchedEffect(amapMessage) {
        amapMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeAmapMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                text = { Text("从高德更新线路") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    showAmapUpdate = true
                                    viewModel.searchAmapUpdate(amapKey, state.line?.lineName.orEmpty())
                                }
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
                            showIndex = showStationIndex,
                            showPassing = showPassingLines,
                            lineColor = lineColor,
                            lineColorHex = state.line?.colorHex.orEmpty(),
                            markers = state.mapContext.markers,
                            metroStationLabels = state.mapContext.metroStationLabels,
                            onClick = { onOpenStation(wrapper.station.id) },
                            onSelect = {},
                            onDelete = { pendingDeleteStation = wrapper.station },
                            onShowPassing = { passingStation = wrapper.station },
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
                                showIndex = showStationIndex,
                                showPassing = showPassingLines,
                                lineColor = lineColor,
                                lineColorHex = state.line?.colorHex.orEmpty(),
                                markers = state.mapContext.markers,
                                metroStationLabels = state.mapContext.metroStationLabels,
                                onClick = { onOpenStation(wrapper.station.id) },
                                onSelect = { selectStation(wrapper.station.id) },
                                onDelete = {},
                                onShowPassing = { passingStation = wrapper.station },
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

    passingStation?.let { station ->
        PassingLinesDialog(
            repository = container.repository,
            station = station,
            onDismiss = { passingStation = null }
        )
    }

    if (showAmapUpdate) {
        AmapUpdateDialog(
            lineName = state.line?.lineName.orEmpty(),
            busy = amapBusy,
            message = amapMessage,
            candidates = amapCandidates,
            onSearch = { keyword -> viewModel.searchAmapUpdate(amapKey, keyword) },
            onApply = { result ->
                viewModel.applyAmapUpdate(amapKey, result)
                showAmapUpdate = false
            },
            onDismiss = {
                showAmapUpdate = false
                viewModel.clearAmapUpdate()
            }
        )
    }
}

/** 从高德更新已有线路：输入/确认线路名，选择结果后更新线路与站点 */
@Composable
private fun AmapUpdateDialog(
    lineName: String,
    busy: Boolean,
    message: String?,
    candidates: List<AmapBusLine>,
    onSearch: (String) -> Unit,
    onApply: (AmapBusLine) -> Unit,
    onDismiss: () -> Unit
) {
    var keyword by remember { mutableStateOf(lineName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("从高德更新线路") },
        text = {
            Column {
                Text(
                    "按线路名重新获取首末班、开往方向与站点坐标（保留原备注 / 图片），并重新识别站点周边地铁 / 火车站 / 机场。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TransNoteTheme.colors.hintText
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text("线路名称 / 编号") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { onSearch(keyword) }, enabled = !busy) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = "查询",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = TransNoteTheme.colors.hintText
                    )
                }
                if (candidates.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(candidates, key = { it.id + it.name }) { line ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onApply(line) }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(line.name, style = MaterialTheme.typography.bodyLarge)
                                val detail = buildString {
                                    val route = listOf(line.startStop, line.endStop)
                                        .filter { it.isNotBlank() }.joinToString(" → ")
                                    if (route.isNotBlank()) append(route)
                                    if (line.stations.isNotEmpty()) {
                                        if (isNotEmpty()) append(" · ")
                                        append("${line.stations.size} 站")
                                    }
                                    if (line.startTime.isNotBlank()) {
                                        if (isNotEmpty()) append(" · ")
                                        append("首末班 ${line.startTime}~${line.endTime}")
                                    }
                                }
                                if (detail.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        detail,
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/**
 * 「经过该站点的线路」弹窗：按坐标/同名聚合所有线路，标明上行/下行与开往方向。
 */
@Composable
private fun PassingLinesDialog(
    repository: TransNoteRepository,
    station: BusStationEntity,
    onDismiss: () -> Unit
) {
    var lines by remember(station.id) { mutableStateOf<List<PassingBusLine>?>(null) }
    LaunchedEffect(station.id) {
        lines = repository.findPassingBusLines(
            station.latitude ?: 0.0,
            station.longitude ?: 0.0,
            station.name
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("经过「${station.name}」的线路") },
        text = {
            val list = lines
            when {
                list == null -> Text(
                    "查询中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = TransNoteTheme.colors.hintText
                )
                list.isEmpty() -> Text(
                    "暂无其它线路经过该站点（同一站点需坐标相近或站名相同）",
                    style = MaterialTheme.typography.bodySmall,
                    color = TransNoteTheme.colors.hintText
                )
                else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(list, key = { "${it.lineId}-${it.direction}" }) { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Filled.DirectionsBus,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.lineName, style = MaterialTheme.typography.bodyLarge)
                                val direction = if (item.direction == 0) "上行" else "下行"
                                val dest = if (item.destination.isBlank()) "" else " · 开往 ${item.destination}"
                                Text(
                                    "$direction$dest",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TransNoteTheme.colors.hintText
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
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
    showIndex: Boolean,
    showPassing: Boolean,
    lineColor: Color,
    lineColorHex: String,
    markers: List<StationMarkerEntity>,
    metroStationLabels: Map<Long, String>,
    onClick: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onShowPassing: () -> Unit,
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
            // 站点右侧按钮：查看经过该站点的所有公交线路（可在设置中关闭）
            if (showPassing && CoordinateUtils.isValid(station.latitude, station.longitude)) {
                IconButton(onClick = onShowPassing) {
                    Icon(
                        Icons.Filled.DirectionsBus,
                        contentDescription = "经过线路",
                        tint = MaterialTheme.colorScheme.primary
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
            // 「已标注地图点位」仅在编辑界面显示，预览界面保持简洁
            if (editMode && CoordinateUtils.isValid(station.latitude, station.longitude)) {
                add("已标注地图点位")
            }
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

