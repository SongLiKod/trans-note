package com.transnote.traffic.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(city: CityEntity): Long

    @Update
    suspend fun update(city: CityEntity)

    @Delete
    suspend fun delete(city: CityEntity)

    @Query("SELECT * FROM city ORDER BY cityName ASC")
    fun observeAll(): Flow<List<CityEntity>>

    @Query("SELECT * FROM city ORDER BY cityName ASC")
    suspend fun getAllOnce(): List<CityEntity>

    @Query("SELECT * FROM city WHERE cityId = :id")
    suspend fun getById(id: Long): CityEntity?

    @Query("SELECT * FROM city WHERE cityName = :name LIMIT 1")
    suspend fun getByName(name: String): CityEntity?

    @Query("DELETE FROM city WHERE cityId = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BusLineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(line: BusLineEntity): Long

    @Update
    suspend fun update(line: BusLineEntity)

    @Delete
    suspend fun delete(line: BusLineEntity)

    @Query("DELETE FROM bus_line WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM bus_line ORDER BY isPinned DESC, isFavorite DESC, createdAt DESC")
    fun observeAll(): Flow<List<BusLineEntity>>

    @Query("SELECT * FROM bus_line WHERE cityId = :cityId ORDER BY isPinned DESC, isFavorite DESC, createdAt DESC")
    fun observeByCity(cityId: Long): Flow<List<BusLineEntity>>

    @Query("SELECT * FROM bus_line ORDER BY isPinned DESC, isFavorite DESC, createdAt DESC")
    suspend fun getAllOnce(): List<BusLineEntity>

    @Query("SELECT * FROM bus_line WHERE id = :id")
    fun observeById(id: Long): Flow<BusLineEntity?>

    @Query("SELECT * FROM bus_line WHERE id = :id")
    suspend fun getById(id: Long): BusLineEntity?

    @Query("SELECT * FROM bus_line WHERE lineName LIKE '%' || :keyword || '%' ORDER BY isPinned DESC, createdAt DESC")
    fun searchByName(keyword: String): Flow<List<BusLineEntity>>

    @Query("UPDATE bus_line SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE bus_line SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE bus_line SET groupName = :group WHERE id = :id")
    suspend fun setGroup(id: Long, group: String)

    @Query("SELECT COUNT(*) FROM bus_line")
    suspend fun count(): Int
}

@Dao
interface BusStationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(station: BusStationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<BusStationEntity>)

    @Update
    suspend fun update(station: BusStationEntity)

    @Delete
    suspend fun delete(station: BusStationEntity)

    @Query("DELETE FROM bus_station WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM bus_station WHERE lineId = :lineId AND direction = :direction ORDER BY orderIndex ASC, id ASC")
    fun observeByLineAndDirection(lineId: Long, direction: Int): Flow<List<BusStationEntity>>

    @Query("SELECT * FROM bus_station WHERE lineId = :lineId AND direction = :direction ORDER BY orderIndex ASC, id ASC")
    suspend fun getByLineAndDirection(lineId: Long, direction: Int): List<BusStationEntity>

    @Query("SELECT * FROM bus_station WHERE lineId = :lineId ORDER BY direction ASC, orderIndex ASC, id ASC")
    suspend fun getByLine(lineId: Long): List<BusStationEntity>

    @Query("SELECT * FROM bus_station ORDER BY lineId ASC, direction ASC, orderIndex ASC, id ASC")
    suspend fun getAllOnce(): List<BusStationEntity>

    @Query("SELECT * FROM bus_station WHERE id = :id")
    suspend fun getById(id: Long): BusStationEntity?

    @Query("SELECT * FROM bus_station WHERE id = :id")
    fun observeById(id: Long): Flow<BusStationEntity?>

    @Query("UPDATE bus_station SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun updateOrder(id: Long, orderIndex: Int)

    // ---------- 换乘关系 ----------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(ref: BusStationEntranceCrossRef)

    @Query("DELETE FROM bus_station_entrance WHERE busStationId = :busStationId AND metroEntranceId = :entranceId")
    suspend fun deleteTransfer(busStationId: Long, entranceId: Long)

    @Query("DELETE FROM bus_station_entrance WHERE busStationId = :busStationId")
    suspend fun deleteAllTransfers(busStationId: Long)

    @Query("SELECT metroEntranceId FROM bus_station_entrance WHERE busStationId = :busStationId")
    suspend fun getTransferEntranceIds(busStationId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM bus_station_entrance WHERE busStationId = :busStationId")
    suspend fun getTransferCount(busStationId: Long): Int

    @Query(
        "SELECT * FROM metro_entrance WHERE id IN " +
            "(SELECT metroEntranceId FROM bus_station_entrance WHERE busStationId = :busStationId) " +
            "ORDER BY name ASC"
    )
    fun observeTransferEntrances(busStationId: Long): Flow<List<MetroEntranceEntity>>

    @Query(
        "SELECT * FROM metro_entrance WHERE id IN " +
            "(SELECT metroEntranceId FROM bus_station_entrance WHERE busStationId = :busStationId) " +
            "ORDER BY name ASC"
    )
    suspend fun getTransferEntrances(busStationId: Long): List<MetroEntranceEntity>

    @Transaction
    @Query("SELECT * FROM bus_station WHERE id = :id")
    suspend fun getByIdWithTransfers(id: Long): BusStationWithTransfers?

    @Transaction
    @Query("SELECT * FROM bus_station WHERE lineId = :lineId AND direction = :direction ORDER BY orderIndex ASC, id ASC")
    fun observeWithTransfers(lineId: Long, direction: Int): Flow<List<BusStationWithTransfers>>

    @Transaction
    @Query("SELECT * FROM bus_station WHERE lineId = :lineId AND direction = :direction ORDER BY orderIndex ASC, id ASC")
    suspend fun getWithTransfers(lineId: Long, direction: Int): List<BusStationWithTransfers>
}

@Dao
interface MetroLineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(line: MetroLineEntity): Long

    @Update
    suspend fun update(line: MetroLineEntity)

    @Delete
    suspend fun delete(line: MetroLineEntity)

    @Query("DELETE FROM metro_line WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM metro_line ORDER BY isPinned DESC, isFavorite DESC, createdAt DESC")
    fun observeAll(): Flow<List<MetroLineEntity>>

    @Query("SELECT * FROM metro_line WHERE cityId = :cityId ORDER BY isPinned DESC, isFavorite DESC, createdAt DESC")
    fun observeByCity(cityId: Long): Flow<List<MetroLineEntity>>

    @Query("SELECT * FROM metro_line ORDER BY isPinned DESC, isFavorite DESC, createdAt DESC")
    suspend fun getAllOnce(): List<MetroLineEntity>

    @Query("SELECT * FROM metro_line WHERE id = :id")
    fun observeById(id: Long): Flow<MetroLineEntity?>

    @Query("SELECT * FROM metro_line WHERE id = :id")
    suspend fun getById(id: Long): MetroLineEntity?

    @Query("SELECT * FROM metro_line WHERE lineName LIKE '%' || :keyword || '%' ORDER BY isPinned DESC, createdAt DESC")
    fun searchByName(keyword: String): Flow<List<MetroLineEntity>>

    @Query("UPDATE metro_line SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE metro_line SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE metro_line SET groupName = :group WHERE id = :id")
    suspend fun setGroup(id: Long, group: String)

    @Query("SELECT COUNT(*) FROM metro_line")
    suspend fun count(): Int
}

@Dao
interface MetroStationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(station: MetroStationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<MetroStationEntity>)

    @Update
    suspend fun update(station: MetroStationEntity)

    @Delete
    suspend fun delete(station: MetroStationEntity)

    @Query("DELETE FROM metro_station WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM metro_station WHERE lineId = :lineId ORDER BY orderIndex ASC, id ASC")
    fun observeByLine(lineId: Long): Flow<List<MetroStationEntity>>

    @Query("SELECT * FROM metro_station WHERE lineId = :lineId ORDER BY orderIndex ASC, id ASC")
    suspend fun getByLine(lineId: Long): List<MetroStationEntity>

    @Query("SELECT * FROM metro_station ORDER BY lineId ASC, orderIndex ASC, id ASC")
    suspend fun getAllOnce(): List<MetroStationEntity>

    @Query("SELECT * FROM metro_station WHERE id = :id")
    suspend fun getById(id: Long): MetroStationEntity?

    @Query("SELECT * FROM metro_station WHERE id = :id")
    fun observeById(id: Long): Flow<MetroStationEntity?>

    @Query("UPDATE metro_station SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun updateOrder(id: Long, orderIndex: Int)

    @Transaction
    @Query("SELECT * FROM metro_station WHERE lineId = :lineId ORDER BY orderIndex ASC, id ASC")
    fun observeWithEntrances(lineId: Long): Flow<List<MetroStationWithEntrances>>

    @Transaction
    @Query("SELECT * FROM metro_station WHERE id = :id")
    suspend fun getWithEntrances(id: Long): MetroStationWithEntrances?
}

@Dao
interface MetroEntranceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entrance: MetroEntranceEntity): Long

    @Update
    suspend fun update(entrance: MetroEntranceEntity)

    @Delete
    suspend fun delete(entrance: MetroEntranceEntity)

    @Query("DELETE FROM metro_entrance WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM metro_entrance WHERE metroStationId = :stationId ORDER BY name ASC")
    fun observeByStation(stationId: Long): Flow<List<MetroEntranceEntity>>

    @Query("SELECT * FROM metro_entrance WHERE metroStationId = :stationId ORDER BY name ASC")
    suspend fun getByStation(stationId: Long): List<MetroEntranceEntity>

    @Query("SELECT * FROM metro_entrance WHERE id = :id")
    suspend fun getById(id: Long): MetroEntranceEntity?

    @Query("SELECT * FROM metro_entrance WHERE id = :id")
    fun observeById(id: Long): Flow<MetroEntranceEntity?>

    @Query("SELECT * FROM metro_entrance ORDER BY metroStationId ASC, name ASC")
    suspend fun getAllOnce(): List<MetroEntranceEntity>

    @Transaction
    @Query("SELECT * FROM metro_entrance WHERE id = :id")
    suspend fun getDetailById(id: Long): MetroEntranceDetail?

    @Query("SELECT COUNT(*) FROM metro_entrance")
    suspend fun count(): Int
}

@Dao
interface StationMarkerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(marker: StationMarkerEntity): Long

    @Update
    suspend fun update(marker: StationMarkerEntity)

    @Delete
    suspend fun delete(marker: StationMarkerEntity)

    @Query("SELECT * FROM station_marker ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<StationMarkerEntity>>

    @Query("SELECT * FROM station_marker ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllOnce(): List<StationMarkerEntity>

    @Query("SELECT COUNT(*) FROM station_marker")
    suspend fun count(): Int

    @Query("SELECT MAX(sortOrder) FROM station_marker")
    suspend fun maxSortOrder(): Int?
}
