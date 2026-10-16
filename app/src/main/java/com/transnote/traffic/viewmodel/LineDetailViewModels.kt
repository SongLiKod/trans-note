package com.transnote.traffic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.transnote.traffic.data.database.BusLineEntity
import com.transnote.traffic.data.database.BusStationEntity
import com.transnote.traffic.data.database.BusStationWithTransfers
import com.transnote.traffic.data.database.MetroLineEntity
import com.transnote.traffic.data.database.MetroStationWithEntrances
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

data class BusLineDetailUiState(
    val line: BusLineEntity? = null,
    val direction: Int = BusStationEntity.DIRECTION_UP,
    val stations: List<BusStationWithTransfers> = emptyList()
)

data class MetroLineDetailUiState(
    val line: MetroLineEntity? = null,
    val stations: List<MetroStationWithEntrances> = emptyList()
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

    val uiState: StateFlow<BusLineDetailUiState> = combine(
        lineFlow, direction
    ) { line, dir -> line to dir }
        .flatMapLatest { (line, dir) ->
            repo.observeBusStationsWithTransfers(lineId, dir).map { list ->
                BusLineDetailUiState(line = line, direction = dir, stations = list)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BusLineDetailUiState())

    fun setDirection(value: Int) {
        direction.value = value
    }

    fun addStation(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val count = repo.getBusStations(lineId, direction.value).size
            repo.saveBusStation(
                BusStationEntity(
                    lineId = lineId,
                    direction = direction.value,
                    orderIndex = count,
                    name = name.trim()
                )
            )
        }
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

    val uiState: StateFlow<MetroLineDetailUiState> = kotlinx.coroutines.flow.combine(
        repo.observeMetroLine(lineId),
        repo.observeMetroStationsWithEntrances(lineId)
    ) { line, stations ->
        MetroLineDetailUiState(line = line, stations = stations)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetroLineDetailUiState())

    fun addStation(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val count = repo.getMetroStations(lineId).size
            repo.saveMetroStation(
                com.transnote.traffic.data.database.MetroStationEntity(
                    lineId = lineId,
                    orderIndex = count,
                    name = name.trim()
                )
            )
        }
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
