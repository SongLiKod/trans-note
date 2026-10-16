package com.transnote.traffic.utils

/**
 * 离线检索结果模型（TechDoc 5.5：全部使用 Kotlin 本地字符串匹配，不调用任何外部搜索接口）。
 */
enum class SearchResultType {
    BUS_LINE,      // 公交线路（正向搜索）
    METRO_LINE,    // 地铁线路（正向搜索）
    BUS_STATION,   // 公交站点（反向搜索：站点名 / 站点备注）
    METRO_STATION, // 地铁站点（反向搜索）
    METRO_ENTRANCE // 地铁出入口（反向搜索：道路 / 地标 / 备注）
}

data class SearchResult(
    /** 结果类型 */
    val type: SearchResultType,
    /** 主键：线路 id / 站点 id / 出入口 id */
    val id: Long,
    /** 所属线路 id（站点、出入口有值） */
    val lineId: Long = -1L,
    /** 主标题（线路名 / 站点名 / 出口名） */
    val title: String,
    /** 副标题（所属线路 + 方向等） */
    val subtitle: String = "",
    /** 命中的字段名（站点名/备注/道路/地标…） */
    val matchedField: String = "",
    /** 命中的文本片段 */
    val matchedText: String = "",
    /** 方向：0 上行 / 1 下行 / -1 不适用 */
    val direction: Int = -1,
    /** 匹配度得分（越高越靠前） */
    val score: Int = 0
)
