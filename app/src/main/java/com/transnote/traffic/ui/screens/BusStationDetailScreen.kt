package com.transnote.traffic.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.transnote.traffic.AppContainer
import com.transnote.traffic.data.database.MetroEntranceEntity
import com.transnote.traffic.ui.components.ConfirmDialog
import com.transnote.traffic.ui.components.ImageGrid
import com.transnote.traffic.ui.components.LabeledTextField
import com.transnote.traffic.ui.components.MapPickerDialog
import com.transnote.traffic.ui.components.SectionTitle
import com.transnote.traffic.ui.components.Tag
import com.transnote.traffic.ui.components.TransNoteCard
import com.transnote.traffic.ui.components.TransNoteTopBar
import com.transnote.traffic.ui.components.rememberImagePicker
import com.transnote.traffic.ui.theme.TransNoteTheme
import com.transnote.traffic.utils.CoordinateUtils
import com.transnote.traffic.viewmodel.BusStationViewModel
import com.transnote.traffic.viewmodel.containerViewModelFactory

/**
 * 公交站点详情页（PRD 3.2）：
 * 站点名称/备注编辑、实景图片、地图经纬度、绑定地铁出入口并高亮【地铁换乘】。
 */
@Composable
fun BusStationDetailScreen(
    container: AppContainer,
    stationId: Long,
    onBack: () -> Unit,
    onOpenEntrance: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    val viewModel: BusStationViewModel = viewModel(
        factory = containerViewModelFactory { BusStationViewModel(it.repository, stationId) }
    )
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val transfers by viewModel.transfers.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showMapPicker by remember { mutableStateOf(false) }
    var showTransferPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val imagePicker = rememberImagePicker { uri -> viewModel.addImage(uri) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            com.transnote.traffic.utils.LocationUtils.lastKnownLocation(context)?.let { (lat, lng) ->
                viewModel.setCoordinate(lat, lng)
            }
        }
    }

    LaunchedEffect(editor.message) {
        editor.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TransNoteTopBar(
                title = "站点详情",
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
            Spacer(Modifier.height(12.dp))
            LabeledTextField(
                label = "文字备注",
                value = editor.note,
                onValueChange = viewModel::setNote,
                singleLine = false,
                minLines = 3,
                placeholder = "如：站台在路口西北侧，可换乘地铁"
            )

            Spacer(Modifier.height(20.dp))
            SectionTitle("实景图片")
            ImageGrid(
                paths = editor.imagePaths,
                onAdd = { imagePicker.launchCamera() },
                onRemove = viewModel::removeImage
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { imagePicker.launchCamera() },
                    shape = MaterialTheme.shapes.small
                ) { Text("拍照") }
                OutlinedButton(
                    onClick = { imagePicker.launchGallery() },
                    shape = MaterialTheme.shapes.small
                ) { Text("从相册选择") }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showMapPicker = true },
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("在地图上选择")
                        }
                        OutlinedButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    com.transnote.traffic.utils.LocationUtils.lastKnownLocation(context)
                                        ?.let { (lat, lng) -> viewModel.setCoordinate(lat, lng) }
                                } else {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            },
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("当前位置")
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("地铁接驳换乘", modifier = Modifier.weight(1f))
                if (editor.isMetroTransfer) {
                    Tag(
                        "已标记地铁换乘",
                        TransNoteTheme.colors.transferTagBg,
                        MaterialTheme.colorScheme.primary,
                        icon = Icons.Filled.DirectionsSubway
                    )
                }
            }
            TransNoteCard {
                Column {
                    if (transfers.isEmpty()) {
                        Text(
                            "未绑定地铁出入口。绑定后站点会高亮显示【地铁换乘】标签。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TransNoteTheme.colors.hintText
                        )
                    } else {
                        transfers.forEach { entrance ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenEntrance(entrance.metroStationId, entrance.id) }
                                    .padding(vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Filled.DirectionsSubway,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        entrance.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (entrance.road.isNotBlank() || entrance.landmark.isNotBlank()) {
                                        Text(
                                            listOf(entrance.road, entrance.landmark)
                                                .filter { it.isNotBlank() }.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TransNoteTheme.colors.hintText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Text(
                                    "查看出口",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.loadPickerLines()
                                showTransferPicker = true
                            },
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("绑定地铁出入口")
                        }
                        if (transfers.isNotEmpty()) {
                            OutlinedButton(
                                onClick = viewModel::clearTransfers,
                                shape = MaterialTheme.shapes.small
                            ) { Text("解除全部") }
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

    if (showTransferPicker) {
        TransferPickerDialog(
            viewModel = viewModel,
            onDismiss = { showTransferPicker = false }
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除站点",
            text = "确定删除站点「${editor.name}」？",
            confirmText = "删除",
            danger = true,
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false }
        )
    }
}

/**
 * 地铁出入口选择器：地铁线路 → 站点 → 出入口（多选），用于公交站点接驳绑定。
 */
@Composable
private fun TransferPickerDialog(
    viewModel: BusStationViewModel,
    onDismiss: () -> Unit
) {
    val lines by viewModel.pickerLines.collectAsStateWithLifecycle()
    val stations by viewModel.pickerStations.collectAsStateWithLifecycle()
    val entrances by viewModel.pickerEntrances.collectAsStateWithLifecycle()
    val selected by viewModel.selectedEntranceIds.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(0) }
    var lineId by remember { mutableStateOf<Long?>(null) }
    var stationId by remember { mutableStateOf<Long?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (step > 0) {
                        IconButton(onClick = {
                            when (step) {
                                1 -> step = 0
                                2 -> step = 1
                            }
                        }) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                    }
                    Text(
                        when (step) {
                            0 -> "选择地铁线路"
                            1 -> "选择地铁站点"
                            else -> "选择出入口（可多选）"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Spacer(Modifier.height(8.dp))

                when (step) {
                    0 -> {
                        if (lines.isEmpty()) {
                            Text(
                                "暂无地铁线路，请先录入地铁线路与站点。",
                                style = MaterialTheme.typography.bodySmall,
                                color = TransNoteTheme.colors.hintText
                            )
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                items(lines, key = { it.id }) { line ->
                                    Text(
                                        line.lineName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                lineId = line.id
                                                viewModel.loadPickerStations(line.id)
                                                step = 1
                                            }
                                            .padding(vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        if (stations.isEmpty()) {
                            Text(
                                "该线路暂无站点",
                                style = MaterialTheme.typography.bodySmall,
                                color = TransNoteTheme.colors.hintText
                            )
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                items(stations, key = { it.id }) { station ->
                                    Text(
                                        station.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                stationId = station.id
                                                viewModel.loadPickerEntrances(station.id)
                                                step = 2
                                            }
                                            .padding(vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        if (entrances.isEmpty()) {
                            Text(
                                "该站点暂无出入口，可在“地铁站点详情”中新增出入口。",
                                style = MaterialTheme.typography.bodySmall,
                                color = TransNoteTheme.colors.hintText
                            )
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                items(entrances, key = { it.id }) { entrance ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.toggleEntranceSelection(entrance.id) }
                                    ) {
                                        Checkbox(
                                            checked = entrance.id in selected,
                                            onCheckedChange = { viewModel.toggleEntranceSelection(entrance.id) }
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(entrance.name, style = MaterialTheme.typography.bodyLarge)
                                            if (entrance.road.isNotBlank() || entrance.landmark.isNotBlank()) {
                                                Text(
                                                    listOf(entrance.road, entrance.landmark)
                                                        .filter { it.isNotBlank() }.joinToString(" · "),
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
                }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = TransNoteTheme.colors.hintText)
                    }
                    Spacer(Modifier.weight(1f))
                    if (step == 2) {
                        TextButton(onClick = {
                            viewModel.applyTransfers()
                            onDismiss()
                        }) {
                            Text("完成（已选 ${selected.size}）")
                        }
                    }
                }
            }
        }
    }
}
