package com.transnote.traffic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.transnote.traffic.data.database.StationMarkerEntity
import com.transnote.traffic.repository.TransNoteRepository
import com.transnote.traffic.ui.theme.TransNoteTheme
import kotlinx.coroutines.launch

/**
 * 站点标记选择（多选）：标签式勾选，标记显示在线路的站点行上。
 * [onManage] 打开标记管理弹窗（新增 / 重命名 / 删除）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarkerSelector(
    markers: List<StationMarkerEntity>,
    selectedIds: List<Long>,
    onToggle: (Long) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("站点标记", modifier = Modifier.weight(1f))
            TextButton(onClick = onManage) { Text("管理标记") }
        }
        TransNoteCard {
            if (markers.isEmpty()) {
                Text(
                    "暂无标记，点击「管理标记」添加（默认有 地铁 / 火车站 / 飞机）",
                    style = MaterialTheme.typography.bodySmall,
                    color = TransNoteTheme.colors.hintText
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    markers.forEach { marker ->
                        FilterChip(
                            selected = marker.id in selectedIds,
                            onClick = { onToggle(marker.id) },
                            label = { Text(marker.name) }
                        )
                    }
                }
            }
        }
    }
}

/** 一行标记标签（超出 [max] 个时以 +N 省略） */
@Composable
fun MarkerTagRow(
    names: List<String>,
    accent: Color,
    modifier: Modifier = Modifier,
    max: Int = Int.MAX_VALUE
) {
    if (names.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        names.take(max).forEach { name -> MarkerTag(name = name, accent = accent) }
        if (names.size > max) {
            Text(
                "+${names.size - max}",
                style = MaterialTheme.typography.bodySmall,
                color = TransNoteTheme.colors.hintText
            )
        }
    }
}

/** 标记管理弹窗：新增、重命名、删除（删除会同步从所有站点移除） */
@Composable
fun MarkerManagerDialog(
    repository: TransNoteRepository,
    onDismiss: () -> Unit
) {
    val markers by repository.observeMarkers()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editingName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("管理站点标记") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("新增标记") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            val name = newName.trim()
                            if (name.isNotEmpty()) {
                                scope.launch { repository.addMarker(name) }
                                newName = ""
                            }
                        }
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "添加标记",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (markers.isEmpty()) {
                    Text(
                        "暂无标记",
                        style = MaterialTheme.typography.bodySmall,
                        color = TransNoteTheme.colors.hintText
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(markers, key = { it.id }) { marker ->
                            if (editingId == marker.id) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = editingName,
                                        onValueChange = { editingName = it },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        val name = editingName.trim()
                                        if (name.isNotEmpty()) {
                                            scope.launch { repository.renameMarker(marker.id, name) }
                                        }
                                        editingId = null
                                    }) {
                                        Icon(Icons.Filled.Check, contentDescription = "确定")
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        marker.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        editingId = marker.id
                                        editingName = marker.name
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "重命名")
                                    }
                                    IconButton(onClick = {
                                        scope.launch { repository.deleteMarker(marker.id) }
                                    }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "删除",
                                            tint = TransNoteTheme.colors.danger
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}
