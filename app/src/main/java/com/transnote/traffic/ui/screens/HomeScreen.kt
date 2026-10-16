package com.transnote.traffic.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.transnote.traffic.data.database.BusLineEntity
import com.transnote.traffic.data.database.CityEntity
import com.transnote.traffic.data.database.MetroLineEntity
import com.transnote.traffic.ui.components.ConfirmDialog
import com.transnote.traffic.ui.components.EmptyState
import com.transnote.traffic.ui.components.MarkerIconRow
import com.transnote.traffic.ui.components.Tag
import com.transnote.traffic.ui.components.TransNoteCard
import com.transnote.traffic.ui.components.TransNoteTopBar
import com.transnote.traffic.ui.theme.LineColors
import com.transnote.traffic.ui.theme.TransNoteTheme
import com.transnote.traffic.viewmodel.HomeTab
import com.transnote.traffic.viewmodel.HomeViewModel
import com.transnote.traffic.viewmodel.LineType
import com.transnote.traffic.viewmodel.containerViewModelFactory

/**
 * 首页：公交 / 地铁线路列表，支持收藏、置顶、分组（PRD 3.1 / 三期功能）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenSearch: () -> Unit,
    onOpenBusLine: (Long) -> Unit,
    onOpenMetroLine: (Long) -> Unit,
    onCreateLine: (LineType, Long, Long) -> Unit
) {
    val viewModel: HomeViewModel = viewModel(
        factory = containerViewModelFactory { HomeViewModel(it.repository, it.themeManager) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(HomeTab.BUS) }
    var pendingDelete by remember { mutableStateOf<Any?>(null) }
    var groupTarget by remember { mutableStateOf<Any?>(null) }
    var showNewLineDialog by remember { mutableStateOf(false) }
    var showCityDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TransNoteTopBar(
                title = "途记交通备忘录",
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewLineDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新增线路") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            CityBar(
                cityName = state.selectedCityName.ifBlank { "默认城市" },
                onClick = { showCityDialog = true }
            )
            TabRow(
                selectedTabIndex = tab.ordinal,
                containerColor = TransNoteTheme.colors.navBar,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = tab == HomeTab.BUS,
                    onClick = { tab = HomeTab.BUS },
                    text = { Text("公交线路 (${state.busLines.size})") }
                )
                Tab(
                    selected = tab == HomeTab.METRO,
                    onClick = { tab = HomeTab.METRO },
                    text = { Text("地铁线路 (${state.metroLines.size})") }
                )
            }

            FilterRow(
                favoriteOnly = state.favoriteOnly,
                onToggleFavorite = viewModel::setFavoriteOnly,
                groups = state.groups,
                selectedGroup = state.groupFilter,
                onSelectGroup = viewModel::setGroupFilter
            )

            when (tab) {
                HomeTab.BUS -> BusLineList(
                    lines = state.busLines,
                    lineMarkers = state.busLineMarkers,
                    onOpen = onOpenBusLine,
                    onEdit = { onCreateLine(LineType.BUS, it.id, it.cityId) },
                    onFavorite = viewModel::toggleBusFavorite,
                    onPin = viewModel::toggleBusPinned,
                    onGroup = { groupTarget = it },
                    onDelete = { pendingDelete = it }
                )

                HomeTab.METRO -> MetroLineList(
                    lines = state.metroLines,
                    lineMarkers = state.metroLineMarkers,
                    onOpen = onOpenMetroLine,
                    onEdit = { onCreateLine(LineType.METRO, it.id, it.cityId) },
                    onFavorite = viewModel::toggleMetroFavorite,
                    onPin = viewModel::toggleMetroPinned,
                    onGroup = { groupTarget = it },
                    onDelete = { pendingDelete = it }
                )
            }
        }
    }

    if (showNewLineDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showNewLineDialog = false }) {
            androidx.compose.material3.Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("新增线路", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "城市：${state.selectedCityName.ifBlank { "默认城市" }}｜请选择要录入的线路类型",
                        style = MaterialTheme.typography.bodySmall,
                        color = TransNoteTheme.colors.hintText
                    )
                    Spacer(Modifier.height(12.dp))
                    TransNoteCard(onClick = {
                        showNewLineDialog = false
                        onCreateLine(LineType.BUS, 0L, state.selectedCityId)
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.DirectionsBus,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("公交线路（上行 / 下行独立站点）", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TransNoteCard(onClick = {
                        showNewLineDialog = false
                        onCreateLine(LineType.METRO, 0L, state.selectedCityId)
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.DirectionsSubway,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("地铁线路（含出入口管理）", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    (pendingDelete as? BusLineEntity)?.let { line ->
        ConfirmDialog(
            title = "删除公交线路",
            text = "将同时删除「${line.lineName}」的全部站点与备注，且不可恢复。",
            confirmText = "删除",
            danger = true,
            onConfirm = { viewModel.deleteBusLine(line) },
            onDismiss = { pendingDelete = null }
        )
    }
    (pendingDelete as? MetroLineEntity)?.let { line ->
        ConfirmDialog(
            title = "删除地铁线路",
            text = "将同时删除「${line.lineName}」的全部站点与出入口，且不可恢复。",
            confirmText = "删除",
            danger = true,
            onConfirm = { viewModel.deleteMetroLine(line) },
            onDismiss = { pendingDelete = null }
        )
    }

    (groupTarget as? BusLineEntity)?.let { line ->
        GroupDialog(
            current = line.groupName,
            onConfirm = { viewModel.setBusGroup(line, it); groupTarget = null },
            onDismiss = { groupTarget = null }
        )
    }
    (groupTarget as? MetroLineEntity)?.let { line ->
        GroupDialog(
            current = line.groupName,
            onConfirm = { viewModel.setMetroGroup(line, it); groupTarget = null },
            onDismiss = { groupTarget = null }
        )
    }

    if (showCityDialog) {
        CityDialog(
            cities = state.cities,
            selectedId = state.selectedCityId,
            onSelect = { viewModel.selectCity(it); showCityDialog = false },
            onAdd = viewModel::addCity,
            onDelete = { viewModel.deleteCity(it.cityId) },
            onDismiss = { showCityDialog = false }
        )
    }
}

@Composable
private fun CityBar(cityName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.LocationCity,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "当前城市",
            style = MaterialTheme.typography.bodySmall,
            color = TransNoteTheme.colors.hintText
        )
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Surface(
            color = TransNoteTheme.colors.transferTagBg,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    cityName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "切换城市",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CityDialog(
    cities: List<CityEntity>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (CityEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<CityEntity?>(null) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("选择城市", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "不同城市的线路相互独立，切换即可查看该城市内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = TransNoteTheme.colors.hintText
                )
                Spacer(Modifier.height(8.dp))

                cities.forEach { city ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(city.cityId) }
                            .padding(vertical = 4.dp)
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = city.cityId == selectedId,
                            onClick = { onSelect(city.cityId) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            city.cityName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (cities.size > 1) {
                            IconButton(onClick = { pendingDelete = city }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除城市",
                                    tint = TransNoteTheme.colors.danger
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                com.transnote.traffic.ui.components.ThemedDivider()
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.transnote.traffic.ui.components.LabeledTextField(
                        label = "新增城市",
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = "如：上海",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (newName.isNotBlank()) {
                            onAdd(newName)
                            newName = ""
                        }
                    }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "添加城市",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("完成", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    pendingDelete?.let { city ->
        ConfirmDialog(
            title = "删除城市",
            text = "将删除「${city.cityName}」及其全部线路、站点、出入口与图片，且不可恢复。",
            confirmText = "删除",
            danger = true,
            onConfirm = { onDelete(city) },
            onDismiss = { pendingDelete = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    favoriteOnly: Boolean,
    onToggleFavorite: (Boolean) -> Unit,
    groups: List<String>,
    selectedGroup: String,
    onSelectGroup: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = favoriteOnly,
                onClick = { onToggleFavorite(!favoriteOnly) },
                label = { Text("收藏") },
                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) }
            )
        }
        item {
            FilterChip(
                selected = selectedGroup.isBlank(),
                onClick = { onSelectGroup("") },
                label = { Text("全部分组") }
            )
        }
        items(groups) { group ->
            FilterChip(
                selected = selectedGroup == group,
                onClick = { onSelectGroup(if (selectedGroup == group) "" else group) },
                label = { Text(group) },
                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun BusLineList(
    lines: List<BusLineEntity>,
    lineMarkers: Map<Long, List<String>> = emptyMap(),
    onOpen: (Long) -> Unit,
    onEdit: (BusLineEntity) -> Unit,
    onFavorite: (BusLineEntity) -> Unit,
    onPin: (BusLineEntity) -> Unit,
    onGroup: (BusLineEntity) -> Unit,
    onDelete: (BusLineEntity) -> Unit
) {
    if (lines.isEmpty()) {
        EmptyState(
            title = "还没有公交线路",
            hint = "点击右下角「新增线路」手动录入，或在录入页拍照识别站牌",
            icon = Icons.Filled.DirectionsBus
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(lines, key = { it.id }) { line ->
            Box(Modifier.animateItem()) {
                LineCard(
                    title = line.lineName,
                    subtitle = buildString {
                        if (line.firstBus.isNotBlank() || line.lastBus.isNotBlank()) {
                            append("首末班 ${line.firstBus.ifBlank { "--" }}~${line.lastBus.ifBlank { "--" }}")
                        }
                        if (line.fare.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append("票价 ${line.fare}")
                        }
                        val dest = line.upDestination.ifBlank { line.downDestination }
                        if (dest.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append("开往 $dest")
                        }
                        if (isEmpty()) append("公交线路")
                    },
                    colorHex = line.colorHex,
                    markers = lineMarkers[line.id].orEmpty(),
                    isFavorite = line.isFavorite,
                    isPinned = line.isPinned,
                    group = line.groupName,
                    isMetro = false,
                    onClick = { onOpen(line.id) },
                    onEdit = { onEdit(line) },
                    onFavorite = { onFavorite(line) },
                    onPin = { onPin(line) },
                    onGroup = { onGroup(line) },
                    onDelete = { onDelete(line) }
                )
            }
        }
    }
}

@Composable
private fun MetroLineList(
    lines: List<MetroLineEntity>,
    lineMarkers: Map<Long, List<String>> = emptyMap(),
    onOpen: (Long) -> Unit,
    onEdit: (MetroLineEntity) -> Unit,
    onFavorite: (MetroLineEntity) -> Unit,
    onPin: (MetroLineEntity) -> Unit,
    onGroup: (MetroLineEntity) -> Unit,
    onDelete: (MetroLineEntity) -> Unit
) {
    if (lines.isEmpty()) {
        EmptyState(
            title = "还没有地铁线路",
            hint = "点击右下角「新增线路」录入地铁线路与出入口",
            icon = Icons.Filled.DirectionsSubway
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(lines, key = { it.id }) { line ->
            Box(Modifier.animateItem()) {
                LineCard(
                    title = line.lineName,
                    subtitle = buildString {
                        if (line.firstBus.isNotBlank() || line.lastBus.isNotBlank()) {
                            append("首末班 ${line.firstBus.ifBlank { "--" }}~${line.lastBus.ifBlank { "--" }}")
                        }
                        if (line.fare.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append("票价 ${line.fare}")
                        }
                        if (line.destination.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append("开往 ${line.destination}")
                        }
                        if (isEmpty()) append("地铁线路")
                    },
                    colorHex = line.colorHex,
                    markers = lineMarkers[line.id].orEmpty(),
                    isFavorite = line.isFavorite,
                    isPinned = line.isPinned,
                    group = line.groupName,
                    isMetro = true,
                    onClick = { onOpen(line.id) },
                    onEdit = { onEdit(line) },
                    onFavorite = { onFavorite(line) },
                    onPin = { onPin(line) },
                    onGroup = { onGroup(line) },
                    onDelete = { onDelete(line) }
                )
            }
        }
    }
}

@Composable
private fun LineCard(
    title: String,
    subtitle: String,
    colorHex: String,
    markers: List<String> = emptyList(),
    isFavorite: Boolean,
    isPinned: Boolean,
    group: String,
    isMetro: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
    onPin: () -> Unit,
    onGroup: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val accent = LineColors.line(colorHex, MaterialTheme.colorScheme.primary)

    TransNoteCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = accent,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.width(4.dp).height(44.dp)
            ) {}
            Spacer(Modifier.width(10.dp))
            Icon(
                if (isMetro) Icons.Filled.DirectionsSubway else Icons.Filled.DirectionsBus,
                contentDescription = null,
                tint = accent
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isPinned) Tag("置顶", TransNoteTheme.colors.transferTagBg, MaterialTheme.colorScheme.primary)
                    if (isFavorite) {
                        Spacer(Modifier.width(4.dp))
                        Tag("收藏", TransNoteTheme.colors.transferTagBg, MaterialTheme.colorScheme.primary)
                    }
                    if (group.isNotBlank()) {
                        Spacer(Modifier.width(4.dp))
                        Tag(group, TransNoteTheme.colors.transferTagBg, MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TransNoteTheme.colors.hintText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (markers.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    MarkerIconRow(names = markers, accent = accent, max = 4)
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isFavorite) "取消收藏" else "收藏") },
                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                        onClick = { menuExpanded = false; onFavorite() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isPinned) "取消置顶" else "置顶") },
                        leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                        onClick = { menuExpanded = false; onPin() }
                    )
                    DropdownMenuItem(
                        text = { Text("设置分组") },
                        leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        onClick = { menuExpanded = false; onGroup() }
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = TransNoteTheme.colors.danger) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = TransNoteTheme.colors.danger
                            )
                        },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(current) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("设置分组") },
        text = {
            com.transnote.traffic.ui.components.LabeledTextField(
                label = "分组名称",
                value = value,
                onValueChange = { value = it },
                placeholder = "如：通勤、常坐、临时改线"
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(value.trim()) }) {
                Text("保存")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消", color = TransNoteTheme.colors.hintText)
            }
        }
    )
}
