package com.transnote.traffic.ui.navigation

/**
 * 页面路由（TechDoc 第 6 节 screens 划分）。
 */
object Routes {
    const val HOME_BUS = "home_bus"
    const val HOME_METRO = "home_metro"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    const val EDIT_LINE = "edit_line?type={type}&id={id}&cityId={cityId}"
    fun editLine(type: String, id: Long = 0L, cityId: Long = 0L) =
        "edit_line?type=$type&id=$id&cityId=$cityId"

    const val BUS_LINE = "bus_line/{id}"
    fun busLine(id: Long) = "bus_line/$id"

    const val METRO_LINE = "metro_line/{id}"
    fun metroLine(id: Long) = "metro_line/$id"

    const val BUS_STATION = "bus_station/{id}"
    fun busStation(id: Long) = "bus_station/$id"

    const val METRO_STATION = "metro_station/{id}"
    fun metroStation(id: Long) = "metro_station/$id"

    /** 地铁出入口详情 / 编辑（id>0 为编辑，=0 为新增） */
    const val ENTRANCE = "entrance?stationId={stationId}&id={id}"
    fun entrance(stationId: Long, id: Long = 0L) = "entrance?stationId=$stationId&id=$id"

    const val OCR = "ocr"
}
