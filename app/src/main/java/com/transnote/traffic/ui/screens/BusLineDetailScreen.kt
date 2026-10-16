package com.transnote.traffic.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.transnote.traffic.AppContainer
import com.transnote.traffic.data.database.BusStationEntity
import com.transnote.traffic.data.database.BusStationWithTransfers
import com.transnote.traffic.ui.components.ConfirmDialog
import com.transnote.traffic.ui.components.DragReorderColumn
import com.transnote.traffic.ui.components.LabeledTextField
import com.transnote.traffic.ui.components.MapPoint
import com.transnote.traffic.ui.components.SectionTitle
import com.transnote.traffic.ui.components.StationTrackMap
import com.transnote.traffic.ui.components.TransferTag
import com.transnote.traffic.ui.components.TransNoteCard
import com.transnote.traffic.ui.components.TransNoteTopBar
import com.transnote.traffic.ui.theme.TransNoteTheme
import com.transnote.traffic.utils.CoordinateUtils
import com.transnote.traffic.viewmodel.BusLineDetailViewModel
import com.transnote.traffic.viewmodel.containerViewModelFactory

/**
 * 公交线路详情页（PRD 3.2）：
 * 一键切换上行/下行，列表展示站点，地图绘制站点轨迹，站点拖拽排序与增删。
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
    var newStationName by remember { mutableStateOf("") }
    var confirmDeleteLine by remember { mutableStateOf(false) }
    var pendingDeleteStation by remember { mutableStateOf<BusStationEntity?>(null) }

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
                    IconButton(onClick = onEditLine) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑线路")
                    }
                    IconButton(onClick = { confirmDeleteLine = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除线路",
                            tint = TransNoteTheme.colors.danger
                        )
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
                        Text(
                            state.line?.let { line ->
                                val fare = if (line.fare.isNotBlank()) " · 票价 ${line.fare}" else ""
                                "首末班 ${line.firstBus.ifBlank { "--" }}~" +
                                    "${line.lastBus.ifBlank { "--" }}$fare"
                            } ?: "—",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
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
                                isTransfer = s.isMetroTransfer
                            )
                        } else null
                    },
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
            }

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
                SectionTitle("站点列表（${state.stations.size}）· 长按可拖拽排序")
            }

            item {
                DragReorderColumn(
                    items = state.stations,
                    onMove = viewModel::reorder
                ) { index, wrapper, isDragging ->
                    StationRow(
                        index = index,
                        wrapper = wrapper,
                        isDragging = isDragging,
                        onClick = { onOpenStation(wrapper.station.id) },
                        onDelete = { pendingDeleteStation = wrapper.station },
                        onOpenEntrance = onOpenEntrance
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
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

@Composable
private fun StationRow(
    index: Int,
    wrapper: BusStationWithTransfers,
    isDragging: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onOpenEntrance: (Long, Long) -> Unit
) {
    val station = wrapper.station
    TransNoteCard(
        modifier = Modifier.padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "拖拽排序",
                tint = TransNoteTheme.colors.hintText
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = TransNoteTheme.colors.hintText,
                modifier = Modifier.width(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
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
                        TransferTag(
                            modifier = Modifier.clickableTag {
                                val entrance = wrapper.entrances.singleOrNull()
                                if (entrance != null) {
                                    onOpenEntrance(entrance.metroStationId, entrance.id)
                                } else {
                                    onClick()
                                }
                            }
                        )
                    }
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
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除站点",
                    tint = TransNoteTheme.colors.danger
                )
            }
        }
    }
}

private fun Modifier.clickableTag(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
