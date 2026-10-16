package com.transnote.traffic.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.transnote.traffic.AppContainer
import com.transnote.traffic.ui.components.CameraCaptureView
import com.transnote.traffic.ui.components.EmptyState
import com.transnote.traffic.ui.components.TransNoteCard
import com.transnote.traffic.ui.components.TransNoteTopBar
import com.transnote.traffic.ui.theme.TransNoteTheme
import com.transnote.traffic.viewmodel.OcrViewModel
import com.transnote.traffic.viewmodel.containerViewModelFactory

/**
 * 拍照 / 相册 OCR 识别页（PRD 3.1 / TechDoc 5.2）：
 * CameraX 拍照或相册选图，ML Kit 离线识别站牌文字，结果反馈给录入表单。
 */
@Composable
fun OcrScanScreen(
    container: AppContainer,
    onApply: (List<String>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: OcrViewModel = viewModel(
        factory = containerViewModelFactory { OcrViewModel(it.repository) }
    )
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.recognize(context, it) } }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = { TransNoteTopBar(title = "拍照识别站牌", onBack = onBack) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (hasCameraPermission) {
                CameraCaptureView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onImageCaptured = { file ->
                        viewModel.recognize(context, android.net.Uri.fromFile(file))
                    },
                    onError = { cameraError = it }
                )
            } else {
                EmptyState(
                    title = "未获得相机权限",
                    hint = "可在系统设置中开启相机权限，或使用相册选图识别",
                    icon = Icons.Filled.PhotoLibrary,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("从相册选择站牌图片")
                }

                Spacer(Modifier.height(12.dp))

                when {
                    busy -> Text(
                        "正在本地离线识别…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    error != null -> Text(
                        "识别失败：$error",
                        style = MaterialTheme.typography.bodySmall,
                        color = TransNoteTheme.colors.danger
                    )

                    cameraError != null -> Text(
                        cameraError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = TransNoteTheme.colors.danger
                    )

                    lines.isNotEmpty() -> {
                        TransNoteCard {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "识别到 ${lines.size} 行文字",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                LazyColumn(
                                    modifier = Modifier.height(120.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    items(lines) { line ->
                                        Text(
                                            line,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = viewModel::clear,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.weight(1f)
                                    ) { Text("重新识别") }
                                    Button(
                                        onClick = { onApply(lines) },
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.weight(1f)
                                    ) { Text("应用到表单") }
                                }
                            }
                        }
                    }

                    else -> Text(
                        "对准站牌点击快门，或在相册选择图片。识别全程本地完成，图片不会上传。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TransNoteTheme.colors.hintText
                    )
                }
            }
        }
    }
}
