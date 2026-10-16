package com.transnote.traffic.repository

import android.net.Uri
import com.transnote.traffic.data.database.AppDatabase
import com.transnote.traffic.data.database.BusLineEntity
import com.transnote.traffic.data.database.BusStationEntranceCrossRef
import com.transnote.traffic.data.database.BusStationEntity
import com.transnote.traffic.data.database.BusStationWithTransfers
import com.transnote.traffic.data.database.CityEntity
import com.transnote.traffic.data.database.LineMarkerIds
import com.transnote.traffic.data.database.MetroEntranceDetail
import com.transnote.traffic.data.database.MetroEntranceEntity
import com.transnote.traffic.data.database.MetroLineEntity
import com.transnote.traffic.data.database.MetroStationEntity
import com.transnote.traffic.data.database.MetroStationWithEntrances
import com.transnote.traffic.data.database.StationMarkerEntity
import com.transnote.traffic.data.file.BackupManager
import com.transnote.traffic.data.file.BackupManager.BackupResult
import com.transnote.traffic.data.file.ImageStorage
import com.transnote.traffic.utils.CoordinateUtils
import com.transnote.traffic.utils.SearchMatcher
import com.transnote.traffic.utils.SearchResult
import com.transnote.traffic.utils.SearchResultType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 从高德更新线路时的站点输入（含可选标记名称） */
data class BusStationInput(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val markerNames: List<String> = emptyList()
)

/** 地铁换乘线路（用于换乘站展示两条线路的颜色，参照 docs/temp/pic1、pic2） */
data class MetroTransferLine(
    val lineId: Long,
    val lineName: String,
    val colorHex: String
)

/** 经过某站点的公交线路（用于「该站点有哪些线路经过」） */
data class PassingBusLine(
    val lineId: Long,
    val lineName: String,
    /** 0 = 上行，1 = 下行 */
    val direction: Int,
    /** 开往方向 */
    val destination: String,
    val stationId: Long
)

/** 城市内已标注坐标的站点（用于地图内搜索与默认中心定位） */
data class CityStation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val subtitle: String,
    val isMetro: Boolean,
    /** 站点标记名称（多选），用于地图气泡与搜索列表展示 */
    val markers: List<String> = emptyList()
)

/**
 * 本地仓库层（TechDoc 第 3 节）：封装 Room 数据库操作 + 文件 IO。
 * **无任何网络请求**，所有业务数据只在本机读写。
 */
class TransNoteRepository(
    private val databaseProvider: () -> AppDatabase,
    val imageStorage: ImageStorage,
    val backupManager: BackupManager
) {
    private val db: AppDatabase get() = databaseProvider()

    /**
     * 站点批量写入串行锁。
     * 批量添加若并发执行，会同时读到相同的 orderIndex 起点而产生重复序号，导致顺序错乱，
     * 因此这里统一串行化「读取最大序号 + 写入」的整个过程。
     */
    private val stationWriteMutex = Mutex()

    // ================= 城市 =================

    fun observeCities(): Flow<List<CityEntity>> = db.cityDao().observeAll()

    suspend fun getCities(): List<CityEntity> = db.cityDao().getAllOnce()

    suspend fun addCity(name: String): Long = db.cityDao().insert(CityEntity(cityName = name))

    /** 删除城市（外键 CASCADE 会一并删除该城市的线路与站点） */
    suspend fun deleteCity(id: Long) = db.cityDao().deleteById(id)

    /** 确保存在默认城市，返回其 id */
    suspend fun ensureDefaultCity(): Long {
        val cities = db.cityDao().getAllOnce()
        return cities.firstOrNull()?.cityId ?: db.cityDao().insert(CityEntity(cityName = "默认城市"))
    }

    // ================= 站点标记（多选，列表可管理） =================

    fun observeMarkers(): Flow<List<StationMarkerEntity>> = db.stationMarkerDao().observeAll()

    /** 各线路站点上出现的标记 id（用于首页线路卡片聚合展示标记） */
    fun observeBusLineMarkerIds(): Flow<List<LineMarkerIds>> =
        db.busStationDao().observeLineMarkerIds()

    fun observeMetroLineMarkerIds(): Flow<List<LineMarkerIds>> =
        db.metroStationDao().observeLineMarkerIds()

    /** 站点标记名称（按标记列表顺序去重） */
    private fun markerNamesOf(markerIds: List<Long>, ordered: List<StationMarkerEntity>): List<String> =
        ordered.filter { it.id in markerIds }.map { it.name }.distinct()

    suspend fun getMarkers(): List<StationMarkerEntity> = db.stationMarkerDao().getAllOnce()

    /** 新增标记（返回 id） */
    suspend fun addMarker(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return 0L
        val order = (db.stationMarkerDao().maxSortOrder() ?: -1) + 1
        return db.stationMarkerDao().insert(
            StationMarkerEntity(name = trimmed, sortOrder = order)
        )
    }

    /** 按名称获取标记 id，不存在则创建（用于导入时自动打标记） */
    suspend fun ensureMarkerId(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return 0L
        db.stationMarkerDao().getAllOnce().firstOrNull { it.name == trimmed }?.let { return it.id }
        return addMarker(trimmed)
    }

    /** 重命名标记（站点上引用同一 id，改名后线路展示同步更新） */
    suspend fun renameMarker(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val marker = db.stationMarkerDao().getAllOnce().firstOrNull { it.id == id } ?: return
        db.stationMarkerDao().update(marker.copy(name = trimmed))
    }

    /** 删除标记（同时从所有站点上移除该标记 id） */
    suspend fun deleteMarker(id: Long) {
        db.busStationDao().getAllOnce().forEach { station ->
            if (id in station.markerIds) {
                db.busStationDao().update(station.copy(markerIds = station.markerIds - id))
            }
        }
        db.metroStationDao().getAllOnce().forEach { station ->
            if (id in station.markerIds) {
                db.metroStationDao().update(station.copy(markerIds = station.markerIds - id))
            }
        }
        db.stationMarkerDao().getAllOnce().firstOrNull { it.id == id }?.let {
            db.stationMarkerDao().delete(it)
        }
    }

    /** 当前城市全部有坐标的站点（地图内搜索用），默认限定城市范围 */
    suspend fun getCityStations(cityId: Long): List<CityStation> {
        val allMarkers = db.stationMarkerDao().getAllOnce()
        val busLineNames = db.busLineDao().getAllOnce()
            .filter { it.cityId == cityId }
            .associate { it.id to it.lineName }
        val metroLineNames = db.metroLineDao().getAllOnce()
            .filter { it.cityId == cityId }
            .associate { it.id to it.lineName }

        val result = mutableListOf<CityStation>()
        db.busStationDao().getAllOnce()
            .filter { it.lineId in busLineNames.keys && CoordinateUtils.isValid(it.latitude, it.longitude) }
            .forEach {
                result.add(
                    CityStation(
                        name = it.name,
                        latitude = it.latitude!!,
                        longitude = it.longitude!!,
                        subtitle = "公交 · ${busLineNames[it.lineId].orEmpty()}",
                        isMetro = false,
                        markers = markerNamesOf(it.markerIds, allMarkers)
                    )
                )
            }
        db.metroStationDao().getAllOnce()
            .filter { it.lineId in metroLineNames.keys && CoordinateUtils.isValid(it.latitude, it.longitude) }
            .forEach {
                result.add(
                    CityStation(
                        name = it.name,
                        latitude = it.latitude!!,
                        longitude = it.longitude!!,
                        subtitle = "地铁 · ${metroLineNames[it.lineId].orEmpty()}",
                        isMetro = true,
                        markers = markerNamesOf(it.markerIds, allMarkers)
                    )
                )
            }
        return result
    }

    /**
     * 查找经过指定站点的所有公交线路（跨线路聚合）。
     *
     * 同站判定：坐标距离很近（≤100m），或站名归一化后相同且距离较近（≤500m），
     * 因此同一物理站点在不同线路上录入后可以互相聚合。返回结果标明上行/下行与开往方向。
     */
    suspend fun findPassingBusLines(
        latitude: Double,
        longitude: Double,
        name: String
    ): List<PassingBusLine> {
        if (!CoordinateUtils.isValid(latitude, longitude)) return emptyList()
        val target = normalizeStationName(name)
        val lineById = db.busLineDao().getAllOnce().associateBy { it.id }
        return db.busStationDao().getAllOnce()
            .filter { CoordinateUtils.isValid(it.latitude, it.longitude) }
            .filter { station ->
                val d = CoordinateUtils.distanceMeters(
                    latitude, longitude, station.latitude!!, station.longitude!!
                )
                d <= 100.0 || (normalizeStationName(station.name) == target && d <= 500.0)
            }
            .mapNotNull { station ->
                val line = lineById[station.lineId] ?: return@mapNotNull null
                val destination = if (station.direction == BusStationEntity.DIRECTION_UP) {
                    line.upDestination
                } else {
                    line.downDestination
                }
                PassingBusLine(
                    lineId = line.id,
                    lineName = line.lineName,
                    direction = station.direction,
                    destination = destination,
                    stationId = station.id
                )
            }
            .distinctBy { it.lineId to it.direction }
            .sortedWith(compareBy({ it.lineName }, { it.direction }))
    }

    /**
     * 每个地铁站点可换乘的其它地铁线路（跨线路聚合，用于换乘站显示两条线颜色）。
     * 同站判定：坐标距离很近（≤100m），或站名归一化后相同且距离较近（≤500m）。
     */
    suspend fun getMetroTransferMap(): Map<Long, List<MetroTransferLine>> {
        val lineById = db.metroLineDao().getAllOnce().associateBy { it.id }
        val stations = db.metroStationDao().getAllOnce()
            .filter { CoordinateUtils.isValid(it.latitude, it.longitude) }
        val result = mutableMapOf<Long, List<MetroTransferLine>>()
        stations.forEach { station ->
            val others = stations.filter { other ->
                other.id != station.id && other.lineId != station.lineId && run {
                    val d = CoordinateUtils.distanceMeters(
                        station.latitude!!, station.longitude!!,
                        other.latitude!!, other.longitude!!
                    )
                    d <= 100.0 || (normalizeStationName(other.name) == normalizeStationName(station.name) && d <= 500.0)
                }
            }.mapNotNull { other ->
                lineById[other.lineId]?.let {
                    MetroTransferLine(it.id, it.lineName, it.colorHex.ifBlank { "#2563EB" })
                }
            }.distinctBy { it.lineId }
            if (others.isNotEmpty()) result[station.id] = others
        }
        return result
    }

    /** 城市中心（该城市所有已标注站点坐标的平均值），无数据返回 null */
    suspend fun getCityCenter(cityId: Long): Pair<Double, Double>? {
        val stations = getCityStations(cityId)
        if (stations.isEmpty()) return null
        return stations.map { it.latitude }.average() to stations.map { it.longitude }.average()
    }

    /** 地铁站点 id → 展示文本（如「2号线 · 人民广场」），用于公交站点的关联地铁信息 */
    suspend fun getMetroStationLabels(): Map<Long, String> {
        val lineNames = db.metroLineDao().getAllOnce().associate { it.id to it.lineName }
        return db.metroStationDao().getAllOnce().associate {
            it.id to "${lineNames[it.lineId] ?: "地铁"} · ${it.name}"
        }
    }

    // ================= 公交线路 =================

    fun observeBusLines(): Flow<List<BusLineEntity>> = db.busLineDao().observeAll()

    /** 按城市筛选公交线路（首页城市切换） */
    fun observeBusLinesByCity(cityId: Long): Flow<List<BusLineEntity>> =
        db.busLineDao().observeByCity(cityId)

    fun observeBusLine(id: Long): Flow<BusLineEntity?> = db.busLineDao().observeById(id)

    suspend fun getBusLine(id: Long): BusLineEntity? = db.busLineDao().getById(id)

    suspend fun saveBusLine(line: BusLineEntity): Long =
        if (line.id == 0L) db.busLineDao().insert(line) else {
            db.busLineDao().update(line); line.id
        }

    suspend fun deleteBusLine(line: BusLineEntity) {
        // 级联删除站点与换乘关系（Room 外键 CASCADE），同时清理图片文件
        db.busStationDao().getByLine(line.id).forEach { station ->
            station.imagePaths.forEach { imageStorage.deleteImage(it) }
        }
        db.busLineDao().delete(line)
    }

    suspend fun setBusFavorite(id: Long, favorite: Boolean) = db.busLineDao().setFavorite(id, favorite)
    suspend fun setBusPinned(id: Long, pinned: Boolean) = db.busLineDao().setPinned(id, pinned)
    suspend fun setBusGroup(id: Long, group: String) = db.busLineDao().setGroup(id, group)

    // ================= 公交站点 =================

    fun observeBusStations(lineId: Long, direction: Int): Flow<List<BusStationEntity>> =
        db.busStationDao().observeByLineAndDirection(lineId, direction)

    fun observeBusStationsWithTransfers(lineId: Long, direction: Int): Flow<List<BusStationWithTransfers>> =
        db.busStationDao().observeWithTransfers(lineId, direction)

    suspend fun getBusStations(lineId: Long, direction: Int): List<BusStationEntity> =
        db.busStationDao().getByLineAndDirection(lineId, direction)

    suspend fun getBusStation(id: Long): BusStationEntity? = db.busStationDao().getById(id)

    fun observeBusStation(id: Long): Flow<BusStationEntity?> = db.busStationDao().observeById(id)

    suspend fun saveBusStation(station: BusStationEntity): Long {
        val target = if (station.id == 0L) autoLocateForBus(station) else station
        return if (target.id == 0L) db.busStationDao().insert(target) else {
            db.busStationDao().update(target); target.id
        }
    }

    // ================= 站点地图点位自动关联 =================

    /**
     * 站点名归一化：用于「同名站点」匹配。
     * 去掉空白、括号补充说明、常见后缀（站 / 口），并统一小写，
     * 使「人民广场」「人民广场站」「人民广场（1号口）」可以互相关联。
     */
    private fun normalizeStationName(raw: String): String =
        raw.trim()
            .replace(Regex("[（(].*?[)）]"), "")
            .replace(Regex("\\s+"), "")
            .removeSuffix("站")
            .removeSuffix("口")
            .lowercase()

    /**
     * 城市内「已知坐标索引」：归一化站名 → 坐标。
     * 公交站点与地铁站点互为参照，因此新增公交站可自动继承同名地铁站坐标，反之亦然。
     */
    private suspend fun knownCoordinateIndex(cityId: Long): Map<String, Pair<Double, Double>> {
        val busLineIds = db.busLineDao().getAllOnce()
            .filter { it.cityId == cityId }.map { it.id }.toSet()
        val metroLineIds = db.metroLineDao().getAllOnce()
            .filter { it.cityId == cityId }.map { it.id }.toSet()
        val result = mutableMapOf<String, Pair<Double, Double>>()
        db.busStationDao().getAllOnce()
            .filter { it.lineId in busLineIds && CoordinateUtils.isValid(it.latitude, it.longitude) }
            .forEach { result.putIfAbsent(normalizeStationName(it.name), it.latitude!! to it.longitude!!) }
        db.metroStationDao().getAllOnce()
            .filter { it.lineId in metroLineIds && CoordinateUtils.isValid(it.latitude, it.longitude) }
            .forEach { result.putIfAbsent(normalizeStationName(it.name), it.latitude!! to it.longitude!!) }
        return result
    }

    /**
     * 按站点名自动匹配城市内已有的地图点位。
     * 用于「新增站点时自动关联地图地点」，避免用户逐站手动选点。
     */
    suspend fun findStationCoordinate(cityId: Long, name: String): Pair<Double, Double>? {
        if (name.isBlank() || cityId <= 0L) return null
        return knownCoordinateIndex(cityId)[normalizeStationName(name)]
    }

    /** 新增公交站点且未指定坐标时，按站名自动关联城市内已有地图点位 */
    private suspend fun autoLocateForBus(station: BusStationEntity): BusStationEntity {
        if (CoordinateUtils.isValid(station.latitude, station.longitude)) return station
        val cityId = db.busLineDao().getById(station.lineId)?.cityId ?: return station
        val point = findStationCoordinate(cityId, station.name) ?: return station
        return station.copy(latitude = point.first, longitude = point.second)
    }

    /** 新增地铁站点且未指定坐标时，按站名自动关联城市内已有地图点位 */
    private suspend fun autoLocateForMetro(station: MetroStationEntity): MetroStationEntity {
        if (CoordinateUtils.isValid(station.latitude, station.longitude)) return station
        val cityId = db.metroLineDao().getById(station.lineId)?.cityId ?: return station
        val point = findStationCoordinate(cityId, station.name) ?: return station
        return station.copy(latitude = point.first, longitude = point.second)
    }

    /** 批量新增站点（OCR 结果一次导入，自动递增 orderIndex） */
    suspend fun addBusStations(lineId: Long, direction: Int, names: List<String>): Int =
        stationWriteMutex.withLock {
            val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }
            if (cleaned.isEmpty()) return@withLock 0
            val existing = db.busStationDao().getByLineAndDirection(lineId, direction)
            val start = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
            // 新增站点自动关联地图点位（按站名匹配城市内已有坐标）
            val index = db.busLineDao().getById(lineId)?.cityId
                ?.let { knownCoordinateIndex(it) }.orEmpty()
            val list = cleaned.mapIndexed { i, name ->
                val point = index[normalizeStationName(name)]
                BusStationEntity(
                    lineId = lineId,
                    direction = direction,
                    orderIndex = start + i,
                    name = name,
                    latitude = point?.first,
                    longitude = point?.second
                )
            }
            db.busStationDao().insertAll(list)
            list.size
        }

    suspend fun deleteBusStation(station: BusStationEntity) {
        station.imagePaths.forEach { imageStorage.deleteImage(it) }
        db.busStationDao().delete(station)
    }

    suspend fun deleteBusStationById(id: Long) = db.busStationDao().deleteById(id)

    /**
     * 用高德线路数据更新已有公交线路：
     * - 更新首末班与当前方向的开往方向；
     * - 按归一化站名对齐站点，更新坐标与标记，保留原站点的备注 / 图片 / 颜色等；
     * - 删除高德结果中已不存在的站点。
     */
    suspend fun updateBusLineFromAmap(
        lineId: Long,
        direction: Int,
        firstBus: String,
        lastBus: String,
        endStop: String,
        stations: List<BusStationInput>
    ) {
        val line = getBusLine(lineId) ?: return
        saveBusLine(
            line.copy(
                firstBus = firstBus.ifBlank { line.firstBus },
                lastBus = lastBus.ifBlank { line.lastBus },
                upDestination = if (direction == BusStationEntity.DIRECTION_UP) {
                    endStop.ifBlank { line.upDestination }
                } else line.upDestination,
                downDestination = if (direction == BusStationEntity.DIRECTION_DOWN) {
                    endStop.ifBlank { line.downDestination }
                } else line.downDestination
            )
        )
        val existing = getBusStations(lineId, direction)
        val existingByName = existing.associateBy { normalizeStationName(it.name) }
        val keptIds = mutableSetOf<Long>()
        stations.forEachIndexed { index, input ->
            val old = existingByName[normalizeStationName(input.name)]
            val base = old ?: BusStationEntity(lineId = lineId, direction = direction, name = input.name)
            val markerIds = if (input.markerNames.isNotEmpty()) {
                input.markerNames.map { ensureMarkerId(it) }.filter { it > 0L }
            } else {
                base.markerIds
            }
            val id = saveBusStation(
                base.copy(
                    id = base.id,
                    lineId = lineId,
                    direction = direction,
                    orderIndex = index,
                    name = input.name,
                    latitude = input.latitude,
                    longitude = input.longitude,
                    markerIds = markerIds
                )
            )
            keptIds.add(id)
        }
        existing.filter { it.id !in keptIds }.forEach { deleteBusStationById(it.id) }
    }

    /** 城市名称 */
    suspend fun getCityName(cityId: Long): String =
        db.cityDao().getAllOnce().firstOrNull { it.cityId == cityId }?.cityName.orEmpty()

    /** 拖拽排序：按传入顺序重写 orderIndex */
    suspend fun reorderBusStations(ordered: List<BusStationEntity>) {
        ordered.forEachIndexed { index, station ->
            db.busStationDao().updateOrder(station.id, index)
        }
    }

    /** 绑定地铁出入口（换乘标记），entranceIds 为空则取消接驳 */
    suspend fun bindMetroEntrances(busStationId: Long, entranceIds: List<Long>) {
        db.busStationDao().deleteAllTransfers(busStationId)
        entranceIds.distinct().forEach { id ->
            db.busStationDao().insertTransfer(BusStationEntranceCrossRef(busStationId, id))
        }
        val station = db.busStationDao().getById(busStationId) ?: return
        db.busStationDao().update(station.copy(isMetroTransfer = entranceIds.isNotEmpty()))
    }

    suspend fun getBusStationWithTransfers(id: Long): BusStationWithTransfers? =
        db.busStationDao().getByIdWithTransfers(id)

    suspend fun getTransferEntranceIds(busStationId: Long): List<Long> =
        db.busStationDao().getTransferEntranceIds(busStationId)

    fun observeTransferEntrances(busStationId: Long): Flow<List<MetroEntranceEntity>> =
        db.busStationDao().observeTransferEntrances(busStationId)

    suspend fun getTransferEntrances(busStationId: Long): List<MetroEntranceEntity> =
        db.busStationDao().getTransferEntrances(busStationId)

    // ================= 地铁线路 =================

    fun observeMetroLines(): Flow<List<MetroLineEntity>> = db.metroLineDao().observeAll()

    /** 按城市筛选地铁线路（首页城市切换） */
    fun observeMetroLinesByCity(cityId: Long): Flow<List<MetroLineEntity>> =
        db.metroLineDao().observeByCity(cityId)

    suspend fun getMetroLines(): List<MetroLineEntity> = db.metroLineDao().getAllOnce()

    fun observeMetroLine(id: Long): Flow<MetroLineEntity?> = db.metroLineDao().observeById(id)

    suspend fun getMetroLine(id: Long): MetroLineEntity? = db.metroLineDao().getById(id)

    suspend fun saveMetroLine(line: MetroLineEntity): Long =
        if (line.id == 0L) db.metroLineDao().insert(line) else {
            db.metroLineDao().update(line); line.id
        }

    suspend fun deleteMetroLine(line: MetroLineEntity) {
        db.metroStationDao().getByLine(line.id).forEach { station ->
            station.imagePaths.forEach { imageStorage.deleteImage(it) }
            db.metroEntranceDao().getByStation(station.id).forEach { entrance ->
                entrance.imagePaths.forEach { imageStorage.deleteImage(it) }
            }
        }
        db.metroLineDao().delete(line)
    }

    suspend fun setMetroFavorite(id: Long, favorite: Boolean) = db.metroLineDao().setFavorite(id, favorite)
    suspend fun setMetroPinned(id: Long, pinned: Boolean) = db.metroLineDao().setPinned(id, pinned)
    suspend fun setMetroGroup(id: Long, group: String) = db.metroLineDao().setGroup(id, group)

    // ================= 地铁站点 =================

    fun observeMetroStations(lineId: Long): Flow<List<MetroStationEntity>> =
        db.metroStationDao().observeByLine(lineId)

    fun observeMetroStationsWithEntrances(lineId: Long): Flow<List<MetroStationWithEntrances>> =
        db.metroStationDao().observeWithEntrances(lineId)

    suspend fun getMetroStations(lineId: Long): List<MetroStationEntity> =
        db.metroStationDao().getByLine(lineId)

    suspend fun getMetroStation(id: Long): MetroStationEntity? = db.metroStationDao().getById(id)

    fun observeMetroStation(id: Long): Flow<MetroStationEntity?> = db.metroStationDao().observeById(id)

    suspend fun saveMetroStation(station: MetroStationEntity): Long {
        val target = if (station.id == 0L) autoLocateForMetro(station) else station
        return if (target.id == 0L) db.metroStationDao().insert(target) else {
            db.metroStationDao().update(target); target.id
        }
    }

    suspend fun addMetroStations(lineId: Long, names: List<String>): Int =
        stationWriteMutex.withLock {
            val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }
            if (cleaned.isEmpty()) return@withLock 0
            val existing = db.metroStationDao().getByLine(lineId)
            val start = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
            // 新增站点自动关联地图点位（按站名匹配城市内已有坐标）
            val index = db.metroLineDao().getById(lineId)?.cityId
                ?.let { knownCoordinateIndex(it) }.orEmpty()
            val list = cleaned.mapIndexed { i, name ->
                val point = index[normalizeStationName(name)]
                MetroStationEntity(
                    lineId = lineId,
                    orderIndex = start + i,
                    name = name,
                    latitude = point?.first,
                    longitude = point?.second
                )
            }
            db.metroStationDao().insertAll(list)
            list.size
        }

    suspend fun deleteMetroStation(station: MetroStationEntity) {
        station.imagePaths.forEach { imageStorage.deleteImage(it) }
        db.metroEntranceDao().getByStation(station.id).forEach { entrance ->
            entrance.imagePaths.forEach { imageStorage.deleteImage(it) }
        }
        db.metroStationDao().delete(station)
    }

    suspend fun reorderMetroStations(ordered: List<MetroStationEntity>) {
        ordered.forEachIndexed { index, station ->
            db.metroStationDao().updateOrder(station.id, index)
        }
    }

    suspend fun deleteMetroStationById(id: Long) = db.metroStationDao().deleteById(id)

    suspend fun getMetroStationWithEntrances(id: Long): MetroStationWithEntrances? =
        db.metroStationDao().getWithEntrances(id)

    // ================= 地铁出入口 =================

    fun observeEntrances(stationId: Long): Flow<List<MetroEntranceEntity>> =
        db.metroEntranceDao().observeByStation(stationId)

    suspend fun getEntrances(stationId: Long): List<MetroEntranceEntity> =
        db.metroEntranceDao().getByStation(stationId)

    suspend fun getEntrance(id: Long): MetroEntranceEntity? = db.metroEntranceDao().getById(id)

    fun observeEntrance(id: Long): Flow<MetroEntranceEntity?> = db.metroEntranceDao().observeById(id)

    suspend fun getEntranceDetail(id: Long): MetroEntranceDetail? =
        db.metroEntranceDao().getDetailById(id)

    suspend fun saveEntrance(entrance: MetroEntranceEntity): Long =
        if (entrance.id == 0L) db.metroEntranceDao().insert(entrance) else {
            db.metroEntranceDao().update(entrance); entrance.id
        }

    suspend fun deleteEntrance(entrance: MetroEntranceEntity) {
        entrance.imagePaths.forEach { imageStorage.deleteImage(it) }
        db.metroEntranceDao().delete(entrance)
    }

    // ================= 图片 =================

    suspend fun saveImage(uri: Uri): String? = imageStorage.saveImageFromUri(uri)

    suspend fun deleteImage(path: String) {
        imageStorage.deleteImage(path)
    }

    // ================= 本地离线检索 =================

    /**
     * 正向检索：模糊匹配 BusLine、MetroLine 的线路名称/编号（TechDoc 5.5.1）。
     */
    suspend fun searchLines(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<SearchResult>()

        // 线路标记聚合：线路下所有站点标记的去重集合
        val allMarkers = db.stationMarkerDao().getAllOnce()
        val busLineMarkers = db.busStationDao().getAllOnce()
            .groupBy { it.lineId }
            .mapValues { (_, stations) -> markerNamesOf(stations.flatMap { it.markerIds }, allMarkers) }
        val metroLineMarkers = db.metroStationDao().getAllOnce()
            .groupBy { it.lineId }
            .mapValues { (_, stations) -> markerNamesOf(stations.flatMap { it.markerIds }, allMarkers) }

        db.busLineDao().getAllOnce().forEach { line ->
            val score = SearchMatcher.weightedScore(query, line.lineName, SearchMatcher.Weight.NAME)
            if (score > SearchMatcher.NO_MATCH) {
                results.add(
                    SearchResult(
                        type = SearchResultType.BUS_LINE,
                        id = line.id,
                        lineId = line.id,
                        title = line.lineName,
                        subtitle = buildBusLineSubtitle(line),
                        matchedField = "线路名称",
                        matchedText = line.lineName,
                        markers = busLineMarkers[line.id].orEmpty(),
                        score = score
                    )
                )
            }
        }

        db.metroLineDao().getAllOnce().forEach { line ->
            val score = SearchMatcher.weightedScore(query, line.lineName, SearchMatcher.Weight.NAME)
            if (score > SearchMatcher.NO_MATCH) {
                results.add(
                    SearchResult(
                        type = SearchResultType.METRO_LINE,
                        id = line.id,
                        lineId = line.id,
                        title = line.lineName,
                        subtitle = buildMetroLineSubtitle(line),
                        matchedField = "线路名称",
                        matchedText = line.lineName,
                        markers = metroLineMarkers[line.id].orEmpty(),
                        score = score
                    )
                )
            }
        }

        return results.sortedWith(compareByDescending<SearchResult> { it.score }.thenBy { it.title })
    }

    /**
     * 反向地点检索：遍历站点名称、站点备注、地铁出口道路/地标字段，
     * 返回所有匹配的站点和所属线路，按匹配度排序（TechDoc 5.5.2）。
     */
    suspend fun searchPlaces(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<SearchResult>()

        val allMarkers = db.stationMarkerDao().getAllOnce()
        val busLineNames = db.busLineDao().getAllOnce().associate { it.id to it.lineName }
        val metroLineNames = db.metroLineDao().getAllOnce().associate { it.id to it.lineName }
        val metroStations = db.metroStationDao().getAllOnce()
        val metroStationNames = metroStations.associate { it.id to it.name }
        val metroStationMarkers = metroStations.associate { it.id to it.markerIds }

        // 1) 公交站点：站点名 + 站点备注
        db.busStationDao().getAllOnce().forEach { station ->
            val nameScore = SearchMatcher.weightedScore(query, station.name, SearchMatcher.Weight.NAME)
            val noteScore = SearchMatcher.weightedScore(query, station.note, SearchMatcher.Weight.NOTE)
            val (score, field, text) = when {
                nameScore >= noteScore && nameScore > SearchMatcher.NO_MATCH ->
                    Triple(nameScore, "站点名称", station.name)
                noteScore > SearchMatcher.NO_MATCH ->
                    Triple(noteScore, "站点备注", SearchMatcher.snippet(station.note, query))
                else -> Triple(SearchMatcher.NO_MATCH, "", "")
            }
            if (score > SearchMatcher.NO_MATCH) {
                results.add(
                    SearchResult(
                        type = SearchResultType.BUS_STATION,
                        id = station.id,
                        lineId = station.lineId,
                        title = station.name,
                        subtitle = "公交 · ${busLineNames[station.lineId] ?: "未知线路"} · " +
                            if (station.direction == BusStationEntity.DIRECTION_UP) "上行" else "下行",
                        matchedField = field,
                        matchedText = text,
                        direction = station.direction,
                        markers = markerNamesOf(station.markerIds, allMarkers),
                        score = score + if (station.isMetroTransfer) 3 else 0
                    )
                )
            }
        }

        // 2) 地铁站点：站点名 + 站点备注
        db.metroStationDao().getAllOnce().forEach { station ->
            val nameScore = SearchMatcher.weightedScore(query, station.name, SearchMatcher.Weight.NAME)
            val noteScore = SearchMatcher.weightedScore(query, station.note, SearchMatcher.Weight.NOTE)
            val score = maxOf(nameScore, noteScore)
            if (score > SearchMatcher.NO_MATCH) {
                results.add(
                    SearchResult(
                        type = SearchResultType.METRO_STATION,
                        id = station.id,
                        lineId = station.lineId,
                        title = station.name,
                        subtitle = "地铁 · ${metroLineNames[station.lineId] ?: "未知线路"}",
                        matchedField = if (nameScore >= noteScore) "站点名称" else "站点备注",
                        matchedText = if (nameScore >= noteScore) station.name
                        else SearchMatcher.snippet(station.note, query),
                        markers = markerNamesOf(station.markerIds, allMarkers),
                        score = score + 5
                    )
                )
            }
        }

        // 3) 地铁出入口：出口名 + 对应道路 + 地标 + 备注
        db.metroEntranceDao().getAllOnce().forEach { entrance ->
            val candidates = listOf(
                Triple("出口名称", entrance.name, SearchMatcher.Weight.NAME),
                Triple("对应道路", entrance.road, SearchMatcher.Weight.ROAD),
                Triple("地标信息", entrance.landmark, SearchMatcher.Weight.LANDMARK),
                Triple("通行备注", entrance.note, SearchMatcher.Weight.NOTE)
            )
            val best = candidates
                .map { (field, value, weight) ->
                    val s = SearchMatcher.weightedScore(query, value, weight)
                    Triple(field, value, s)
                }
                .filter { it.third > SearchMatcher.NO_MATCH }
                .maxByOrNull { it.third } ?: return@forEach

            val stationName = metroStationNames[entrance.metroStationId] ?: "未知站点"
            results.add(
                SearchResult(
                    type = SearchResultType.METRO_ENTRANCE,
                    id = entrance.id,
                    lineId = entrance.metroStationId,
                    title = "${stationName} ${entrance.name}",
                    subtitle = "地铁出口 · ${entrance.road.ifBlank { "未填写道路" }}",
                    matchedField = best.first,
                    matchedText = SearchMatcher.snippet(best.second, query),
                    markers = markerNamesOf(metroStationMarkers[entrance.metroStationId].orEmpty(), allMarkers),
                    score = best.third
                )
            )
        }

        return results.sortedWith(compareByDescending<SearchResult> { it.score }.thenBy { it.title })
    }

    /** 正向 + 反向统一入口，供搜索结果页使用 */
    suspend fun searchAll(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        return (searchLines(query) + searchPlaces(query))
            .sortedWith(compareByDescending<SearchResult> { it.score }.thenBy { it.title })
    }

    // ================= 备份 =================

    /** 导出前做一次 WAL checkpoint，保证数据库文件完整 */
    private suspend fun checkpointDatabase() {
        runCatching {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        }
    }

    suspend fun exportBackup(destUri: Uri): BackupResult {
        checkpointDatabase()
        return backupManager.exportBackup(destUri)
    }

    /** 导入：关闭数据库连接 → 覆盖文件 → 下次访问自动重开 */
    suspend fun importBackup(zipUri: Uri): BackupResult {
        AppDatabase.close()
        return backupManager.importBackup(zipUri)
    }

    // ================= 统计 =================

    suspend fun totalCounts(): Triple<Int, Int, Int> {
        val busLines = db.busLineDao().count()
        val metroLines = db.metroLineDao().count()
        val entrances = db.metroEntranceDao().count()
        return Triple(busLines, metroLines, entrances)
    }

    private fun buildBusLineSubtitle(line: BusLineEntity): String {
        val parts = mutableListOf<String>()
        if (line.firstBus.isNotBlank() || line.lastBus.isNotBlank()) {
            parts.add("首末班 ${line.firstBus.ifBlank { "--" }}~${line.lastBus.ifBlank { "--" }}")
        }
        if (line.fare.isNotBlank()) parts.add("票价 ${line.fare}")
        val dest = line.upDestination.ifBlank { line.downDestination }
        if (dest.isNotBlank()) parts.add("开往 $dest")
        return parts.joinToString(" · ").ifBlank { "公交线路" }
    }

    private fun buildMetroLineSubtitle(line: MetroLineEntity): String {
        val parts = mutableListOf<String>()
        if (line.firstBus.isNotBlank() || line.lastBus.isNotBlank()) {
            parts.add("首末班 ${line.firstBus.ifBlank { "--" }}~${line.lastBus.ifBlank { "--" }}")
        }
        if (line.fare.isNotBlank()) parts.add("票价 ${line.fare}")
        if (line.destination.isNotBlank()) parts.add("开往 ${line.destination}")
        return parts.joinToString(" · ").ifBlank { "地铁线路" }
    }
}
