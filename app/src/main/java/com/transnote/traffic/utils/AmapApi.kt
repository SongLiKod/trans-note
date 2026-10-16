package com.transnote.traffic.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** 高德线路中的一个站点（经纬度已转换为 WGS-84） */
data class AmapStation(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

/** 高德公交线路查询结果（站点含经纬度，可直接关联到地图） */
data class AmapBusLine(
    val id: String,
    val name: String,
    val type: String,
    /** 首发站 */
    val startStop: String,
    /** 末站 */
    val endStop: String,
    /** 首班车时间 */
    val startTime: String,
    /** 末班车时间 */
    val endTime: String,
    /** 发车时刻表（由高德 timedesc / 运行时间解析，可能为空） */
    val timetable: String = "",
    val distance: String,
    val stations: List<AmapStation>
)

/**
 * 高德地铁出入口 POI。
 * [road] 为逆地理编码得到的真实街道名，[landmark] 为所属社区 / 地标，
 * [latitude]/[longitude] 已转为 WGS-84。
 */
data class AmapMetroExit(
    val name: String,
    val road: String = "",
    val landmark: String = "",
    /** 所在行政区（如「黄浦区」） */
    val district: String = "",
    /** 周边道路（如「南京东路、西藏中路」） */
    val nearby: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
)

/** 站点周边可换乘的交通设施类型（对应默认站点标记名称） */
enum class AmapTransitKind(val markerName: String) {
    METRO("地铁"),
    TRAIN("火车站"),
    AIRPORT("飞机")
}

/**
 * 高德地图 Web 服务 API 客户端（公交线路关键字查询）。
 *
 * 接口：`v3/bus/linename`（`extensions=all` 返回途经站点与首末班时间）。
 * 返回坐标为国家 GCJ-02 坐标系，这里统一转换为 WGS-84 存储，
 * 与地图选点、轨迹绘制保持一致。
 *
 * 仅使用 `HttpURLConnection` 与系统 `org.json`，不引入额外网络库。
 */
object AmapApi {

    private const val BUS_LINE_URL = "https://restapi.amap.com/v3/bus/linename"
    private const val PLACE_AROUND_URL = "https://restapi.amap.com/v3/place/around"
    private const val PLACE_TEXT_URL = "https://restapi.amap.com/v3/place/text"
    private const val REGEO_URL = "https://restapi.amap.com/v3/geocode/regeo"

    // 高德 POI 分类码：机场 1501、火车站 1502、地铁站 1505、地铁出入口 150501
    private const val TYPES_AIRPORT = "150104"
    private const val TYPES_TRAIN = "150200"
    private const val TYPES_METRO = "150500"
    private const val TYPE_METRO_EXIT = "150501"

    /** 从出入口 POI 名称中提取「A口 / 2号口 / C出入口」等 */
    private val EXIT_REGEX = Regex("[A-Za-z0-9]{1,3}\\s*号?\\s*(出入口|出口|口)")

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** 关键字查询公交线路（[city] 可为城市名 / citycode / adcode） */
    suspend fun searchBusLines(
        key: String,
        keywords: String,
        city: String
    ): Result<List<AmapBusLine>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildString {
                append(BUS_LINE_URL)
                append("?key=").append(enc(key))
                append("&keywords=").append(enc(keywords))
                if (city.isNotBlank()) append("&city=").append(enc(city))
                append("&offset=20&page=1&extensions=all&output=JSON")
            }
            val body = httpGet(url)
            parse(body)
        }
    }

    /**
     * 查询某坐标周边的地铁站 / 火车站 / 机场。
     * 传入坐标为 WGS-84（本 App 存储坐标），内部转 GCJ-02 后再请求高德。
     * 任一请求失败都返回空集合，不抛出异常。
     */
    suspend fun detectNearbyTransit(
        key: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 800
    ): Set<AmapTransitKind> = withContext(Dispatchers.IO) {
        if (key.isBlank() || !CoordinateUtils.isValid(latitude, longitude)) return@withContext emptySet()
        runCatching {
            val (gLat, gLng) = CoordinateUtils.wgs84ToGcj02(latitude, longitude)
            // 经纬度顺序为「经度,纬度」，小数不超过 6 位
            val location = String.format(java.util.Locale.US, "%.6f,%.6f", gLng, gLat)
            val types = "$TYPES_AIRPORT|$TYPES_TRAIN|$TYPES_METRO"
            val url = buildString {
                append(PLACE_AROUND_URL)
                append("?key=").append(enc(key))
                append("&location=").append(enc(location))
                append("&types=").append(enc(types))
                append("&radius=").append(radiusMeters)
                append("&offset=25&page=1&extensions=base&output=JSON")
            }
            parseTransit(httpGet(url))
        }.getOrDefault(emptySet())
    }

    /**
     * 查询某地铁站周边的出入口（高德 POI 类型 150501）。
     * 有坐标时用周边搜索（更准），否则用关键字搜索。
     * 名称解析出入口编号；再对每个出口坐标做逆地理编码，得到真实街道与地标。
     */
    suspend fun searchMetroExits(
        key: String,
        stationName: String,
        latitude: Double,
        longitude: Double,
        city: String = ""
    ): List<AmapMetroExit> = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext emptyList()
        runCatching {
            val body = if (CoordinateUtils.isValid(latitude, longitude)) {
                val (gLat, gLng) = CoordinateUtils.wgs84ToGcj02(latitude, longitude)
                val location = String.format(java.util.Locale.US, "%.6f,%.6f", gLng, gLat)
                val url = buildString {
                    append(PLACE_AROUND_URL)
                    append("?key=").append(enc(key))
                    append("&location=").append(enc(location))
                    append("&types=").append(TYPE_METRO_EXIT)
                    append("&radius=1500&offset=25&page=1&extensions=base&output=JSON")
                }
                httpGet(url)
            } else {
                val url = buildString {
                    append(PLACE_TEXT_URL)
                    append("?key=").append(enc(key))
                    append("&keywords=").append(enc("$stationName 口"))
                    if (city.isNotBlank()) append("&city=").append(enc(city))
                    append("&types=").append(TYPE_METRO_EXIT)
                    append("&offset=25&page=1&extensions=base&output=JSON")
                }
                httpGet(url)
            }
            val raws = parseExits(body, stationName)
            if (raws.isEmpty()) return@runCatching emptyList()
            // 逆地理编码批量解析每个出口所在的真实街道、社区 / 商圈、行政区与周边道路
            val details = reverseGeocode(key, raws)
            raws.mapIndexed { i, raw ->
                val d = details.getOrElse(i) { ExitDetail.EMPTY }
                AmapMetroExit(
                    name = raw.name,
                    road = d.road,
                    landmark = d.landmark,
                    district = d.district,
                    nearby = d.nearby,
                    latitude = raw.wgsLat,
                    longitude = raw.wgsLng
                )
            }.sortedWith(compareBy({ it.name.firstOrNull()?.isDigit() == true }, { it.name }))
        }.getOrDefault(emptyList())
    }

    /** 出入口 POI 解析中间结果：名称 + 坐标（GCJ-02 用于逆地理编码，WGS-84 用于存储） */
    private data class ExitRaw(
        val name: String,
        val gcjLat: Double?,
        val gcjLng: Double?,
        val wgsLat: Double?,
        val wgsLng: Double?
    )

    private fun parseExits(body: String, stationName: String): List<ExitRaw> {
        val root = JSONObject(body)
        if (root.optString("status") != "1") return emptyList()
        val pois = root.optJSONArray("pois") ?: return emptyList()
        val core = stationName.removeSuffix("站").removeSuffix("地铁站")
        val result = LinkedHashMap<String, ExitRaw>()
        for (i in 0 until pois.length()) {
            val poi = pois.optJSONObject(i) ?: continue
            val name = poi.optString("name")
            // 尽量只取属于该站的出入口
            if (core.isNotBlank() && !name.contains(core)) continue
            val match = EXIT_REGEX.find(name) ?: continue
            val label = match.value.replace(" ", "")
                .replace("出入口", "口")
                .replace("出口", "口")
            val location = poi.optString("location")
            val parts = location.split(",")
            var gLat: Double? = null
            var gLng: Double? = null
            var wLat: Double? = null
            var wLng: Double? = null
            if (parts.size == 2) {
                val glng = parts[0].trim().toDoubleOrNull()
                val glat = parts[1].trim().toDoubleOrNull()
                if (glng != null && glat != null) {
                    gLat = glat
                    gLng = glng
                    // 高德返回 GCJ-02，转换为 WGS-84 存储
                    val (wl, wo) = CoordinateUtils.gcj02ToWgs84(glat, glng)
                    wLat = wl
                    wLng = wo
                }
            }
            // 同名去重：优先保留带坐标的一条
            val prev = result[label]
            if (prev == null || (prev.gcjLat == null && gLat != null)) {
                result[label] = ExitRaw(label, gLat, gLng, wLat, wLng)
            }
        }
        return result.values.toList()
    }

    /** 逆地理编码解析出的出口详情（真实街道 / 社区地标 / 行政区 / 周边道路） */
    private data class ExitDetail(
        val road: String,
        val landmark: String,
        val district: String,
        val nearby: String
    ) {
        companion object {
            val EMPTY = ExitDetail("", "", "", "")
        }
    }

    /**
     * 批量逆地理编码：返回与 [raws] 等长的出口详情列表。
     * 使用 batch=true，最多 20 个点一次请求。
     */
    private fun reverseGeocode(
        key: String,
        raws: List<ExitRaw>
    ): List<ExitDetail> {
        val out = MutableList(raws.size) { ExitDetail.EMPTY }
        val validIdx = raws.indices.filter { raws[it].gcjLat != null && raws[it].gcjLng != null }
        validIdx.chunked(20).forEach { chunk ->
            val location = chunk.joinToString("|") { i ->
                String.format(
                    java.util.Locale.US, "%.6f,%.6f", raws[i].gcjLng!!, raws[i].gcjLat!!
                )
            }
            val url = buildString {
                append(REGEO_URL)
                append("?key=").append(enc(key))
                append("&location=").append(enc(location))
                append("&batch=").append(if (chunk.size > 1) "true" else "false")
                append("&radius=200&extensions=all&output=JSON")
            }
            val body = runCatching { httpGet(url) }.getOrNull()
            val details = parseRegeo(body, chunk.size)
            chunk.forEachIndexed { j, rawIdx -> out[rawIdx] = details.getOrElse(j) { ExitDetail.EMPTY } }
        }
        return out
    }

    private fun parseRegeo(body: String?, expected: Int): List<ExitDetail> {
        val empty = List(expected) { ExitDetail.EMPTY }
        if (body == null) return empty
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return empty
        if (root.optString("status") != "1") return empty
        val list = ArrayList<ExitDetail>(expected)
        val arr = root.optJSONArray("regeocodes")
        if (arr != null) {
            for (i in 0 until arr.length()) list.add(parseDetail(arr.optJSONObject(i)))
        } else {
            list.add(parseDetail(root.optJSONObject("regeocode")))
        }
        while (list.size < expected) list.add(ExitDetail.EMPTY)
        return list
    }

    /** 从逆地理编码结果提取街道 / 社区地标 / 行政区 / 周边道路 */
    private fun parseDetail(regeo: JSONObject?): ExitDetail {
        if (regeo == null) return ExitDetail.EMPTY
        val comp = regeo.optJSONObject("addressComponent")
        val roadsArr = regeo.optJSONArray("roads")
        // 真实街道：门牌所属道路 → 附近道路 → 乡镇/街道
        val road = cleanField(comp?.optJSONObject("streetNumber")?.optString("street"))
            .ifBlank { firstArrayString(roadsArr, "name") }
            .ifBlank { cleanField(comp?.optString("township")) }
        // 地标：所在社区 → 所属商圈
        val landmark = cleanField(jsonFirst(comp?.optJSONObject("neighborhood")?.opt("name")))
            .ifBlank { firstArrayString(regeo.optJSONArray("businessAreas"), "name") }
        val district = cleanField(comp?.optString("district"))
        val nearby = arrayStrings(roadsArr, "name")
            .filter { it.isNotBlank() && it != road }
            .distinct()
            .take(3)
            .joinToString("、")
        return ExitDetail(road, landmark, district, nearby)
    }

    /** 取 JSON 数组里首个对象的指定字段（如 roads[].name / businessAreas[].name） */
    private fun firstArrayString(arr: JSONArray?, key: String): String {
        if (arr == null) return ""
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val v = cleanField(obj.optString(key))
            if (v.isNotBlank()) return v
        }
        return ""
    }

    /** 取 JSON 数组里所有对象的指定字段 */
    private fun arrayStrings(arr: JSONArray?, key: String): List<String> {
        if (arr == null) return emptyList()
        val list = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(cleanField(obj.optString(key)))
        }
        return list
    }

    /** 高德空值可能是 ""、[]、{} 等形式，统一清洗为空串 */
    private fun cleanField(raw: String?): String {
        val v = raw?.trim().orEmpty()
        return if (v.isEmpty() || v == "[]" || v == "{}" || v == "null") "" else v
    }

    /** 字段可能是字符串或数组（如 neighborhood.name），取首个有效值 */
    private fun jsonFirst(value: Any?): String = when (value) {
        null -> ""
        is JSONArray -> if (value.length() > 0) jsonFirst(value.opt(0)) else ""
        is String -> value
        else -> value.toString()
    }

    private fun parseTransit(body: String): Set<AmapTransitKind> {
        val root = JSONObject(body)
        if (root.optString("status") != "1") return emptySet()
        val pois = root.optJSONArray("pois") ?: return emptySet()
        val result = LinkedHashSet<AmapTransitKind>()
        for (i in 0 until pois.length()) {
            val poi = pois.optJSONObject(i) ?: continue
            // typecode 可能形如 "150500|150700"，取首个前缀判断
            val typecode = poi.optString("typecode").substringBefore("|").trim()
            when {
                typecode.startsWith("1505") -> result.add(AmapTransitKind.METRO)
                typecode.startsWith("1502") -> result.add(AmapTransitKind.TRAIN)
                typecode.startsWith("1501") -> result.add(AmapTransitKind.AIRPORT)
            }
        }
        return result
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("网络错误（HTTP $code）")
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): List<AmapBusLine> {
        val root = JSONObject(body)
        if (root.optString("status") != "1") {
            val info = root.optString("info").ifBlank { "查询失败" }
            throw IllegalStateException(info)
        }
        val lines = root.optJSONArray("buslines") ?: return emptyList()
        val result = ArrayList<AmapBusLine>(lines.length())
        for (i in 0 until lines.length()) {
            val line = lines.optJSONObject(i) ?: continue
            val startTime = formatAmapTime(line.optString("start_time"))
            val endTime = formatAmapTime(line.optString("end_time"))
            result.add(
                AmapBusLine(
                    id = line.optString("id"),
                    name = line.optString("name"),
                    type = line.optString("type"),
                    startStop = line.optString("start_stop"),
                    endStop = line.optString("end_stop"),
                    startTime = startTime,
                    endTime = endTime,
                    timetable = parseTimetable(line.optString("timedesc"), startTime, endTime),
                    distance = line.optString("distance"),
                    stations = parseStations(line.optJSONArray("busstops"))
                )
            )
        }
        return result
    }

    private fun parseStations(array: org.json.JSONArray?): List<AmapStation> {
        if (array == null) return emptyList()
        val list = ArrayList<AmapStation>(array.length())
        for (i in 0 until array.length()) {
            val stop = array.optJSONObject(i) ?: continue
            val name = stop.optString("name")
            val location = stop.optString("location")
            val parts = location.split(",")
            if (name.isBlank() || parts.size != 2) continue
            val lng = parts[0].trim().toDoubleOrNull() ?: continue
            val lat = parts[1].trim().toDoubleOrNull() ?: continue
            // 高德返回 GCJ-02，转换为 WGS-84 存储
            val (wLat, wLng) = CoordinateUtils.gcj02ToWgs84(lat, lng)
            list.add(AmapStation(name = name, latitude = wLat, longitude = wLng))
        }
        return list
    }

    /** 高德时间字段可能为 "05:10"、"05:10-23:30" 或 "0510"，统一取首段并规整为 HH:MM；空值（[] 等）归一为空串 */
    private fun formatAmapTime(raw: String): String {
        val first = cleanField(raw.trim().substringBefore("-").substringBefore("—").trim())
        return cleanField(normalizeClock(first))
    }

    /** 把 "0510" / "5:10" 规整为 "05:10"；无法识别时原样返回 */
    private fun normalizeClock(value: String): String {
        if (value.isEmpty()) return ""
        if (value.contains(":")) {
            val parts = value.split(":")
            val h = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return value
            val m = parts.getOrNull(1)?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull() ?: return value
            return String.format(java.util.Locale.US, "%02d:%02d", h, m)
        }
        if (value.length in 3..4 && value.all { it.isDigit() }) {
            val h = value.dropLast(2).toIntOrNull() ?: return value
            val m = value.takeLast(2).toIntOrNull() ?: return value
            return String.format(java.util.Locale.US, "%02d:%02d", h, m)
        }
        return value
    }

    /**
     * 解析高德 `timedesc`（线路详细时间，JSON 串，可能经过 URL 编码）为时刻表文本：
     * 抽取其中所有 HH:MM 时间点并按出现顺序去重；若取不到则用首末班时间兜底。
     */
    private fun parseTimetable(timedesc: String, startTime: String, endTime: String): String {
        val decoded = if (timedesc.isBlank()) timedesc
        else runCatching { URLDecoder.decode(timedesc, "UTF-8") }.getOrDefault(timedesc)
        val times = Regex("([01]?\\d|2[0-4]):[0-5]\\d")
            .findAll(decoded)
            .map { it.value }
            .distinct()
            .toList()
        if (times.isNotEmpty()) return times.joinToString(" ")
        // 兜底：仅给出首末班两个时间点
        return listOf(startTime, endTime).filter { it.isNotBlank() }.distinct().joinToString(" ")
    }
}
