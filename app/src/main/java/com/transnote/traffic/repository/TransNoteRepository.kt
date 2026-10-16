package com.transnote.traffic.repository

import android.net.Uri
import com.transnote.traffic.data.database.AppDatabase
import com.transnote.traffic.data.database.BusLineEntity
import com.transnote.traffic.data.database.BusStationEntranceCrossRef
import com.transnote.traffic.data.database.BusStationEntity
import com.transnote.traffic.data.database.BusStationWithTransfers
import com.transnote.traffic.data.database.CityEntity
import com.transnote.traffic.data.database.MetroEntranceDetail
import com.transnote.traffic.data.database.MetroEntranceEntity
import com.transnote.traffic.data.database.MetroLineEntity
import com.transnote.traffic.data.database.MetroStationEntity
import com.transnote.traffic.data.database.MetroStationWithEntrances
import com.transnote.traffic.data.file.BackupManager
import com.transnote.traffic.data.file.BackupManager.BackupResult
import com.transnote.traffic.data.file.ImageStorage
import com.transnote.traffic.utils.SearchMatcher
import com.transnote.traffic.utils.SearchResult
import com.transnote.traffic.utils.SearchResultType
import kotlinx.coroutines.flow.Flow

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

    // ================= 城市 =================

    fun observeCities(): Flow<List<CityEntity>> = db.cityDao().observeAll()

    suspend fun getCities(): List<CityEntity> = db.cityDao().getAllOnce()

    suspend fun addCity(name: String): Long = db.cityDao().insert(CityEntity(cityName = name))

    /** 确保存在默认城市，返回其 id */
    suspend fun ensureDefaultCity(): Long {
        val cities = db.cityDao().getAllOnce()
        return cities.firstOrNull()?.cityId ?: db.cityDao().insert(CityEntity(cityName = "默认城市"))
    }

    // ================= 公交线路 =================

    fun observeBusLines(): Flow<List<BusLineEntity>> = db.busLineDao().observeAll()

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

    suspend fun saveBusStation(station: BusStationEntity): Long =
        if (station.id == 0L) db.busStationDao().insert(station) else {
            db.busStationDao().update(station); station.id
        }

    /** 批量新增站点（OCR 结果一次导入，自动递增 orderIndex） */
    suspend fun addBusStations(lineId: Long, direction: Int, names: List<String>): Int {
        val start = db.busStationDao().getByLineAndDirection(lineId, direction).size
        val list = names.mapIndexed { index, name ->
            BusStationEntity(
                lineId = lineId,
                direction = direction,
                orderIndex = start + index,
                name = name
            )
        }
        db.busStationDao().insertAll(list)
        return list.size
    }

    suspend fun deleteBusStation(station: BusStationEntity) {
        station.imagePaths.forEach { imageStorage.deleteImage(it) }
        db.busStationDao().delete(station)
    }

    suspend fun deleteBusStationById(id: Long) = db.busStationDao().deleteById(id)

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

    suspend fun saveMetroStation(station: MetroStationEntity): Long =
        if (station.id == 0L) db.metroStationDao().insert(station) else {
            db.metroStationDao().update(station); station.id
        }

    suspend fun addMetroStations(lineId: Long, names: List<String>): Int {
        val start = db.metroStationDao().getByLine(lineId).size
        val list = names.mapIndexed { index, name ->
            MetroStationEntity(lineId = lineId, orderIndex = start + index, name = name)
        }
        db.metroStationDao().insertAll(list)
        return list.size
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

        val busLineNames = db.busLineDao().getAllOnce().associate { it.id to it.lineName }
        val metroLineNames = db.metroLineDao().getAllOnce().associate { it.id to it.lineName }
        val metroStationNames = db.metroStationDao().getAllOnce().associate { it.id to it.name }

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
        return parts.joinToString(" · ").ifBlank { "公交线路" }
    }

    private fun buildMetroLineSubtitle(line: MetroLineEntity): String {
        val parts = mutableListOf<String>()
        if (line.firstBus.isNotBlank() || line.lastBus.isNotBlank()) {
            parts.add("首末班 ${line.firstBus.ifBlank { "--" }}~${line.lastBus.ifBlank { "--" }}")
        }
        if (line.fare.isNotBlank()) parts.add("票价 ${line.fare}")
        return parts.joinToString(" · ").ifBlank { "地铁线路" }
    }
}
