package com.transnote.traffic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.transnote.traffic.data.database.BusLineEntity
import com.transnote.traffic.data.database.CityEntity
import com.transnote.traffic.data.database.MetroLineEntity
import com.transnote.traffic.data.database.StationMarkerEntity
import com.transnote.traffic.repository.TransNoteRepository
import com.transnote.traffic.utils.ThemeManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 首页线路分类 */
enum class HomeTab { BUS, METRO }

/** 首页状态（含城市切换、收藏 / 置顶 / 分组过滤、线路标记聚合） */
data class HomeUiState(
    val busLines: List<BusLineEntity> = emptyList(),
    val metroLines: List<MetroLineEntity> = emptyList(),
    val cities: List<CityEntity> = emptyList(),
    val selectedCityId: Long = 0L,
    val selectedCityName: String = "",
    val favoriteOnly: Boolean = false,
    val groupFilter: String = "",
    val groups: List<String> = emptyList(),
    /** 线路 id → 该线路上出现的站点标记名称（去重） */
    val busLineMarkers: Map<Long, List<String>> = emptyMap(),
    val metroLineMarkers: Map<Long, List<String>> = emptyMap()
)

/**
 * 首页 ViewModel：城市切换 + 公交 / 地铁线路列表、收藏、置顶、分组。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repo: TransNoteRepository,
    private val settings: ThemeManager
) : ViewModel() {

    private val favoriteOnly = MutableStateFlow(false)
    private val groupFilter = MutableStateFlow("")

    /** 用户主动选择的城市（0 = 尚未选择，回退到第一个城市） */
    private val cityOverride = MutableStateFlow(0L)

    private data class Filters(
        val cities: List<CityEntity>,
        val cityOverride: Long,
        val favoriteOnly: Boolean,
        val groupFilter: String
    )

    val uiState: StateFlow<HomeUiState> = combine(
        repo.observeCities(),
        cityOverride,
        favoriteOnly,
        groupFilter
    ) { cities, override, fav, group ->
        Filters(cities, override, fav, group)
    }.flatMapLatest { filters ->
        val cityId = if (filters.cities.any { it.cityId == filters.cityOverride }) filters.cityOverride
        else filters.cities.firstOrNull()?.cityId ?: 0L

        combine(
            repo.observeBusLinesByCity(cityId),
            repo.observeMetroLinesByCity(cityId),
            repo.observeMarkers(),
            repo.observeBusLineMarkerIds(),
            repo.observeMetroLineMarkerIds()
        ) { bus, metro, markers, busMarkerIds, metroMarkerIds ->
            val busFiltered = bus
                .filter { !filters.favoriteOnly || it.isFavorite }
                .filter { filters.groupFilter.isBlank() || it.groupName == filters.groupFilter }
            val metroFiltered = metro
                .filter { !filters.favoriteOnly || it.isFavorite }
                .filter { filters.groupFilter.isBlank() || it.groupName == filters.groupFilter }
            HomeUiState(
                busLines = busFiltered,
                metroLines = metroFiltered,
                cities = filters.cities,
                selectedCityId = cityId,
                selectedCityName = filters.cities.firstOrNull { it.cityId == cityId }?.cityName
                    ?: "默认城市",
                favoriteOnly = filters.favoriteOnly,
                groupFilter = filters.groupFilter,
                groups = (bus.map { it.groupName } + metro.map { it.groupName })
                    .filter { it.isNotBlank() }.distinct().sorted(),
                busLineMarkers = busMarkerIds.associate { it.lineId to markerNames(it.markerIds, markers) },
                metroLineMarkers = metroMarkerIds.associate { it.lineId to markerNames(it.markerIds, markers) }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** 站点标记名称（按标记列表顺序去重） */
    private fun markerNames(markerIds: List<Long>, markers: List<StationMarkerEntity>): List<String> =
        markers.filter { it.id in markerIds }.map { it.name }.distinct()

    init {
        viewModelScope.launch { repo.ensureDefaultCity() }
        // 恢复上次选中的城市
        viewModelScope.launch {
            val saved = settings.selectedCityId.first()
            if (saved > 0L) cityOverride.value = saved
        }
    }

    fun setFavoriteOnly(value: Boolean) {
        favoriteOnly.value = value
    }

    fun setGroupFilter(value: String) {
        groupFilter.value = value
    }

    /** 切换当前城市 */
    fun selectCity(cityId: Long) {
        cityOverride.value = cityId
        viewModelScope.launch { settings.setSelectedCityId(cityId) }
    }

    /** 新增城市并自动切换过去 */
    fun addCity(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = repo.addCity(trimmed)
            cityOverride.value = id
            settings.setSelectedCityId(id)
        }
    }

    /** 删除城市（同时删除该城市的全部线路） */
    fun deleteCity(cityId: Long) {
        viewModelScope.launch {
            repo.deleteCity(cityId)
            if (cityOverride.value == cityId) {
                cityOverride.value = 0L
                settings.setSelectedCityId(0L)
            }
        }
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
