package com.transnote.traffic.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.transnote.traffic.data.database.BusStationEntity
import com.transnote.traffic.data.database.MetroEntranceEntity
import com.transnote.traffic.data.database.MetroLineEntity
import com.transnote.traffic.data.database.MetroStationEntity
import com.transnote.traffic.data.database.StationMarkerEntity
import com.transnote.traffic.repository.TransNoteRepository
import com.transnote.traffic.utils.AmapApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 站点/出入口通用编辑状态 */
data class StationEditorState(
    val id: Long = 0,
    val name: String = "",
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val imagePaths: List<String> = emptyList(),
    val isMetroTransfer: Boolean = false,
    /** 站点圆点颜色（HEX；空=跟随线路颜色） */
    val colorHex: String = "",
    /** 站点标记（多选），关联 station_marker.id */
    val markerIds: List<Long> = emptyList(),
    /** 所属线路（用于从站点跳转到对应线路查看） */
    val lineId: Long = 0,
    val lineName: String = "",
    /** 所属线路颜色（HEX），用于站点颜色默认值与地图预览 */
    val lineColorHex: String = "",
    /** 所属城市中心（WGS-84），用于地图选点默认定位到当前城市 */
    val cityCenter: Pair<Double, Double>? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val message: String? = null,
    val finished: Boolean = false
)

/** 地铁出入口编辑状态 */
data class EntranceEditorState(
    val id: Long = 0,
    val stationId: Long = 0,
    val name: String = "",
    val road: String = "",
    val landmark: String = "",
    val note: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val imagePaths: List<String> = emptyList(),
    /** 所属城市中心（WGS-84），用于地图选点默认定位 */
    val cityCenter: Pair<Double, Double>? = null,
    val loading: Boolean = true,
    val message: String? = null,
    val finished: Boolean = false
)

/**
 * 公交站点详情 ViewModel（PRD 3.2）：
 * 站点名称/删除、文字备注、实景图片、地图经纬度、绑定地铁出入口换乘标记。
 */
class BusStationViewModel(
    private val repo: TransNoteRepository,
    private val stationId: Long
) : ViewModel() {

    private val _editor = MutableStateFlow(StationEditorState())
    val editor: StateFlow<StationEditorState> = _editor.asStateFlow()

    /** 可用的站点标记列表（用户可管理） */
    val markers: StateFlow<List<StationMarkerEntity>> =
        repo.observeMarkers()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前站点已绑定的地铁出入口（换乘标签数据） */
    val transfers: StateFlow<List<MetroEntranceEntity>> =
        repo.observeTransferEntrances(stationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- 换乘绑定选择器（地铁线路 → 站点 → 出入口） ----
    private val _pickerLines = MutableStateFlow<List<MetroLineEntity>>(emptyList())
    val pickerLines: StateFlow<List<MetroLineEntity>> = _pickerLines.asStateFlow()

    private val _pickerStations = MutableStateFlow<List<MetroStationEntity>>(emptyList())
    val pickerStations: StateFlow<List<MetroStationEntity>> = _pickerStations.asStateFlow()

    private val _pickerEntrances = MutableStateFlow<List<MetroEntranceEntity>>(emptyList())
    val pickerEntrances: StateFlow<List<MetroEntranceEntity>> = _pickerEntrances.asStateFlow()

    private val _selectedEntranceIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedEntranceIds: StateFlow<Set<Long>> = _selectedEntranceIds.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val station = repo.getBusStation(stationId)
        if (station == null) {
            _editor.update { it.copy(loading = false, message = "站点不存在") }
            return
        }
        val line = repo.getBusLine(station.lineId)
        _editor.value = StationEditorState(
            id = station.id,
            name = station.name,
            note = station.note,
            latitude = station.latitude,
            longitude = station.longitude,
            imagePaths = station.imagePaths,
            isMetroTransfer = station.isMetroTransfer,
            colorHex = station.colorHex,
            markerIds = station.markerIds,
            lineId = station.lineId,
            lineName = line?.lineName ?: "",
            lineColorHex = line?.colorHex.orEmpty(),
            cityCenter = line?.let { repo.getCityCenter(it.cityId) },
            loading = false
        )
        _selectedEntranceIds.value = repo.getTransferEntranceIds(stationId).toSet()
    }

    fun setName(value: String) = _editor.update { it.copy(name = value) }
    fun setNote(value: String) = _editor.update { it.copy(note = value) }
    fun setCoordinate(lat: Double, lng: Double) = _editor.update { it.copy(latitude = lat, longitude = lng) }
    fun clearCoordinate() = _editor.update { it.copy(latitude = null, longitude = null) }
    fun setColor(hex: String) = _editor.update { it.copy(colorHex = hex) }
    fun toggleMarker(id: Long) = _editor.update {
        it.copy(markerIds = if (id in it.markerIds) it.markerIds - id else it.markerIds + id)
    }
    fun consumeMessage() = _editor.update { it.copy(message = null) }

    /** 上传实景图片（拍照/相册），保存到 APP 私有目录并写库 */
    fun addImage(uri: Uri) {
        viewModelScope.launch {
            val path = repo.saveImage(uri)
            if (path == null) {
                _editor.update { it.copy(message = "图片保存失败") }
                return@launch
            }
            val newPaths = _editor.value.imagePaths + path
            persist(imagePaths = newPaths)
        }
    }

    fun removeImage(path: String) {
        viewModelScope.launch {
            repo.deleteImage(path)
            persist(imagePaths = _editor.value.imagePaths - path)
        }
    }

    private suspend fun persist(
        name: String = _editor.value.name,
        note: String = _editor.value.note,
        latitude: Double? = _editor.value.latitude,
        longitude: Double? = _editor.value.longitude,
        imagePaths: List<String> = _editor.value.imagePaths
    ) {
        val current = repo.getBusStation(stationId) ?: return
        val updated = current.copy(
            name = name.trim().ifBlank { current.name },
            note = note,
            latitude = latitude,
            longitude = longitude,
            imagePaths = imagePaths,
            colorHex = _editor.value.colorHex,
            markerIds = _editor.value.markerIds
        )
        repo.saveBusStation(updated)
        _editor.update {
            it.copy(
                name = updated.name,
                note = updated.note,
                latitude = latitude,
                longitude = longitude,
                imagePaths = imagePaths
            )
        }
    }

    fun save() {
        if (_editor.value.name.isBlank()) {
            _editor.update { it.copy(message = "站点名称不能为空") }
            return
        }
        viewModelScope.launch {
            persist()
            _editor.update { it.copy(message = "已保存") }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val station = repo.getBusStation(stationId) ?: return@launch
            repo.deleteBusStation(station)
            onDone()
        }
    }

    // ---------- 换乘绑定 ----------

    fun loadPickerLines() {
        viewModelScope.launch { _pickerLines.value = repo.getMetroLines() }
    }

    fun loadPickerStations(lineId: Long) {
        viewModelScope.launch {
            _pickerStations.value = repo.getMetroStations(lineId)
            _pickerEntrances.value = emptyList()
        }
    }

    fun loadPickerEntrances(stationId: Long) {
        viewModelScope.launch { _pickerEntrances.value = repo.getEntrances(stationId) }
    }

    fun toggleEntranceSelection(entranceId: Long) {
        _selectedEntranceIds.update { current ->
            if (entranceId in current) current - entranceId else current + entranceId
        }
    }

    /** 应用绑定：写入换乘关系并更新【地铁换乘】标签状态 */
    fun applyTransfers() {
        viewModelScope.launch {
            val ids = _selectedEntranceIds.value.toList()
            repo.bindMetroEntrances(stationId, ids)
            _editor.update { it.copy(isMetroTransfer = ids.isNotEmpty(), message = "接驳标记已更新") }
        }
    }

    /** 快速解除全部接驳 */
    fun clearTransfers() {
        viewModelScope.launch {
            repo.bindMetroEntrances(stationId, emptyList())
            _selectedEntranceIds.value = emptySet()
            _editor.update { it.copy(isMetroTransfer = false, message = "已取消接驳标记") }
        }
    }
}

/**
 * 地铁站点详情 ViewModel（PRD 3.3）：站点信息编辑 + 多个出入口新增/编辑/删除。
 */
class MetroStationViewModel(
    private val repo: TransNoteRepository,
    private val stationId: Long
) : ViewModel() {

    private val _editor = MutableStateFlow(StationEditorState())
    val editor: StateFlow<StationEditorState> = _editor.asStateFlow()

    /** 可用的站点标记列表（用户可管理） */
    val markers: StateFlow<List<StationMarkerEntity>> =
        repo.observeMarkers()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val entrances: StateFlow<List<MetroEntranceEntity>> =
        repo.observeEntrances(stationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val station = repo.getMetroStation(stationId)
        if (station == null) {
            _editor.update { it.copy(loading = false, message = "站点不存在") }
            return
        }
        val line = repo.getMetroLine(station.lineId)
        _editor.value = StationEditorState(
            id = station.id,
            name = station.name,
            note = station.note,
            latitude = station.latitude,
            longitude = station.longitude,
            imagePaths = station.imagePaths,
            colorHex = station.colorHex,
            markerIds = station.markerIds,
            lineId = station.lineId,
            lineName = line?.lineName ?: "",
            lineColorHex = line?.colorHex.orEmpty(),
            cityCenter = line?.let { repo.getCityCenter(it.cityId) },
            loading = false
        )
    }

    fun setName(value: String) = _editor.update { it.copy(name = value) }
    fun setNote(value: String) = _editor.update { it.copy(note = value) }
    fun setCoordinate(lat: Double, lng: Double) = _editor.update { it.copy(latitude = lat, longitude = lng) }
    fun setColor(hex: String) = _editor.update { it.copy(colorHex = hex) }
    fun toggleMarker(id: Long) = _editor.update {
        it.copy(markerIds = if (id in it.markerIds) it.markerIds - id else it.markerIds + id)
    }
    fun consumeMessage() = _editor.update { it.copy(message = null) }

    fun addImage(uri: Uri) {
        viewModelScope.launch {
            val path = repo.saveImage(uri) ?: run {
                _editor.update { it.copy(message = "图片保存失败") }
                return@launch
            }
            persist(imagePaths = _editor.value.imagePaths + path)
        }
    }

    fun removeImage(path: String) {
        viewModelScope.launch {
            repo.deleteImage(path)
            persist(imagePaths = _editor.value.imagePaths - path)
        }
    }

    private suspend fun persist(imagePaths: List<String> = _editor.value.imagePaths) {
        val current = repo.getMetroStation(stationId) ?: return
        val updated = current.copy(
            name = _editor.value.name.trim().ifBlank { current.name },
            note = _editor.value.note,
            latitude = _editor.value.latitude,
            longitude = _editor.value.longitude,
            imagePaths = imagePaths,
            colorHex = _editor.value.colorHex,
            markerIds = _editor.value.markerIds
        )
        repo.saveMetroStation(updated)
        _editor.update {
            it.copy(
                name = updated.name,
                note = updated.note,
                imagePaths = imagePaths
            )
        }
    }

    fun save() {
        if (_editor.value.name.isBlank()) {
            _editor.update { it.copy(message = "站点名称不能为空") }
            return
        }
        viewModelScope.launch {
            persist()
            _editor.update { it.copy(message = "已保存") }
        }
    }

    /** 新增出入口（A口、1号口等） */
    fun addEntrance(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repo.saveEntrance(MetroEntranceEntity(metroStationId = stationId, name = name.trim()))
        }
    }

    /** 从高德 POI（地铁出入口 150501）自动获取并新增该站的出入口（按名称去重，并补充道路 / 坐标） */
    fun importExitsFromAmap(key: String) {
        if (key.isBlank()) {
            _editor.update { it.copy(message = "请先在「设置 → 高德地图 API」填写 Key") }
            return
        }
        viewModelScope.launch {
            val s = _editor.value
            _editor.update { it.copy(message = "正在从高德获取出入口…") }
            val city = repo.getMetroLine(s.lineId)?.let { repo.getCityName(it.cityId) }.orEmpty()
            val exits = AmapApi.searchMetroExits(
                key, s.name, s.latitude ?: 0.0, s.longitude ?: 0.0, city
            )
            if (exits.isEmpty()) {
                _editor.update { it.copy(message = "未从高德获取到出入口，可手动添加") }
                return@launch
            }
            val existing = repo.getEntrances(stationId)
            val byName = existing.associateBy { it.name }
            var added = 0
            var filled = 0
            exits.forEach { exit ->
                val match = byName[exit.name]
                if (match == null) {
                    repo.saveEntrance(
                        MetroEntranceEntity(
                            metroStationId = stationId,
                            name = exit.name,
                            road = exit.road,
                            landmark = exit.landmark,
                            latitude = exit.latitude,
                            longitude = exit.longitude
                        )
                    )
                    added++
                } else {
                    val patched = match.copy(
                        road = exit.road.ifBlank { match.road },
                        landmark = match.landmark.ifBlank { exit.landmark },
                        latitude = match.latitude ?: exit.latitude,
                        longitude = match.longitude ?: exit.longitude
                    )
                    if (patched != match) {
                        repo.saveEntrance(patched)
                        filled++
                    }
                }
            }
            _editor.update {
                it.copy(
                    message = when {
                        added == 0 && filled == 0 -> "出入口已是最新"
                        filled == 0 -> "已添加 $added 个出入口"
                        added == 0 -> "已补充 $filled 个出入口信息"
                        else -> "已添加 $added 个、补充 $filled 个出入口信息"
                    }
                )
            }
        }
    }

    fun deleteEntrance(entrance: MetroEntranceEntity) {
        viewModelScope.launch { repo.deleteEntrance(entrance) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val station = repo.getMetroStation(stationId) ?: return@launch
            repo.deleteMetroStation(station)
            onDone()
        }
    }
}

/**
 * 地铁出入口编辑 ViewModel（PRD 3.3.3）：
 * 出口名称、对应道路、地标信息、通行备注、图片、地图定位。
 */
class EntranceEditViewModel(
    private val repo: TransNoteRepository,
    private val stationId: Long,
    private val entranceId: Long
) : ViewModel() {

    private val _editor = MutableStateFlow(EntranceEditorState(stationId = stationId))
    val editor: StateFlow<EntranceEditorState> = _editor.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        if (entranceId <= 0L) {
            _editor.update { it.copy(loading = false) }
            return
        }
        val entrance = repo.getEntrance(entranceId)
        if (entrance == null) {
            _editor.update { it.copy(loading = false, finished = true) }
            return
        }
        val line = repo.getMetroStation(entrance.metroStationId)?.let { station ->
            repo.getMetroLine(station.lineId)
        }
        _editor.value = EntranceEditorState(
            id = entrance.id,
            stationId = entrance.metroStationId,
            name = entrance.name,
            road = entrance.road,
            landmark = entrance.landmark,
            note = entrance.note,
            latitude = entrance.latitude,
            longitude = entrance.longitude,
            imagePaths = entrance.imagePaths,
            cityCenter = line?.let { repo.getCityCenter(it.cityId) },
            loading = false
        )
    }

    fun setName(value: String) = _editor.update { it.copy(name = value) }
    fun setRoad(value: String) = _editor.update { it.copy(road = value) }
    fun setLandmark(value: String) = _editor.update { it.copy(landmark = value) }
    fun setNote(value: String) = _editor.update { it.copy(note = value) }
    fun setCoordinate(lat: Double, lng: Double) = _editor.update { it.copy(latitude = lat, longitude = lng) }
    fun consumeMessage() = _editor.update { it.copy(message = null) }

    fun addImage(uri: Uri) {
        viewModelScope.launch {
            val path = repo.saveImage(uri) ?: run {
                _editor.update { it.copy(message = "图片保存失败") }
                return@launch
            }
            _editor.update { it.copy(imagePaths = it.imagePaths + path) }
        }
    }

    fun removeImage(path: String) {
        viewModelScope.launch {
            repo.deleteImage(path)
            _editor.update { it.copy(imagePaths = it.imagePaths - path) }
        }
    }

    fun save() {
        val s = _editor.value
        if (s.name.isBlank()) {
            _editor.update { it.copy(message = "请填写出口名称") }
            return
        }
        viewModelScope.launch {
            repo.saveEntrance(
                MetroEntranceEntity(
                    id = s.id,
                    metroStationId = s.stationId,
                    name = s.name.trim(),
                    road = s.road.trim(),
                    landmark = s.landmark.trim(),
                    note = s.note.trim(),
                    imagePaths = s.imagePaths,
                    latitude = s.latitude,
                    longitude = s.longitude
                )
            )
            _editor.update { it.copy(finished = true) }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val entrance = repo.getEntrance(entranceId) ?: return@launch
            repo.deleteEntrance(entrance)
            onDone()
        }
    }
}
