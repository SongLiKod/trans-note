package com.transnote.traffic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.transnote.traffic.data.database.BusLineEntity
import com.transnote.traffic.data.database.CityEntity
import com.transnote.traffic.data.database.MetroLineEntity
import com.transnote.traffic.repository.TransNoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 首页线路分类 */
enum class HomeTab { BUS, METRO }

/** 分组管理（三期：线路分组） */
data class HomeUiState(
    val busLines: List<BusLineEntity> = emptyList(),
    val metroLines: List<MetroLineEntity> = emptyList(),
    val cities: List<CityEntity> = emptyList(),
    val favoriteOnly: Boolean = false,
    val groupFilter: String = "",
    val groups: List<String> = emptyList()
)

/**
 * 首页 ViewModel：公交 / 地铁线路列表、收藏、置顶、分组。
 */
class HomeViewModel(private val repo: TransNoteRepository) : ViewModel() {

    private val favoriteOnly = MutableStateFlow(false)
    private val groupFilter = MutableStateFlow("")

    val uiState: StateFlow<HomeUiState> = combine(
        repo.observeBusLines(),
        repo.observeMetroLines(),
        repo.observeCities(),
        combine(favoriteOnly, groupFilter) { fav, group -> fav to group }
    ) { bus, metro, cities, filter ->
        val (fav, group) = filter
        val busFiltered = bus
            .filter { !fav || it.isFavorite }
            .filter { group.isBlank() || it.groupName == group }
        val metroFiltered = metro
            .filter { !fav || it.isFavorite }
            .filter { group.isBlank() || it.groupName == group }
        HomeUiState(
            busLines = busFiltered,
            metroLines = metroFiltered,
            cities = cities,
            favoriteOnly = fav,
            groupFilter = group,
            groups = (bus.map { it.groupName } + metro.map { it.groupName })
                .filter { it.isNotBlank() }.distinct().sorted()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { repo.ensureDefaultCity() }
    }

    fun setFavoriteOnly(value: Boolean) {
        favoriteOnly.value = value
    }

    fun setGroupFilter(value: String) {
        groupFilter.value = value
    }

    fun addCity(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.addCity(name.trim()) }
    }

    fun deleteBusLine(line: BusLineEntity) {
        viewModelScope.launch { repo.deleteBusLine(line) }
    }

    fun deleteMetroLine(line: MetroLineEntity) {
        viewModelScope.launch { repo.deleteMetroLine(line) }
    }

    fun toggleBusFavorite(line: BusLineEntity) {
        viewModelScope.launch { repo.setBusFavorite(line.id, !line.isFavorite) }
    }

    fun toggleBusPinned(line: BusLineEntity) {
        viewModelScope.launch { repo.setBusPinned(line.id, !line.isPinned) }
    }

    fun setBusGroup(line: BusLineEntity, group: String) {
        viewModelScope.launch { repo.setBusGroup(line.id, group) }
    }

    fun toggleMetroFavorite(line: MetroLineEntity) {
        viewModelScope.launch { repo.setMetroFavorite(line.id, !line.isFavorite) }
    }

    fun toggleMetroPinned(line: MetroLineEntity) {
        viewModelScope.launch { repo.setMetroPinned(line.id, !line.isPinned) }
    }

    fun setMetroGroup(line: MetroLineEntity, group: String) {
        viewModelScope.launch { repo.setMetroGroup(line.id, group) }
    }
}
