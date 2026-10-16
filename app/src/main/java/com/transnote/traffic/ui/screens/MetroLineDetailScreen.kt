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
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.transnote.traffic.AppContainer
import com.transnote.traffic.ui.components.ConfirmDialog
import com.transnote.traffic.ui.components.DragReorderColumn
import com.transnote.traffic.ui.components.LabeledTextField
import com.transnote.traffic.ui.components.MapPoint
import com.transnote.traffic.ui.components.SectionTitle
import com.transnote.traffic.ui.components.StationTrackMap
import com.transnote.traffic.ui.components.Tag
import com.transnote.traffic.ui.components.TransNoteCard
import com.transnote.traffic.ui.components.TransNoteTopBar
import com.transnote.traffic.ui.theme.TransNoteTheme
import com.transnote.traffic.utils.CoordinateUtils
import com.transnote.traffic.viewmodel.MetroLineDetailViewModel
import com.transnote.traffic.viewmodel.containerViewModelFactory

/**
 * 地铁线路详情页（PRD 3.3）：站点列表、出入口数量、地图轨迹、站点增删与排序。
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
    var newStationName by remember { mutableStateOf("") }
    var confirmDeleteLine by remember { mutableStateOf(false) }
    var pendingDeleteStation by remember { mutableStateOf<Pair<Long, String>?>(null) }

    Scaffold(
        topBar = {
            TransNoteTopBar(
                title = state.line?.lineName ?: "地铁线路",
                onBack = onBack,
                actions = {
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
                    Text(
                        state.line?.let { line ->
                            val fare = if (line.fare.isNotBlank()) " · 票价 ${line.fare}" else ""
                            "首末班 ${line.firstBus.ifBlank { "--" }}~" +
                                "${line.lastBus.ifBlank { "--" }}$fare"
                        } ?: "—",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            item {
                SectionTitle("线路轨迹")
                StationTrackMap(
                    points = state.stations.mapNotNull { wrapper ->
                        val s = wrapper.station
                        if (CoordinateUtils.isValid(s.latitude, s.longitude)) {
                            MapPoint(s.latitude!!, s.longitude!!, s.name, false)
                        } else null
                    },
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    emptyHint = "为地铁站点设置地图坐标后将在此绘制轨迹"
                )
            }

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

            item { SectionTitle("地铁站点（${state.stations.size}）· 长按可拖拽排序") }

            item {
                DragReorderColumn(items = state.stations, onMove = viewModel::reorder) { index, wrapper, _ ->
                    val station = wrapper.station
                    TransNoteCard(
                        modifier = Modifier.padding(vertical = 4.dp),
                        onClick = { onOpenStation(station.id) }
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
                                Text(
                                    station.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                            if (wrapper.entrances.isNotEmpty()) {
                                Tag(
                                    "可换乘",
                                    TransNoteTheme.colors.transferTagBg,
                                    MaterialTheme.colorScheme.primary,
                                    icon = Icons.Filled.Login
                                )
                            }
                            IconButton(onClick = { pendingDeleteStation = station.id to station.name }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除站点",
                                    tint = TransNoteTheme.colors.danger
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
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
