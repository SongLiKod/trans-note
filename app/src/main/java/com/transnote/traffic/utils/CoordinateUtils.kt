package com.transnote.traffic.utils

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 坐标工具（TechDoc 5.3 / 5.4）：经纬度计算、地图边界、距离估算。
 */
object CoordinateUtils {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** 两点球面距离（米） */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2.0)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    fun distanceText(meters: Double): String =
        if (meters < 1000) "${meters.toInt()} m"
        else String.format(java.util.Locale.CHINA, "%.2f km", meters / 1000)

    /** 判断坐标是否有效 */
    fun isValid(lat: Double?, lng: Double?): Boolean =
        lat != null && lng != null &&
            lat in -90.0..90.0 && lng in -180.0..180.0 &&
            !(lat == 0.0 && lng == 0.0)

    /**
     * 轨迹中点（用于地图自动缩放）
     */
    fun centerOf(points: List<Pair<Double, Double>>): Pair<Double, Double>? {
        if (points.isEmpty()) return null
        return points.map { it.first }.average() to points.map { it.second }.average()
    }

    /** 格式化为可读坐标文本 */
    fun format(lat: Double?, lng: Double?): String =
        if (isValid(lat, lng)) String.format(java.util.Locale.CHINA, "%.6f, %.6f", lat, lng)
        else "未设置坐标"
}
