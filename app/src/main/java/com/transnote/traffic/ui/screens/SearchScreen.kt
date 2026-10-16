package com.transnote.traffic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.transnote.traffic.AppContainer
import com.transnote.traffic.ui.components.EmptyState
import com.transnote.traffic.ui.components.MarkerIconRow
import com.transnote.traffic.ui.components.Tag
import com.transnote.traffic.ui.components.TransNoteCard
import com.transnote.traffic.ui.components.TransNoteTopBar
import com.transnote.traffic.ui.theme.TransNoteTheme
import com.transnote.traffic.utils.AmapNearbyStop
import com.transnote.traffic.utils.SearchResult
import com.transnote.traffic.utils.SearchResultType
import com.transnote.traffic.utils.formatDistance
import com.transnote.traffic.viewmodel.SearchMode
import com.transnote.traffic.viewmodel.SearchViewModel
import com.transnote.traffic.viewmodel.containerViewModelFactory

/**
 * 离线检索页（PRD 3.4）：
 * 正向搜索（输入线路查看站点）、反向搜索（输入地名查询所有可达公交地铁）。
 */
@Composable
fun SearchScreen(
    container: AppContainer,
    onOpenBusLine: (Long) -> Unit,
    onOpenMetroLine: (Long) -> Unit,
    onOpenBusStation: (Long) -> Unit,
    onOpenMetroStation: (Long) -> Unit,
    onOpenEntrance: (Long, Long) -> Unit
) {
    val viewModel: SearchViewModel = viewModel(
        factory = containerViewModelFactory { SearchViewModel(it.repository, it.themeManager) }
    )
    val query by viewModel.query.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val onlineResults by viewModel.onlineResults.collectAsStateWithLifecycle()
    val onlineBusy by viewModel.onlineBusy.collectAsStateWithLifecycle()
    val onlineMessage by viewModel.onlineMessage.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TransNoteTopBar(title = "检索")
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = {
                    Text(
                        if (online) "输入地点名称（如 人民广场），查看附近公交 / 地铁站"
                        else "输入线路编号 / 站点 / 地名 / 道路 / 地标"
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearQuery) { Text("✕") }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !online,
                    onClick = { viewModel.setOnline(false) },
                    label = { Text("离线检索") }
                )
                FilterChip(
                    selected = online,
                    onClick = { viewModel.setOnline(true) },
                    label = { Text("在线·周边站") }
                )
            }

            if (!online) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { viewModel.setMode(m) },
                            label = { Text(m.label) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (online) {
                OnlineResults(
                    query = query,
                    busy = onlineBusy,
                    message = onlineMessage,
                    results = onlineResults
                )
            } else {
                when {
                    query.isBlank() -> EmptyState(
                        title = "输入关键词开始检索",
                        hint = "正向：输入线路编号（如 302路）\n反向：输入地名、道路或地标，查询所有可达线路",
                        icon = Icons.Filled.Search
                    )

                    results.isEmpty() -> EmptyState(
                        title = "未找到匹配结果",
                        hint = "换个关键词试试，检索完全在本地进行",
                        icon = Icons.Filled.Search
                    )

                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(results) { result ->
                            Box(Modifier.animateItem()) {
                                val openLine: (() -> Unit)? = when (result.type) {
                                    SearchResultType.BUS_STATION -> ({ onOpenBusLine(result.lineId) })
                                    SearchResultType.METRO_STATION -> ({ onOpenMetroLine(result.lineId) })
                                    else -> null
                                }
                                SearchResultCard(
                                    result = result,
                                    onOpenLine = openLine,
                                    onClick = {
                                        when (result.type) {
                                            SearchResultType.BUS_LINE -> onOpenBusLine(result.id)
                                            SearchResultType.METRO_LINE -> onOpenMetroLine(result.id)
                                            SearchResultType.BUS_STATION -> onOpenBusStation(result.id)
                                            SearchResultType.METRO_STATION -> onOpenMetroStation(result.id)
                                            SearchResultType.METRO_ENTRANCE ->
                                                onOpenEntrance(result.lineId, result.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 在线检索结果：某地点附近的公交站 / 地铁站 */
@Composable
private fun OnlineResults(
    query: String,
    busy: Boolean,
    message: String?,
    results: List<AmapNearbyStop>
) {
    when {
        query.isBlank() -> EmptyState(
            title = "输入地点开始在线检索",
            hint = "输入地点、道路或地标，查询其周边的公交站 / 地铁站（需联网并配置高德 Key）",
            icon = Icons.Filled.Search
        )

        busy -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        message != null -> EmptyState(
            title = message,
            hint = "换个地点试试",
            icon = Icons.Filled.Search
        )

        else -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(results) { stop ->
                OnlineStopCard(stop)
            }
        }
    }
}

@Composable
private fun OnlineStopCard(stop: AmapNearbyStop) {
    TransNoteCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                if (stop.isMetro) Icons.Filled.DirectionsSubway else Icons.Filled.DirectionsBus,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // 站名完整展示（过长自动换行，不省略）
                Text(
                    stop.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(
                        if (stop.isMetro) "地铁站" else "公交站",
                        TransNoteTheme.colors.transferTagBg,
                        MaterialTheme.colorScheme.primary
                    )
                    if (stop.distanceMeters > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatDistance(stop.distanceMeters),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                // 地址完整展示（过长自动换行，不省略）
                if (stop.address.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stop.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = TransNoteTheme.colors.hintText
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit,
    onOpenLine: (() -> Unit)? = null
) {
    val icon = when (result.type) {
        SearchResultType.BUS_LINE, SearchResultType.BUS_STATION -> Icons.Filled.DirectionsBus
        SearchResultType.METRO_LINE, SearchResultType.METRO_STATION -> Icons.Filled.DirectionsSubway
        SearchResultType.METRO_ENTRANCE -> Icons.Filled.Login
    }
    TransNoteCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        result.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (result.type == SearchResultType.BUS_STATION && result.direction >= 0) {
                        Spacer(Modifier.width(6.dp))
                        Tag(
                            if (result.direction == 0) "上行" else "下行",
                            TransNoteTheme.colors.transferTagBg,
                            MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (result.subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        result.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TransNoteTheme.colors.hintText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (result.markers.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    MarkerIconRow(
                        names = result.markers,
                        accent = MaterialTheme.colorScheme.primary,
                        max = 4
                    )
                }
                if (result.matchedField.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "命中 ${result.matchedField}：${result.matchedText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onOpenLine != null) {
                TextButton(onClick = onOpenLine) { Text("线路") }
            }
        }
    }
}
