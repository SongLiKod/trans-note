package com.transnote.traffic.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
    val startStop: String,
    val endStop: String,
    val startTime: String,
    val endTime: String,
    val distance: String,
    val stations: List<AmapStation>
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

    // 高德 POI 分类码：机场 1501、火车站 1502、地铁站 1505
    private const val TYPES_AIRPORT = "150104"
    private const val TYPES_TRAIN = "150200"
    private const val TYPES_METRO = "150500"

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
            result.add(
                AmapBusLine(
                    id = line.optString("id"),
                    name = line.optString("name"),
                    type = line.optString("type"),
                    startStop = line.optString("start_stop"),
                    endStop = line.optString("end_stop"),
                    startTime = cleanTime(line.optString("start_time")),
                    endTime = cleanTime(line.optString("end_time")),
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

    /** 高德时间字段可能为 "05:10" 或 "05:10-23:30"，统一取首段 */
    private fun cleanTime(raw: String): String =
        raw.trim().substringBefore("-").substringBefore("—").trim()
}
