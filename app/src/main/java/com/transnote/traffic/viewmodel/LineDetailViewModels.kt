package com.transnote.traffic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.transnote.traffic.data.database.BusLineEntity
import com.transnote.traffic.data.database.BusStationEntity
import com.transnote.traffic.data.database.BusStationWithTransfers
import com.transnote.traffic.data.database.MetroLineEntity
import com.transnote.traffic.data.database.MetroStationWithEntrances
import com.transnote.traffic.data.database.StationMarkerEntity
import com.transnote.traffic.repository.CityStation
import com.transnote.traffic.repository.TransNoteRepository
import com.transnote.traffic.utils.DragReorder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 线路地图上下文：默认城市中心 + 城市内站点（地图搜索）+ 站点标记 */
data class LineMapContext(
    val cityCenter: Pair<Double, Double>? = null,
    val searchStations: List<CityStation> = emptyList(),
    val metroStationLabels: Map<Long, String> = emptyMap(),
    val markers: List<StationMarkerEntity> = emptyList()
)

data class BusLineDetailUiState(
    val line: BusLineEntity? = null,
    val direction: Int = BusStationEntity.DIRECTION_UP,
    val stations: List<BusStationWithTransfers> = emptyList(),
    val mapContext: LineMapContext = LineMapContext()
)

data class MetroLineDetailUiState(
    val line: MetroLineEntity? = null,
    val stations: List<MetroStationWithEntrances> = emptyList(),
    val mapContext: LineMapContext = LineMapContext()
)

/**
 * 公交线路详情 ViewModel（PRD 3.2）：
 * 一键切换上行/下行，展示站点列表与地图轨迹，站点增删与拖拽排序。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BusLineDetailViewModel(
    private val repo: TransNoteRepository,
    private val lineId: Long
) : ViewModel() {

    val direction = MutableStateFlow(BusStationEntity.DIRECTION_UP)

    private val lineFlow = repo.observeBusLine(lineId)
    private val mapContext = MutableStateFlow(LineMapContext())

    val uiState: StateFlow<BusLineDetailUiState> = combine(
        lineFlow, direction, mapContext, repo.observeMarkers()
    ) { line, dir, ctx, markers ->
        BusLineDetailUiState(
            line = line,
            direction = dir,
            stations = emptyList(),
            mapContext = ctx.copy(markers = markers)
        )
    }
        .flatMapLatest { base ->
            repo.observeBusStationsWithTransfers(lineId, base.direction).map { list ->
                base.copy(stations = list)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BusLineDetailUiState())

    init {
        viewModelScope.launch {
            val line = repo.getBusLine(lineId) ?: return@launch
            mapContext.value = LineMapContext(
                cityCenter = repo.getCityCenter(line.cityId),
                searchStations = repo.getCityStations(line.cityId),
                metroStationLabels = repo.getMetroStationLabels()
            )
        }
    }

    fun setDirection(value: Int) {
        direction.value = value
    }

    fun addStation(name: String) = addStations(listOf(name))

    /**
     * 批量添加站点：单次串行写入，严格保持录入顺序。
     * 不能对每个名称各起一个协程，否则会并发读到相同的序号起点导致顺序错乱。
     */
    fun addStations(names: List<String>) {
        val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return
        val dir = direction.value
        viewModelScope.launch { repo.addBusStations(lineId, dir, cleaned) }
    }

    fun deleteStation(station: BusStationEntity) {
        viewModelScope.launch { repo.deleteBusStation(station) }
    }

    /** 拖拽排序后持久化顺序 */
    fun reorder(from: Int, to: Int) {
        val list = uiState.value.stations.map { it.station }
        val moved = DragReorder.move(list, from, to)
        viewModelScope.launch { repo.reorderBusStations(moved) }
    }

    fun toggleFavorite() {
        val line = uiState.value.line ?: return
        viewModelScope.launch { repo.setBusFavorite(line.id, !line.isFavorite) }
    }

    fun togglePinned() {
        val line = uiState.value.line ?: return
        viewModelScope.launch { repo.setBusPinned(line.id, !line.isPinned) }
    }

    fun deleteLine(onDone: () -> Unit) {
        val line = uiState.value.line ?: return
        viewModelScope.launch {
            repo.deleteBusLine(line)
            onDone()
        }
    }
}

/**
 * 地铁线路详情 ViewModel（PRD 3.3）：站点列表 + 每个站点的出入口数量，地图轨迹。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MetroLineDetailViewModel(
    private val repo: TransNoteRepository,
    private val lineId: Long
) : ViewModel() {

    private val mapContext = MutableStateFlow(LineMapContext())

    val uiState: StateFlow<MetroLineDetailUiState> = combine(
        repo.observeMetroLine(lineId),
        mapContext,
        repo.observeMarkers()
    ) { line, ctx, markers ->
        MetroLineDetailUiState(
            line = line,
            stations = emptyList(),
            mapContext = ctx.copy(markers = markers)
        )
    }
        .flatMapLatest { base ->
            repo.observeMetroStationsWithEntrances(lineId).map { base.copy(stations = it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetroLineDetailUiState())

    init {
        viewModelScope.launch {
            val line = repo.getMetroLine(lineId) ?: return@launch
            mapContext.value = LineMapContext(
                cityCenter = repo.getCityCenter(line.cityId),
                searchStations = repo.getCityStations(line.cityId)
            )
        }
    }

    fun addStation(name: String) = addStations(listOf(name))

    /** 批量添加站点：单次串行写入，严格保持录入顺序 */
    fun addStations(names: List<String>) {
        val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return
        viewModelScope.launch { repo.addMetroStations(lineId, cleaned) }
    }

    fun deleteStation(stationId: Long) {
        viewModelScope.launch {
            val station = repo.getMetroStation(stationId) ?: return@launch
            repo.deleteMetroStation(station)
        }
    }

    fun reorder(from: Int, to: Int) {
        val list = uiState.value.stations.map { it.station }
        val moved = DragReorder.move(list, from, to)
        viewModelScope.launch { repo.reorderMetroStations(moved) }
    }

    fun toggleFavorite() {
        val line = uiState.value.line ?: return
        viewModelScope.launch { repo.setMetroFavorite(line.id, !line.isFavorite) }
    }

    fun togglePinned() {
        val line = uiState.value.line ?: return
        viewModelScope.launch { repo.setMetroPinned(line.id, !line.isPinned) }
    }

    fun deleteLine(onDone: () -> Unit) {
        val line = uiState.value.line ?: return
        viewModelScope.launch {
            repo.deleteMetroLine(line)
            onDone()
        }
    }
}
