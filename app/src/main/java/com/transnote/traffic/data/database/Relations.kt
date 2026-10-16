package com.transnote.traffic.data.database

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * 关系查询包装类（TechDoc 第 4 节关系说明）：
 * - 一条公交线路包含上行、下行两套独立站点集合（BusStation.direction 区分）；
 * - 一个地铁站点可以关联多个地铁出入口；
 * - 公交站点可绑定 0 或多个地铁出入口，实现换乘标记。
 */

data class BusStationWithTransfers(
    @Embedded val station: BusStationEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BusStationEntranceCrossRef::class,
            parentColumn = "busStationId",
            entityColumn = "metroEntranceId"
        )
    )
    val entrances: List<MetroEntranceEntity> = emptyList()
)

data class MetroStationWithEntrances(
    @Embedded val station: MetroStationEntity,
    @Relation(parentColumn = "id", entityColumn = "metroStationId")
    val entrances: List<MetroEntranceEntity> = emptyList()
)

/** 地铁出入口 + 所属地铁站点 + 所属线路（用于换乘跳转详情展示） */
data class MetroEntranceDetail(
    @Embedded val entrance: MetroEntranceEntity,
    @Relation(parentColumn = "metroStationId", entityColumn = "id")
    val station: MetroStationEntity? = null
)
