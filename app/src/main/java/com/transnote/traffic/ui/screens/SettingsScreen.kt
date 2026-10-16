package com.transnote.traffic.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.transnote.traffic.AppContainer
import com.transnote.traffic.ui.components.InfoRow
import com.transnote.traffic.ui.components.SectionTitle
import com.transnote.traffic.ui.components.TransNoteCard
import com.transnote.traffic.ui.components.TransNoteTopBar
import com.transnote.traffic.ui.theme.TransNoteTheme
import com.transnote.traffic.utils.ThemeMode
import com.transnote.traffic.viewmodel.SettingsViewModel
import com.transnote.traffic.viewmodel.containerViewModelFactory

/**
 * 设置页：主题三选一（PRD 3.6）+ 本地备份导出/导入（PRD 3.5）+ 数据统计。
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = containerViewModelFactory { SettingsViewModel(it.repository) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val activity = LocalContext.current as? android.app.Activity
    var pendingRestart by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(viewModel::exportBackup) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importBackup) }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
            // 导入会替换数据库文件，需要重建页面以刷新所有 ViewModel 的数据流
            if (message.startsWith("导入成功")) pendingRestart = true
        }
    }

    LaunchedEffect(pendingRestart) {
        if (pendingRestart) {
            kotlinx.coroutines.delay(1200)
            activity?.recreate()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TransNoteTopBar(title = "设置") },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionTitle("主题配色")
            TransNoteCard {
                Column(modifier = Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = themeMode == mode,
                                    onClick = { onThemeChange(mode) },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                when (mode) {
                                    ThemeMode.FOLLOW_SYSTEM -> Icons.Filled.BrightnessAuto
                                    ThemeMode.LIGHT -> Icons.Filled.LightMode
                                    ThemeMode.DARK -> Icons.Filled.DarkMode
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("本地数据备份")
            TransNoteCard {
                Column {
                    Text(
                        "导出：数据库 + 图片打包为 ZIP 保存到手机本地；导入：读取本地 ZIP 恢复全部线路、站点、图片数据。全程离线。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TransNoteTheme.colors.hintText
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { exportLauncher.launch(viewModel.suggestedBackupName()) },
                            enabled = !state.busy,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Backup, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("导出备份")
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            enabled = !state.busy,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("导入备份")
                        }
                    }
                    if (state.busy) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(20.dp).height(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("处理中…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("数据统计")
            TransNoteCard {
                Column {
                    InfoRow("公交线路", "${state.busLineCount} 条")
                    InfoRow("地铁线路", "${state.metroLineCount} 条")
                    InfoRow("地铁出入口", "${state.entranceCount} 个")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::refreshStats,
                        shape = MaterialTheme.shapes.small
                    ) { Text("刷新统计") }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("关于")
            TransNoteCard {
                Column {
                    InfoRow("应用名称", "途记交通备忘录")
                    InfoRow("包名", "com.transnote.traffic")
                    InfoRow("存储方式", "纯本地（无后端、无上传）")
                    InfoRow("隐私", "无广告、无推送、无埋点")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "所有数据保存在手机本地私有目录，卸载应用会清除数据，请定期使用本地备份功能导出 ZIP 备份包。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TransNoteTheme.colors.hintText
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
