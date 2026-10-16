package com.transnote.traffic.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager

/**
 * 定位工具（TechDoc 7：申请定位权限，用于地图打点）。
 * 优先读取系统最后一次已知位置，纯本地能力，不上传。
 */
object LocationUtils {

    @SuppressLint("MissingPermission")
    fun lastKnownLocation(context: Context): Pair<Double, Double>? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        for (provider in providers) {
            val enabled = runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
            if (!enabled) continue
            val location = runCatching { manager.getLastKnownLocation(provider) }.getOrNull() ?: continue
            return location.latitude to location.longitude
        }
        return null
    }
}
