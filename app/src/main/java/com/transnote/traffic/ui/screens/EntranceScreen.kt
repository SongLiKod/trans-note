package com.transnote.traffic.ui.screens

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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.transnote.traffic.AppContainer
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
import com.transnote.traffic.viewmodel.EntranceEditViewModel
import com.transnote.traffic.viewmodel.containerViewModelFactory

/**
 * 地铁出入口详情 / 编辑页（PRD 3.3.3）：
 * 出口名称、对应道路、地标信息、通行备注、图片、地图定位，全部独立编辑。
 */
@Composable
fun EntranceScreen(
    container: AppContainer,
    stationId: Long,
    entranceId: Long,
    onBack: () -> Unit
) {
    val viewModel: EntranceEditViewModel = viewModel(
        factory = containerViewModelFactory {
            EntranceEditViewModel(it.repository, stationId, entranceId)
        }
    )
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showMapPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val imagePicker = rememberImagePicker { uri -> viewModel.addImage(uri) }

    LaunchedEffect(editor.finished) {
        if (editor.finished) onBack()
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
                title = if (entranceId > 0) "出入口详情" else "新增出入口",
                onBack = onBack,
                actions = {
                    if (entranceId > 0) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除出入口",
                                tint = TransNoteTheme.colors.danger
                            )
                        }
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
                ) { Text("保存出入口") }
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
                label = "出口名称",
                value = editor.name,
                onValueChange = viewModel::setName,
                placeholder = "如：A口 / 1号口"
            )
            Spacer(Modifier.height(12.dp))
            LabeledTextField(
                label = "对应道路",
                value = editor.road,
                onValueChange = viewModel::setRoad,
                placeholder = "如：人民路东侧"
            )
            Spacer(Modifier.height(12.dp))
            LabeledTextField(
                label = "地标信息",
                value = editor.landmark,
                onValueChange = viewModel::setLandmark,
                placeholder = "如：市图书馆、万达广场"
            )
            Spacer(Modifier.height(12.dp))
            LabeledTextField(
                label = "通行备注",
                value = editor.note,
                onValueChange = viewModel::setNote,
                singleLine = false,
                minLines = 3,
                placeholder = "如：仅楼梯、有无障碍电梯"
            )

            if (editor.district.isNotBlank() || editor.nearby.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                SectionTitle("高德信息")
                TransNoteCard {
                    Column {
                        if (editor.district.isNotBlank()) {
                            Text(
                                "所在行政区：${editor.district}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (editor.nearby.isNotBlank()) {
                            if (editor.district.isNotBlank()) Spacer(Modifier.height(4.dp))
                            Text(
                                "周边道路：${editor.nearby}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("出口图片")
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
            SectionTitle("地图定位")
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
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showMapPicker) {
        MapPickerDialog(
            initialLatitude = editor.latitude,
            initialLongitude = editor.longitude,
            onConfirm = { lat, lng -> viewModel.setCoordinate(lat, lng) },
            onDismiss = { showMapPicker = false },
            defaultCenter = editor.cityCenter
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除出入口",
            text = "确定删除「${editor.name}」？",
            confirmText = "删除",
            danger = true,
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false }
        )
    }
}
