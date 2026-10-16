package com.transnote.traffic.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.transnote.traffic.AppContainer
import com.transnote.traffic.data.database.BusStationEntity
import com.transnote.traffic.data.database.CityEntity
import com.transnote.traffic.ui.components.ColorPickerRow
import com.transnote.traffic.ui.components.DragReorderColumn
import com.transnote.traffic.ui.components.LabeledTextField
import com.transnote.traffic.ui.components.SectionTitle
import com.transnote.traffic.ui.components.StationColorDot
import com.transnote.traffic.ui.components.StationTimelineRow
import com.transnote.traffic.ui.components.ThemedDivider
import com.transnote.traffic.ui.components.TransNoteCard
import com.transnote.traffic.ui.components.TransNoteTopBar
import com.transnote.traffic.ui.theme.LineColors
import com.transnote.traffic.ui.theme.TransNoteTheme
import com.transnote.traffic.utils.StationNameParser
import com.transnote.traffic.viewmodel.LineEditViewModel
import com.transnote.traffic.viewmodel.LineType
import com.transnote.traffic.viewmodel.StationDraft
import com.transnote.traffic.viewmodel.containerViewModelFactory

/**
 * 线路录入 / 编辑页（PRD 3.1）：
 * 手动录入表单 + 拍照 OCR 识别自动填充 + 站点拖拽排序 + 公交上下行独立站点。
 */
@Composable
fun LineEditScreen(
    container: AppContainer,
    lineType: LineType,
    lineId: Long,
    cityId: Long,
    ocrLines: List<String>?,
    onConsumeOcr: () -> Unit,
    onOpenOcr: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: LineEditViewModel = viewModel(
        factory = containerViewModelFactory {
            LineEditViewModel(it.repository, lineType, lineId, cityId)
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var renameIndex by remember { mutableStateOf<Int?>(null) }
    var colorIndex by remember { mutableStateOf<Int?>(null) }
    var newStationName by remember { mutableStateOf("") }
    var showBatchAdd by remember { mutableStateOf(false) }

    // OCR 识别结果自动填充表单
    LaunchedEffect(ocrLines) {
        if (!ocrLines.isNullOrEmpty()) {
            viewModel.applyOcr(ocrLines)
            onConsumeOcr()
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val stations: List<StationDraft> = when (lineType) {
        LineType.BUS -> if (state.editDirection == BusStationEntity.DIRECTION_UP) state.upStations
        else state.downStations
        LineType.METRO -> state.metroStations
    }
    val lineColor = LineColors.line(state.colorHex, MaterialTheme.colorScheme.primary)

    Scaffold(
        topBar = {
            TransNoteTopBar(
                title = if (lineId > 0) "编辑${lineType.label}" else "新增${lineType.label}",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(color = TransNoteTheme.colors.navBar) {
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("保存线路")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            TransNoteCard {
                Column {
                    Text(
                        "线路类型：${lineType.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TransNoteTheme.colors.hintText
                    )
                    Spacer(Modifier.height(8.dp))
                    CitySelector(
                        cities = state.cities,
                        selectedId = state.selectedCityId,
                        onSelect = viewModel::setCityId
                    )
                    Spacer(Modifier.height(8.dp))
                    LabeledTextField(
                        label = "线路名称 / 编号",
                        value = state.lineName,
                        onValueChange = viewModel::setLineName,
                        placeholder = "如：302路 / 地铁2号线"
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabeledTextField(
                            label = "首班",
                            value = state.firstBus,
                            onValueChange = viewModel::setFirstBus,
                            placeholder = "06:00",
                            modifier = Modifier.weight(1f)
                        )
                        LabeledTextField(
                            label = "末班",
                            value = state.lastBus,
                            onValueChange = viewModel::setLastBus,
                            placeholder = "22:30",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LabeledTextField(
                        label = "票价",
                        value = state.fare,
                        onValueChange = viewModel::setFare,
                        placeholder = "2元",
                        keyboardType = KeyboardType.Text
                    )
                    Spacer(Modifier.height(12.dp))
                    ColorPickerRow(
                        label = "线路颜色",
                        selectedHex = state.colorHex,
                        onSelect = viewModel::setColorHex
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onOpenOcr,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("拍照识别站牌（离线 OCR）")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (lineType == LineType.BUS) {
                TabRow(
                    selectedTabIndex = state.editDirection,
                    containerColor = TransNoteTheme.colors.navBar
                ) {
                    Tab(
                        selected = state.editDirection == BusStationEntity.DIRECTION_UP,
                        onClick = { viewModel.setEditDirection(BusStationEntity.DIRECTION_UP) },
                        text = { Text("上行 (${state.upStations.size})") }
                    )
                    Tab(
                        selected = state.editDirection == BusStationEntity.DIRECTION_DOWN,
                        onClick = { viewModel.setEditDirection(BusStationEntity.DIRECTION_DOWN) },
                        text = { Text("下行 (${state.downStations.size})") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (state.editDirection == BusStationEntity.DIRECTION_DOWN && state.upStations.isNotEmpty()) {
                    OutlinedButton(
                        onClick = viewModel::copyUpToDown,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("复制上行站点到下行")
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(4.dp))
            SectionTitle("开往方向与时刻表")

            if (lineType == LineType.BUS) {
                val up = state.editDirection == BusStationEntity.DIRECTION_UP
                val directionLabel = if (up) "上行" else "下行"
                LabeledTextField(
                    label = "${directionLabel}开往方向",
                    value = if (up) state.upDestination else state.downDestination,
                    onValueChange = if (up) viewModel::setUpDestination else viewModel::setDownDestination,
                    placeholder = "如：开往 火车站"
                )
                Spacer(Modifier.height(8.dp))
                LabeledTextField(
                    label = "${directionLabel}时刻表",
                    value = if (up) state.upTimetable else state.downTimetable,
                    onValueChange = if (up) viewModel::setUpTimetable else viewModel::setDownTimetable,
                    singleLine = false,
                    minLines = 2,
                    placeholder = "如：06:00 06:20 06:40，或每行一个发车时间"
                )
            } else {
                LabeledTextField(
                    label = "开往方向",
                    value = state.destination,
                    onValueChange = viewModel::setDestination,
                    placeholder = "如：开往 高铁站"
                )
                Spacer(Modifier.height(8.dp))
                LabeledTextField(
                    label = "时刻表",
                    value = state.timetable,
                    onValueChange = viewModel::setTimetable,
                    singleLine = false,
                    minLines = 2,
                    placeholder = "如：06:00 06:20 06:40，或每行一个发车时间"
                )
            }

            Spacer(Modifier.height(16.dp))

            SectionTitle("站点列表（${stations.size}）")

            Row(verticalAlignment = Alignment.CenterVertically) {
                LabeledTextField(
                    label = "站点名称",
                    value = newStationName,
                    onValueChange = { newStationName = it },
                    placeholder = "输入后点右侧添加",
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

            androidx.compose.material3.TextButton(onClick = { showBatchAdd = true }) {
                Text("批量添加站点（批量编辑）")
            }

            Spacer(Modifier.height(8.dp))

            if (stations.isEmpty()) {
                Text(
                    "暂无站点，可手动添加或使用拍照识别自动填充",
                    style = MaterialTheme.typography.bodySmall,
                    color = TransNoteTheme.colors.hintText,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Text(
                    "长按站点条目可拖拽调整顺序",
                    style = MaterialTheme.typography.bodySmall,
                    color = TransNoteTheme.colors.hintText
                )
                Spacer(Modifier.height(8.dp))
                DragReorderColumn(
                    items = stations,
                    onMove = viewModel::moveStation
                ) { index, item, isDragging ->
                    StationTimelineRow(
                        index = index,
                        total = stations.size,
                        isTransfer = false,
                        isDragging = isDragging,
                        lineColor = lineColor,
                        pointColor = LineColors.station(item.colorHex, state.colorHex, lineColor),
                        onClick = { renameIndex = index },
                        trailing = {
                            StationColorDot(
                                color = LineColors.station(item.colorHex, state.colorHex, lineColor),
                                onClick = { colorIndex = index }
                            )
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = "拖拽排序",
                                tint = TransNoteTheme.colors.hintText
                            )
                            IconButton(onClick = { renameIndex = index }) {
                                Icon(Icons.Filled.Edit, contentDescription = "重命名")
                            }
                            IconButton(onClick = { viewModel.removeStation(index) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = TransNoteTheme.colors.danger
                                )
                            }
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TransNoteTheme.colors.hintText,
                                modifier = Modifier.width(22.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showBatchAdd) {
        var batchText by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
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
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.addStations(StationNameParser.parse(batchText))
                    showBatchAdd = false
                }) { Text("添加") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showBatchAdd = false }) {
                    Text("取消", color = TransNoteTheme.colors.hintText)
                }
            }
        )
    }

    renameIndex?.let { index ->
        var value by remember(index) { mutableStateOf(stations.getOrNull(index)?.name.orEmpty()) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameIndex = null },
            shape = MaterialTheme.shapes.large,
            title = { Text("修改站点名称") },
            text = {
                LabeledTextField(
                    label = "站点名称",
                    value = value,
                    onValueChange = { value = it }
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.updateStation(index, value.trim())
                    renameIndex = null
                }) { Text("确定") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { renameIndex = null }) {
                    Text("取消", color = TransNoteTheme.colors.hintText)
                }
            }
        )
    }

    colorIndex?.let { index ->
        val current = stations.getOrNull(index)?.colorHex.orEmpty()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { colorIndex = null },
            shape = MaterialTheme.shapes.large,
            title = { Text("站点颜色") },
            text = {
                ColorPickerRow(
                    label = "选择该站点圆点颜色（可单独设置，默认跟随线路）",
                    selectedHex = current,
                    onSelect = { viewModel.setStationColor(index, it) },
                    inheritLabel = "跟随线路",
                    onInherit = { viewModel.setStationColor(index, "") }
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { colorIndex = null }) {
                    Text("完成")
                }
            }
        )
    }
}

/** 所属城市选择器：新增 / 编辑线路时可切换归属城市 */
@Composable
private fun CitySelector(
    cities: List<CityEntity>,
    selectedId: Long,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val name = cities.firstOrNull { it.cityId == selectedId }?.cityName ?: "默认城市"

    Column {
        Text(
            "所属城市",
            style = MaterialTheme.typography.bodySmall,
            color = TransNoteTheme.colors.hintText
        )
        Spacer(Modifier.height(4.dp))
        Box {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, TransNoteTheme.colors.divider),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                ) {
                    Icon(
                        Icons.Filled.LocationCity,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = "选择城市",
                        tint = TransNoteTheme.colors.hintText
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                cities.forEach { city ->
                    DropdownMenuItem(
                        text = { Text(city.cityName) },
                        onClick = {
                            onSelect(city.cityId)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
