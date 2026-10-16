package com.transnote.traffic.utils

import java.util.Locale

/** 距离展示（公里，保留一位小数；小于 0.1km 显示 0.1km） */
fun formatDistanceKm(meters: Double): String {
    val km = (meters / 1000.0).coerceAtLeast(0.1)
    return String.format(Locale.US, "%.1fkm", km)
}
