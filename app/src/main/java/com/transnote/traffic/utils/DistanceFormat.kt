package com.transnote.traffic.utils

import java.util.Locale
import kotlin.math.roundToInt

/** 距离展示（公里，保留一位小数；小于 0.1km 显示 0.1km） */
fun formatDistanceKm(meters: Double): String {
    val km = (meters / 1000.0).coerceAtLeast(0.1)
    return String.format(Locale.US, "%.1fkm", km)
}

/** 距离展示（不足 1km 用米，否则用公里保留一位小数） */
fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()}m"
    else String.format(Locale.US, "%.1fkm", meters / 1000.0)
