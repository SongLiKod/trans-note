package com.transnote.traffic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.transnote.traffic.data.database.BusLineEntity
import com.transnote.traffic.data.database.BusStationEntity
import com.transnote.traffic.data.database.CityEntity
import com.transnote.traffic.data.database.MetroLineEntity
import com.transnote.traffic.data.database.MetroStationEntity
import com.transnote.traffic.repository.TransNoteRepository
import com.transnote.traffic.utils.AmapApi
import com.transnote.traffic.utils.AmapBusLine
import com.transnote.traffic.utils.AmapStation
import com.transnote.traffic.utils.DragReorder
import com.transnote.traffic.utils.StationBoardParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class LineType(val label: String) { BUS("公交线路"), METRO("地铁线路") }

/**
 * 编辑中的站点草稿（id=0 表示新增）。
 * @param colorHex 站点颜色（空=跟随线路）
 * @param latitude / @param longitude 地图点位（WGS-84；空表示保存时按站名自动关联）
 * @param markerNames 站点标记名称（如 地铁 / 火车站 / 飞机）；
 *        `null` 表示不修改已有标记，非 null（含空列表）则保存时按名称覆盖写入标记。
 */
data class StationDraft(
    val id: Long = 0,
    val name: String = "",
    val colorHex: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val markerNames: List<String>? = null
)

data class LineEditUiState(
    val lineType: LineType = LineType.BUS,
    val lineName: String = "",
    /** 所属城市（可切换）*/
    val cities: List<CityEntity> = emptyList(),
    val selectedCityId: Long = 0L,
    val firstBus: String = "",
    val lastBus: String = "",
    val fare: String = "",
    /** 公交上/下行开往方向与时刻表 */
    val upDestination: String = "",
    val downDestination: String = "",
    val upTimetable: String = "",
    val downTimetable: String = "",
    /** 地铁开往方向与时刻表 */
    val destination: String = "",
    val timetable: String = "",
    val colorHex: String = "#2563EB",
    /** 线路图样式：连线颜色（空=跟随线路颜色） */
    val railColorHex: String = "",
    /** 线路图样式：连线粗细（dp） */
    val railWidthDp: Int = 4,
    /** 线路图样式：站点圆点颜色（空=跟随线路颜色） */
    val dotColorHex: String = "",
    /** 线路图样式：站点圆点大小（dp，直径） */
    val dotSizeDp: Int = 14,
    val upStations: List<StationDraft> = emptyList(),
    val downStations: List<StationDraft> = emptyList(),
    val metroStations: List<StationDraft> = emptyList(),
    val editDirection: Int = BusStationEntity.DIRECTION_UP,
    /** 高德在线导入：搜索关键词 / 结果 / 搜索中状态 / 提示信息 */
    val amapQuery: String = "",
    val amapResults: List<AmapBusLine> = emptyList(),
    val amapSearching: Boolean = false,
    val amapMessage: String? = null,
    val loading: Boolean = true,
    val saved: Boolean = false,
    val ocrApplied: Boolean = false,
    val message: String? = null
)

/**
 * 线路录入 / 编辑 ViewModel（PRD 3.1）。
 * 支持手动录入表单、拖拽排序站点、公交上下行独立站点列表、OCR 结果自动填充。
 */
class LineEditViewModel(
    private val repo: TransNoteRepository,
    private val lineType: LineType,
    private val lineId: Long,
    private val cityId: Long = 0L
) : ViewModel() {

    private val _state = MutableStateFlow(LineEditUiState(lineType = lineType))
    val state: StateFlow<LineEditUiState> = _state.asStateFlow()

    val isEditing: Boolean get() = lineId > 0

    /** 最近一次高德查询使用的 Key，用于导入后的周边交通识别 */
    private var lastAmapKey: String = ""

    init {
        viewModelScope.launch {
            val cities = repo.getCities()
            // 所属城市：编辑时沿用该线路现有城市；新增时取首页当前城市
            val existingCity = when (lineType) {
                LineType.BUS -> if (lineId > 0L) repo.getBusLine(lineId)?.cityId else null
                LineType.METRO -> if (lineId > 0L) repo.getMetroLine(lineId)?.cityId else null
            }
            val initialCity = existingCity
                ?: cityId.takeIf { it > 0L }
                ?: cities.firstOrNull()?.cityId
                ?: 0L
            _state.update { it.copy(cities = cities, selectedCityId = initialCity) }
            load()
        }
    }

    private suspend fun load() {
        if (lineId <= 0L) {
            _state.update { it.copy(loading = false) }
            return
        }
        when (lineType) {
            LineType.BUS -> {
                val line = repo.getBusLine(lineId)
                if (line != null) {
                    val up = repo.getBusStations(lineId, BusStationEntity.DIRECTION_UP)
                        .map { StationDraft(it.id, it.name, it.colorHex, it.latitude, it.longitude) }
                    val down = repo.getBusStations(lineId, BusStationEntity.DIRECTION_DOWN)
                        .map { StationDraft(it.id, it.name, it.colorHex, it.latitude, it.longitude) }
                    _state.update {
                        it.copy(
                            lineName = line.lineName,
                            firstBus = line.firstBus,
                            lastBus = line.lastBus,
                            fare = line.fare,
                            upDestination = line.upDestination,
                            downDestination = line.downDestination,
                            upTimetable = line.upTimetable,
                            downTimetable = line.downTimetable,
                            colorHex = line.colorHex.ifBlank { "#2563EB" },
                            railColorHex = line.railColorHex,
                            railWidthDp = line.railWidthDp,
                            dotColorHex = line.dotColorHex,
                            dotSizeDp = line.dotSizeDp,
                            upStations = up,
                            downStations = down,
                            loading = false
                        )
                    }
                } else {
                    _state.update { it.copy(loading = false) }
                }
            }
            LineType.METRO -> {
                val line = repo.getMetroLine(lineId)
                if (line != null) {
                    val stations = repo.getMetroStations(lineId)
                        .map { StationDraft(it.id, it.name, it.colorHex, it.latitude, it.longitude) }
                    _state.update {
                        it.copy(
                            lineName = line.lineName,
                            firstBus = line.firstBus,
                            lastBus = line.lastBus,
                            fare = line.fare,
                            destination = line.destination,
                            timetable = line.timetable,
                            colorHex = line.colorHex.ifBlank { "#2563EB" },
                            railColorHex = line.railColorHex,
                            railWidthDp = line.railWidthDp,
                            dotColorHex = line.dotColorHex,
                            dotSizeDp = line.dotSizeDp,
                            metroStations = stations,
                            loading = false
                        )
                    }
                } else {
                    _state.update { it.copy(loading = false) }
                }
            }
        }
    }

    fun setLineName(value: String) = _state.update { it.copy(lineName = value) }
    fun setCityId(id: Long) = _state.update { it.copy(selectedCityId = id) }
    fun setFirstBus(value: String) = _state.update { it.copy(firstBus = value) }
    fun setLastBus(value: String) = _state.update { it.copy(lastBus = value) }
    fun setFare(value: String) = _state.update { it.copy(fare = value) }
    fun setUpDestination(value: String) = _state.update { it.copy(upDestination = value) }
    fun setDownDestination(value: String) = _state.update { it.copy(downDestination = value) }
    fun setUpTimetable(value: String) = _state.update { it.copy(upTimetable = value) }
    fun setDownTimetable(value: String) = _state.update { it.copy(downTimetable = value) }
    fun setDestination(value: String) = _state.update { it.copy(destination = value) }
    fun setTimetable(value: String) = _state.update { it.copy(timetable = value) }
    fun setColorHex(value: String) = _state.update { it.copy(colorHex = value) }
    fun setRailColorHex(value: String) = _state.update { it.copy(railColorHex = value) }
    fun setRailWidthDp(value: Int) = _state.update { it.copy(railWidthDp = value.coerceIn(1, 12)) }
    fun setDotColorHex(value: String) = _state.update { it.copy(dotColorHex = value) }
    fun setDotSizeDp(value: Int) = _state.update { it.copy(dotSizeDp = value.coerceIn(8, 32)) }
    fun setEditDirection(direction: Int) = _state.update { it.copy(editDirection = direction) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeOcrApplied() = _state.update { it.copy(ocrApplied = false) }

    /** 当前编辑方向的站点列表 */
    private fun currentList(state: LineEditUiState): List<StationDraft> = when (state.lineType) {
        LineType.BUS -> if (state.editDirection == BusStationEntity.DIRECTION_UP) state.upStations
        else state.downStations
        LineType.METRO -> state.metroStations
    }

    private fun updateList(state: LineEditUiState, list: List<StationDraft>): LineEditUiState =
        when (state.lineType) {
            LineType.BUS -> if (state.editDirection == BusStationEntity.DIRECTION_UP)
                state.copy(upStations = list) else state.copy(downStations = list)
            LineType.METRO -> state.copy(metroStations = list)
        }

    /** 新增站点 */
    fun addStation(name: String) {
        if (name.isBlank()) return
        _state.update { s -> updateList(s, currentList(s) + StationDraft(name = name.trim())) }
    }

    /** 批量新增站点（批量编辑） */
    fun addStations(names: List<String>) {
        val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return
        _state.update { s ->
            updateList(s, currentList(s) + cleaned.map { StationDraft(name = it) })
        }
    }

    fun updateStation(index: Int, name: String) {
        _state.update { s ->
            val list = currentList(s).toMutableList()
            if (index in list.indices) list[index] = list[index].copy(name = name)
            updateList(s, list)
        }
    }

    /** 设置某个站点的颜色（空串 = 跟随线路颜色） */
    fun setStationColor(index: Int, hex: String) {
        _state.update { s ->
            val list = currentList(s).toMutableList()
            if (index in list.indices) list[index] = list[index].copy(colorHex = hex)
            updateList(s, list)
        }
    }

    /** 把指定颜色应用到整条线路的全部站点节点（公交含上行与下行） */
    fun applyStationColorToAll(hex: String) {
        _state.update { s ->
            when (s.lineType) {
                LineType.BUS -> s.copy(
                    upStations = s.upStations.map { it.copy(colorHex = hex) },
                    downStations = s.downStations.map { it.copy(colorHex = hex) }
                )
                LineType.METRO -> s.copy(
                    metroStations = s.metroStations.map { it.copy(colorHex = hex) }
                )
            }
        }
    }

    fun removeStation(index: Int) {
        _state.update { s ->
            val list = currentList(s).toMutableList()
            if (index in list.indices) list.removeAt(index)
            updateList(s, list)
        }
    }

    /** 拖拽调整站点顺序 */
    fun moveStation(from: Int, to: Int) {
        _state.update { s -> updateList(s, DragReorder.move(currentList(s), from, to)) }
    }

    /**
     * 一键复制另一方向的站点到当前编辑方向，站点顺序相反（终点 → 起点），
     * 并保留站点颜色与地图点位。用于「上行复制到下行 / 下行复制到上行」。
     */
    fun copyFromOtherDirection() {
        _state.update { s ->
            if (s.lineType != LineType.BUS) return@update s
            val toDown = s.editDirection == BusStationEntity.DIRECTION_DOWN
            val source = if (toDown) s.upStations else s.downStations
            if (source.isEmpty()) return@update s
            val copied = source.asReversed().map {
                StationDraft(
                    name = it.name,
                    colorHex = it.colorHex,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    markerNames = it.markerNames
                )
            }
            if (toDown) s.copy(downStations = copied) else s.copy(upStations = copied)
        }
    }

    // ---------- 高德在线导入线路 ----------

    fun setAmapQuery(value: String) = _state.update { it.copy(amapQuery = value) }

    fun clearAmapResults() = _state.update { it.copy(amapResults = emptyList(), amapMessage = null) }

    fun consumeAmapMessage() = _state.update { it.copy(amapMessage = null) }

    private fun selectedCityName(s: LineEditUiState): String =
        s.cities.firstOrNull { it.cityId == s.selectedCityId }?.cityName.orEmpty()

    /**
     * 按关键词向高德公交线路接口查询（需在「设置」中配置 Web 服务 Key）。
     * 结果包含线路走向与每个站点的经纬度，可直接关联到地图。
     */
    fun searchAmap(key: String) {
        lastAmapKey = key.trim()
        val s = _state.value
        val query = s.amapQuery.trim()
        if (key.isBlank()) {
            _state.update { it.copy(amapMessage = "请先在「设置 → 高德地图 API」填写 Key") }
            return
        }
        if (query.isEmpty()) {
            _state.update { it.copy(amapMessage = "请输入线路名称 / 编号") }
            return
        }
        _state.update { it.copy(amapSearching = true, amapMessage = null, amapResults = emptyList()) }
        viewModelScope.launch {
            val result = AmapApi.searchBusLines(key, query, selectedCityName(s))
            result.onSuccess { lines ->
                _state.update {
                    it.copy(
                        amapSearching = false,
                        amapResults = lines,
                        amapMessage = if (lines.isEmpty()) "未查询到线路，请检查名称或城市" else null
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(amapSearching = false, amapMessage = e.message ?: "查询失败，请检查网络或 Key")
                }
            }
        }
    }

    /**
     * 应用选中的高德线路：
     * 1) 自动填充线路名、开往方向（末站）与首末班；
     * 2) 把该方向站点（含经纬度）覆盖到当前编辑方向，自动关联地图；
     * 3) 后台识别每个站点周边的地铁 / 火车站 / 机场，自动打上站点标记。
     */
    fun applyAmapLine(line: AmapBusLine) {
        val cleanedName = line.name.substringBefore("(").substringBefore("（").trim()
            .ifBlank { line.name }
        _state.update { s ->
            val drafts = line.stations.map {
                StationDraft(name = it.name, latitude = it.latitude, longitude = it.longitude)
            }
            val withDestination = if (line.endStop.isBlank()) s else {
                if (s.lineType == LineType.BUS) {
                    if (s.editDirection == BusStationEntity.DIRECTION_UP) {
                        s.copy(upDestination = line.endStop)
                    } else {
                        s.copy(downDestination = line.endStop)
                    }
                } else {
                    s.copy(destination = line.endStop)
                }
            }
            val updated = withDestination.copy(
                lineName = cleanedName,
                firstBus = line.startTime.ifBlank { s.firstBus },
                lastBus = line.endTime.ifBlank { s.lastBus },
                amapResults = emptyList(),
                amapMessage = "已导入「$cleanedName」，共 ${drafts.size} 个站点（含地图点位）",
                message = "已导入「$cleanedName」，共 ${drafts.size} 个站点"
            )
            if (drafts.isEmpty()) updated else updateList(updated, drafts)
        }

        // 后台识别周边地铁 / 火车站 / 机场（需 Key 与站点坐标）
        if (lastAmapKey.isBlank() || line.stations.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(amapMessage = "已导入，正在识别周边地铁 / 火车站 / 机场…") }
            val detected = detectNearbyTransit(line.stations)

            val current = _state.value
            val list = currentList(current)
            if (list.size != line.stations.size) {
                _state.update { it.copy(amapMessage = "站点列表已变化，跳过周边识别") }
                return@launch
            }
            val updated = list.mapIndexed { index, draft ->
                val names = detected.getOrNull(index).orEmpty().distinct()
                if (names.isEmpty()) draft else draft.copy(markerNames = names)
            }
            val marked = updated.count { it.markerNames?.isNotEmpty() == true }
            _state.update { s ->
                if (currentList(s).size != line.stations.size) s else updateList(s, updated)
            }
            _state.update {
                it.copy(
                    amapMessage = "识别完成：$marked 个站点已关联周边交通",
                    message = if (marked > 0) "已自动标记 $marked 个站点的地铁/火车站/机场" else null
                )
            }
        }
    }

    /** 并发识别每个站点周边交通（限制并发数，避免触发高德限流） */
    private suspend fun detectNearbyTransit(
        stations: List<AmapStation>
    ): List<List<String>> = coroutineScope {
        val semaphore = Semaphore(4)
        stations.map { station ->
            async {
                semaphore.withPermit {
                    AmapApi.detectNearbyTransit(lastAmapKey, station.latitude, station.longitude)
                        .map { it.markerName }
                }
            }
        }.awaitAll()
    }

    /** OCR 识别结果自动填充（PRD 3.1：自动填充线路、站点、运营时间，支持人工修改） */
    fun applyOcr(lines: List<String>) {
        val parsed = StationBoardParser.parse(lines)
        _state.update { s ->
            var newState = s.copy(
                lineName = s.lineName.ifBlank { parsed.lineName },
                firstBus = s.firstBus.ifBlank { parsed.firstBus },
                lastBus = s.lastBus.ifBlank { parsed.lastBus },
                fare = s.fare.ifBlank { parsed.fare },
                ocrApplied = true,
                message = "识别到 ${parsed.stations.size} 个站点，请校对后保存"
            )
            if (parsed.stations.isNotEmpty()) {
                val merged = (currentList(newState) + parsed.stations.map { StationDraft(name = it) })
                    .distinctBy { it.name }
                newState = updateList(newState, merged)
            }
            newState
        }
    }

    /** 保存线路与站点（新增或更新，含站点删除与排序） */
    fun save() {
        val s = _state.value
        if (s.lineName.isBlank()) {
            _state.update { it.copy(message = "请填写线路名称") }
            return
        }
        viewModelScope.launch {
            when (s.lineType) {
                LineType.BUS -> {
                    val effectiveCityId = s.selectedCityId.takeIf { it > 0L }
                        ?: repo.ensureDefaultCity()
                    val newId = repo.saveBusLine(
                        BusLineEntity(
                            id = lineId,
                            cityId = effectiveCityId,
                            lineName = s.lineName.trim(),
                            firstBus = s.firstBus.trim(),
                            lastBus = s.lastBus.trim(),
                            fare = s.fare.trim(),
                            upDestination = s.upDestination.trim(),
                            downDestination = s.downDestination.trim(),
                            upTimetable = s.upTimetable.trim(),
                            downTimetable = s.downTimetable.trim(),
                            colorHex = s.colorHex,
                            railColorHex = s.railColorHex,
                            railWidthDp = s.railWidthDp,
                            dotColorHex = s.dotColorHex,
                            dotSizeDp = s.dotSizeDp
                        )
                    )
                    reconcileBus(newId, BusStationEntity.DIRECTION_UP, s.upStations)
                    reconcileBus(newId, BusStationEntity.DIRECTION_DOWN, s.downStations)
                }
                LineType.METRO -> {
                    val effectiveCityId = s.selectedCityId.takeIf { it > 0L }
                        ?: repo.ensureDefaultCity()
                    val newId = repo.saveMetroLine(
                        MetroLineEntity(
                            id = lineId,
                            cityId = effectiveCityId,
                            lineName = s.lineName.trim(),
                            firstBus = s.firstBus.trim(),
                            lastBus = s.lastBus.trim(),
                            fare = s.fare.trim(),
                            destination = s.destination.trim(),
                            timetable = s.timetable.trim(),
                            colorHex = s.colorHex,
                            railColorHex = s.railColorHex,
                            railWidthDp = s.railWidthDp,
                            dotColorHex = s.dotColorHex,
                            dotSizeDp = s.dotSizeDp
                        )
                    )
                    reconcileMetro(newId, s.metroStations)
                }
            }
            _state.update { it.copy(saved = true) }
        }
    }

    private suspend fun reconcileBus(lineId: Long, direction: Int, drafts: List<StationDraft>) {
        val existing = repo.getBusStations(lineId, direction)
        val existingById = existing.associateBy { it.id }
        val kept = drafts.map { it.id }.filter { it > 0L }.toSet()
        existing.filter { it.id !in kept }.forEach { repo.deleteBusStationById(it.id) }
        drafts.forEachIndexed { index, draft ->
            // 保留站点原有的备注 / 坐标 / 图片 / 标记等字段，避免线路编辑时被清空
            val base = existingById[draft.id]
                ?: BusStationEntity(lineId = lineId, direction = direction, name = draft.name)
            repo.saveBusStation(
                base.copy(
                    id = draft.id,
                    lineId = lineId,
                    direction = direction,
                    orderIndex = index,
                    name = draft.name,
                    colorHex = draft.colorHex,
                    // 草稿带坐标（如高德导入）时写入，否则保留原坐标并按站名自动关联
                    latitude = draft.latitude ?: base.latitude,
                    longitude = draft.longitude ?: base.longitude,
                    // 草稿带标记（如导入时自动识别周边交通）时覆盖，否则保留原标记
                    markerIds = draft.markerNames?.let { resolveMarkerIds(it) } ?: base.markerIds
                )
            )
        }
    }

    /** 标记名称 → 标记 id（不存在则创建），用于导入时自动打标记 */
    private suspend fun resolveMarkerIds(names: List<String>): List<Long> =
        names.mapNotNull { repo.ensureMarkerId(it).takeIf { id -> id > 0L } }.distinct()

    private suspend fun reconcileMetro(lineId: Long, drafts: List<StationDraft>) {
        val existing = repo.getMetroStations(lineId)
        val existingById = existing.associateBy { it.id }
        val kept = drafts.map { it.id }.filter { it > 0L }.toSet()
        existing.filter { it.id !in kept }.forEach { repo.deleteMetroStationById(it.id) }
        drafts.forEachIndexed { index, draft ->
            val base = existingById[draft.id]
                ?: MetroStationEntity(lineId = lineId, name = draft.name)
            repo.saveMetroStation(
                base.copy(
                    id = draft.id,
                    lineId = lineId,
                    orderIndex = index,
                    name = draft.name,
                    colorHex = draft.colorHex,
                    latitude = draft.latitude ?: base.latitude,
                    longitude = draft.longitude ?: base.longitude,
                    markerIds = draft.markerNames?.let { resolveMarkerIds(it) } ?: base.markerIds
                )
            )
        }
    }
}
