package com.transnote.traffic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.transnote.traffic.data.database.MetroEntranceEntity
import com.transnote.traffic.ui.theme.TransNoteTheme

/**
 * 出入口弹层（供站点详情 / 线路详情复用）：
 * 展示出入口列表，点击可打开详情；[onDelete] 非空时显示删除按钮；
 * [onRefresh] 非空时标题栏显示刷新按钮（获取高德最新出入口），[busy] 期间显示进度并禁用刷新，
 * [message] 用于展示刷新结果；[secondaryActions] 可追加「新增出入口」等操作。
 */
@Composable
fun MetroEntranceListDialog(
    entrances: List<MetroEntranceEntity>,
    emptyHint: String,
    onDismiss: () -> Unit,
    title: String = "出入口（${entrances.size}）",
    onOpen: ((MetroEntranceEntity) -> Unit)? = null,
    onDelete: ((MetroEntranceEntity) -> Unit)? = null,
    busy: Boolean = false,
    message: String? = null,
    onRefresh: (() -> Unit)? = null,
    secondaryActions: (@Composable RowScope.() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, modifier = Modifier.weight(1f))
                if (onRefresh != null) {
                    IconButton(onClick = onRefresh, enabled = !busy) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "获取高德最新出入口",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column {
                if (entrances.isEmpty()) {
                    Text(
                        emptyHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = TransNoteTheme.colors.hintText
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(entrances, key = { it.id }) { entrance ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (onOpen != null) {
                                            Modifier.clickable { onOpen(entrance) }
                                        } else Modifier
                                    )
                                    .padding(vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Login,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entrance.name, style = MaterialTheme.typography.bodyLarge)
                                    val sub = buildList {
                                        if (entrance.road.isNotBlank()) add("道路 ${entrance.road}")
                                        if (entrance.district.isNotBlank()) add(entrance.district)
                                        if (entrance.landmark.isNotBlank()) add(entrance.landmark)
                                        if (entrance.nearby.isNotBlank()) add("周边 ${entrance.nearby}")
                                        if (entrance.note.isNotBlank()) add(entrance.note)
                                    }.joinToString(" · ")
                                    if (sub.isNotBlank()) {
                                        Text(
                                            sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TransNoteTheme.colors.hintText,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (onDelete != null) {
                                    IconButton(onClick = { onDelete(entrance) }) {
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
                }
                if (message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (secondaryActions != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { secondaryActions() }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
