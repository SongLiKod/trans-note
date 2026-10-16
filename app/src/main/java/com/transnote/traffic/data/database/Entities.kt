package com.transnote.traffic.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room 实体定义（严格对应 TechDoc 第 4 节数据库设计）。
 */

/** 城市表 */
@Entity(tableName = "city")
data class CityEntity(
    @PrimaryKey(autoGenerate = true) val cityId: Long = 0,
    val cityName: String
)

/** 公交线路：所属城市、线路名称、首末班、票价、上下行站点（站点独立表，按 direction 区分） */
@Entity(
    tableName = "bus_line",
    foreignKeys = [
        ForeignKey(
            entity = CityEntity::class,
            parentColumns = ["cityId"],
            childColumns = ["cityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cityId")]
)
data class BusLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cityId: Long = 0,
    val lineName: String,
    val firstBus: String = "",
    val lastBus: String = "",
    val fare: String = "",
    /** 开往方向 / 时刻表（公交上行、下行各自独立） */
    @ColumnInfo(defaultValue = "") val upDestination: String = "",
    @ColumnInfo(defaultValue = "") val downDestination: String = "",
    @ColumnInfo(defaultValue = "") val upTimetable: String = "",
    @ColumnInfo(defaultValue = "") val downTimetable: String = "",
    /** 线路颜色（HEX，如 #2563EB；空表示使用默认色） */
    @ColumnInfo(defaultValue = "") val colorHex: String = "",
    /** 线路图样式：连线颜色（空=跟随线路颜色） */
    @ColumnInfo(defaultValue = "") val railColorHex: String = "",
    /** 线路图样式：连线粗细（dp） */
    @ColumnInfo(defaultValue = "4") val railWidthDp: Int = 4,
    /** 线路图样式：站点圆点默认颜色（空=跟随线路颜色） */
    @ColumnInfo(defaultValue = "") val dotColorHex: String = "",
    /** 线路图样式：站点圆点大小（dp，直径） */
    @ColumnInfo(defaultValue = "14") val dotSizeDp: Int = 14,
    // 三期：收藏 / 置顶 / 分组
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val groupName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** 公交站点：名称、所属线路、方向、经纬度、文字备注、图片路径数组、是否接驳地铁 */
@Entity(
    tableName = "bus_station",
    foreignKeys = [
        ForeignKey(
            entity = BusLineEntity::class,
            parentColumns = ["id"],
            childColumns = ["lineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("lineId")]
)
data class BusStationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lineId: Long,
    /** 0 = 上行，1 = 下行 */
    val direction: Int = DIRECTION_UP,
    val orderIndex: Int = 0,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val note: String = "",
    val imagePaths: List<String> = emptyList(),
    val isMetroTransfer: Boolean = false,
    /** 站点圆点颜色（HEX；空表示跟随线路颜色） */
    @ColumnInfo(defaultValue = "") val colorHex: String = "",
    /** 站点标记（多选），关联 station_marker.id */
    @ColumnInfo(defaultValue = "") val markerIds: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DIRECTION_UP = 0
        const val DIRECTION_DOWN = 1
    }
}

/** 地铁线路 */
@Entity(
    tableName = "metro_line",
    foreignKeys = [
        ForeignKey(
            entity = CityEntity::class,
            parentColumns = ["cityId"],
            childColumns = ["cityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cityId")]
)
data class MetroLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cityId: Long = 0,
    val lineName: String,
    val firstBus: String = "",
    val lastBus: String = "",
    val fare: String = "",
    /** 开往方向 / 时刻表 */
    @ColumnInfo(defaultValue = "") val destination: String = "",
    @ColumnInfo(defaultValue = "") val timetable: String = "",
    val colorHex: String = "#2563EB",
    /** 线路图样式：连线颜色（空=跟随线路颜色） */
    @ColumnInfo(defaultValue = "") val railColorHex: String = "",
    /** 线路图样式：连线粗细（dp） */
    @ColumnInfo(defaultValue = "4") val railWidthDp: Int = 4,
    /** 线路图样式：站点圆点默认颜色（空=跟随线路颜色） */
    @ColumnInfo(defaultValue = "") val dotColorHex: String = "",
    /** 线路图样式：站点圆点大小（dp，直径） */
    @ColumnInfo(defaultValue = "14") val dotSizeDp: Int = 14,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val groupName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** 地铁站点 */
@Entity(
    tableName = "metro_station",
    foreignKeys = [
        ForeignKey(
            entity = MetroLineEntity::class,
            parentColumns = ["id"],
            childColumns = ["lineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("lineId")]
)
data class MetroStationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lineId: Long,
    val orderIndex: Int = 0,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val note: String = "",
    val imagePaths: List<String> = emptyList(),
    /** 站点圆点颜色（HEX；空表示跟随线路颜色） */
    @ColumnInfo(defaultValue = "") val colorHex: String = "",
    /** 站点标记（多选），关联 station_marker.id */
    @ColumnInfo(defaultValue = "") val markerIds: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

/** 站点标记（如：地铁 / 火车站 / 飞机）。列表由用户管理，站点可多选标记 */
@Entity(tableName = "station_marker")
data class StationMarkerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/** 地铁出入口：归属地铁站点，出口名称、对应道路、地标备注、文字备注、图片路径、经纬度 */
@Entity(
    tableName = "metro_entrance",
    foreignKeys = [
        ForeignKey(
            entity = MetroStationEntity::class,
            parentColumns = ["id"],
            childColumns = ["metroStationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("metroStationId")]
)
data class MetroEntranceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metroStationId: Long,
    val name: String,
    val road: String = "",
    val landmark: String = "",
    val note: String = "",
    val imagePaths: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** 公交站点 ↔ 地铁出入口 换乘关系（多对多，实现接驳标记） */
@Entity(
    tableName = "bus_station_entrance",
    primaryKeys = ["busStationId", "metroEntranceId"],
    foreignKeys = [
        ForeignKey(
            entity = BusStationEntity::class,
            parentColumns = ["id"],
            childColumns = ["busStationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MetroEntranceEntity::class,
            parentColumns = ["id"],
            childColumns = ["metroEntranceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("busStationId"), Index("metroEntranceId")]
)
data class BusStationEntranceCrossRef(
    val busStationId: Long,
    val metroEntranceId: Long
)
