package com.transnote.traffic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.transnote.traffic.ui.theme.TransNoteTheme
import com.transnote.traffic.utils.AmapBusLine

/**
 * 从高德更新已有（公交 / 地铁）线路：输入 / 确认线路名，选择结果后更新线路与站点。
 */
@Composable
fun AmapUpdateDialog(
    lineName: String,
    busy: Boolean,
    message: String?,
    candidates: List<AmapBusLine>,
    onSearch: (String) -> Unit,
    onApply: (AmapBusLine) -> Unit,
    onDismiss: () -> Unit
) {
    var keyword by remember { mutableStateOf(lineName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("从高德更新线路") },
        text = {
            Column {
                Text(
                    "按线路名重新获取首末班、开往方向、时刻表与站点坐标（保留原备注 / 图片），并重新识别站点周边地铁 / 火车站 / 机场。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TransNoteTheme.colors.hintText
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text("线路名称 / 编号") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { onSearch(keyword) }, enabled = !busy) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = "查询",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = TransNoteTheme.colors.hintText
                    )
                }
                if (candidates.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(candidates, key = { it.id + it.name }) { line ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onApply(line) }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(line.name, style = MaterialTheme.typography.bodyLarge)
                                val detail = buildString {
                                    val route = listOf(line.startStop, line.endStop)
                                        .filter { it.isNotBlank() }.joinToString(" → ")
                                    if (route.isNotBlank()) append(route)
                                    if (line.stations.isNotEmpty()) {
                                        if (isNotEmpty()) append(" · ")
                                        append("${line.stations.size} 站")
                                    }
                                    if (line.startTime.isNotBlank()) {
                                        if (isNotEmpty()) append(" · ")
                                        append("首末班 ${line.startTime}~${line.endTime}")
                                    }
                                    if (line.timetable.isNotBlank()) {
                                        if (isNotEmpty()) append(" · ")
                                        append("时刻表 ${line.timetable}")
                                    }
                                }
                                if (detail.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        detail,
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
