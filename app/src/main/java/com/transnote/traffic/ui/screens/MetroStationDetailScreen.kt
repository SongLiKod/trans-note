package com.transnote.traffic.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.transnote.traffic.AppContainer
import com.transnote.traffic.data.database.MetroEntranceEntity
import com.transnote.traffic.ui.components.ConfirmDialog
import com.transnote.traffic.ui.components.ImageGrid
import com.transnote.traffic.ui.components.LabeledTextField
import com.transnote.traffic.ui.components.MapPickerDialog
import com.transnote.traffic.ui.components.SectionTitle
import com.transnote.traffic.ui.components.TransNoteCard
import com.transnote.traffic.ui.components.TransNoteTopBar
import com.transnote.traffic.ui.components.rememberImagePicker
import com.transnote.traffic.ui.theme.TransNoteTheme
import com.transnote.traffic.utils.CoordinateUtils
import com.transnote.traffic.viewmodel.MetroStationViewModel
import com.transnote.traffic.viewmodel.containerViewModelFactory

/**
 * 地铁站点详情页（PRD 3.3）：
 * 站点信息编辑 + 一个站点可新增多个出入口（A口、1号口等），每个出入口独立编辑。
 */
@Composable
fun MetroStationDetailScreen(
    container: AppContainer,
    stationId: Long,
    onBack: () -> Unit,
    onOpenLine: (Long) -> Unit,
    onOpenEntrance: (Long, Long) -> Unit
) {
    val viewModel: MetroStationViewModel = viewModel(
        factory = containerViewModelFactory { MetroStationViewModel(it.repository, stationId) }
    )
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val entrances by viewModel.entrances.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showMapPicker by remember { mutableStateOf(false) }
    var showAddEntrance by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingDeleteEntrance by remember { mutableStateOf<MetroEntranceEntity?>(null) }

    val imagePicker = rememberImagePicker { uri -> viewModel.addImage(uri) }

    LaunchedEffect(editor.message) {
        editor.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TransNoteTopBar(
                title = "地铁站点详情",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除站点",
                            tint = TransNoteTheme.colors.danger
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(color = TransNoteTheme.colors.navBar) {
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = MaterialTheme.shapes.small
                ) { Text("保存") }
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
            LabeledTextField(
                label = "站点名称",
                value = editor.name,
                onValueChange = viewModel::setName
            )
            if (editor.lineId > 0L && editor.lineName.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                TransNoteCard(onClick = { onOpenLine(editor.lineId) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.DirectionsSubway,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "所属线路",
                                style = MaterialTheme.typography.bodySmall,
                                color = TransNoteTheme.colors.hintText
                            )
                            Text(
                                editor.lineName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            "查看线路 ›",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LabeledTextField(
                label = "文字备注",
                value = editor.note,
                onValueChange = viewModel::setNote,
                singleLine = false,
                minLines = 3
            )

            Spacer(Modifier.height(20.dp))
            SectionTitle("站点图片")
            ImageGrid(
                paths = editor.imagePaths,
                onAdd = { imagePicker.launchCamera() },
                onRemove = viewModel::removeImage
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { imagePicker.launchCamera() }, shape = MaterialTheme.shapes.small) {
                    Text("拍照")
                }
                OutlinedButton(onClick = { imagePicker.launchGallery() }, shape = MaterialTheme.shapes.small) {
                    Text("从相册选择")
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("地图点位")
            TransNoteCard {
                Column {
                    Text(
                        CoordinateUtils.format(editor.latitude, editor.longitude),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showMapPicker = true },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("在地图上选择")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("出入口（${entrances.size}）", modifier = Modifier.weight(1f))
                IconButton(onClick = { showAddEntrance = true }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "新增出入口",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (entrances.isEmpty()) {
                Text(
                    "暂未添加入口，点击右上角 + 新增 A口 / 1号口等出入口",
                    style = MaterialTheme.typography.bodySmall,
                    color = TransNoteTheme.colors.hintText
                )
            } else {
                entrances.forEach { entrance ->
                    TransNoteCard(
                        modifier = Modifier.padding(vertical = 4.dp),
                        onClick = { onOpenEntrance(entrance.metroStationId, entrance.id) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Login,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entrance.name, style = MaterialTheme.typography.titleMedium)
                                val sub = listOf(entrance.road, entrance.landmark, entrance.note)
                                    .filter { it.isNotBlank() }.joinToString(" · ")
                                if (sub.isNotBlank()) {
                                    Text(
                                        sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TransNoteTheme.colors.hintText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = { pendingDeleteEntrance = entrance }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除出入口",
                                    tint = TransNoteTheme.colors.danger
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showMapPicker) {
        MapPickerDialog(
            initialLatitude = editor.latitude,
            initialLongitude = editor.longitude,
            onConfirm = { lat, lng -> viewModel.setCoordinate(lat, lng) },
            onDismiss = { showMapPicker = false }
        )
    }

    if (showAddEntrance) {
        var name by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddEntrance = false },
            shape = MaterialTheme.shapes.large,
            title = { Text("新增出入口") },
            text = {
                LabeledTextField(
                    label = "出口名称",
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "如：A口 / 1号口 / C2口"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addEntrance(name.trim())
                    showAddEntrance = false
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAddEntrance = false }) {
                    Text("取消", color = TransNoteTheme.colors.hintText)
                }
            }
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除地铁站点",
            text = "确定删除站点「${editor.name}」及其全部出入口？",
            confirmText = "删除",
            danger = true,
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false }
        )
    }

    pendingDeleteEntrance?.let { entrance ->
        ConfirmDialog(
            title = "删除出入口",
            text = "确定删除「${entrance.name}」？",
            confirmText = "删除",
            danger = true,
            onConfirm = { viewModel.deleteEntrance(entrance) },
            onDismiss = { pendingDeleteEntrance = null }
        )
    }
}
