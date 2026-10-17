package com.transnote.traffic.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.transnote.traffic.ui.theme.TransNoteTheme
import com.transnote.traffic.utils.AppUpdater
import com.transnote.traffic.utils.UpdateManager
import com.transnote.traffic.utils.UpdateState

/**
 * 应用内更新弹窗：发现新版 → 下载（进度）→ 签名校验 → 调起系统安装。
 *
 * 全流程在软件内完成；仅「安装未知应用」授权与最后的安装确认由系统界面承接，
 * 授权返回后自动继续安装。
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    updateManager: UpdateManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val info = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.Ready -> state.info
        is UpdateState.Failed -> state.info
        else -> null
    }

    // 从「安装未知应用」授权页返回后，已授权则自动继续安装
    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val ready = state as? UpdateState.Ready
        if (ready != null && AppUpdater.canInstall(context)) {
            updateManager.install(context, ready.apkFile)
        }
    }

    fun startInstall() {
        val ready = state as? UpdateState.Ready ?: return
        if (AppUpdater.canInstall(context)) {
            updateManager.install(context, ready.apkFile)
        } else {
            installLauncher.launch(AppUpdater.installPermissionIntent(context))
        }
    }

    val title = when {
        state is UpdateState.Downloading -> "正在下载 v${info?.versionName.orEmpty()}"
        state is UpdateState.Ready -> "新版本 v${info?.versionName.orEmpty()} 已就绪"
        info != null -> "发现新版本 v${info.versionName}"
        state is UpdateState.Failed -> "检查更新失败"
        else -> "应用更新"
    }
    val dismissLabel = if (state is UpdateState.Failed && state.info == null) "关闭"
    else "稍后再说"

    AlertDialog(
        onDismissRequest = { if (state !is UpdateState.Downloading) onDismiss() },
        shape = MaterialTheme.shapes.large,
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (state) {
                    is UpdateState.Available -> AvailableContent(state)
                    is UpdateState.Downloading -> DownloadingContent(state)
                    is UpdateState.Ready -> ReadyContent(state, context)
                    is UpdateState.Failed -> FailedContent(state)
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(20.dp).height(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "正在检查更新…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TransNoteTheme.colors.hintText
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> Button(
                    onClick = { updateManager.download() },
                    shape = MaterialTheme.shapes.small
                ) { Text("立即更新") }

                is UpdateState.Downloading -> TextButton(
                    onClick = { updateManager.cancelDownload() }
                ) { Text("取消下载") }

                is UpdateState.Ready -> Button(
                    onClick = { startInstall() },
                    shape = MaterialTheme.shapes.small
                ) { Text("立即安装") }

                is UpdateState.Failed -> Button(
                    onClick = {
                        if (state.info != null) updateManager.download()
                        else updateManager.check()
                    },
                    shape = MaterialTheme.shapes.small
                ) { Text("重试") }

                else -> Button(enabled = false, onClick = {}) { Text("检查中…") }
            }
        },
        dismissButton = {
            if (state is UpdateState.Available ||
                state is UpdateState.Ready ||
                state is UpdateState.Failed
            ) {
                TextButton(onClick = onDismiss) { Text(dismissLabel) }
            }
        }
    )
}

/** 发现新版：版本信息 + 发布说明 */
@Composable
private fun AvailableContent(state: UpdateState.Available) {
    val info = state.info
    val hint = TransNoteTheme.colors.hintText
    val preferredSize = AppUpdater
        .selectApkAssets(info.versionName, info.assets)
        .firstOrNull()?.size ?: 0L

    Text(
        buildString {
            append(info.releaseName)
            if (preferredSize > 0) {
                append(" · 安装包约 ")
                append(AppUpdater.formatBytes(preferredSize))
            }
            if (info.publishedAt.isNotEmpty()) {
                append(" · 发布于 ")
                append(AppUpdater.formatPublishDate(info.publishedAt))
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = hint
    )
    Spacer(Modifier.height(12.dp))
    Text("更新内容", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        info.releaseNotes.ifBlank { "（该版本未提供更新说明）" },
        style = MaterialTheme.typography.bodySmall
    )
}

/** 下载中：进度条 + 已下载 / 总量 */
@Composable
private fun DownloadingContent(state: UpdateState.Downloading) {
    val hint = TransNoteTheme.colors.hintText
    if (state.percent in 0..100) {
        LinearProgressIndicator(
            progress = { state.percent / 100f },
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(12.dp))
    val detail = buildString {
        append(AppUpdater.formatBytes(state.downloaded))
        if (state.total > 0) {
            append(" / ")
            append(AppUpdater.formatBytes(state.total))
        }
        if (state.percent >= 0) {
            append("（")
            append(state.percent)
            append("%）")
        } else {
            append("，正在下载…")
        }
    }
    Text(detail, style = MaterialTheme.typography.bodySmall, color = hint)
}

/** 已就绪：可安装（必要时引导授权） */
@Composable
private fun ReadyContent(state: UpdateState.Ready, context: android.content.Context) {
    val hint = TransNoteTheme.colors.hintText
    Text(
        "安装包已下载并通过签名校验，点击「立即安装」完成更新。",
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(8.dp))
    Text(
        if (AppUpdater.canInstall(context)) {
            "系统会弹出安装确认框，确认后应用将自动升级并重启。"
        } else {
            "首次更新需在系统设置中允许「途记交通备忘录」安装未知应用，授权返回后会自动继续安装。"
        },
        style = MaterialTheme.typography.bodySmall,
        color = hint
    )
}

/** 失败：错误原因 */
@Composable
private fun FailedContent(state: UpdateState.Failed) {
    val danger = TransNoteTheme.colors.danger
    Text(state.message, style = MaterialTheme.typography.bodySmall, color = danger)
}
