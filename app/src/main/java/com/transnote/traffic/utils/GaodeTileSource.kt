package com.transnote.traffic.utils

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * 高德地图瓦片源。
 *
 * 官方 OSM 瓦片在国内网络下经常无法直连，导致地图长时间灰色无瓦片；
 * 高德瓦片在国内可直连。**注意**：高德瓦片为 GCJ-02 坐标系，
 * 站点经纬度（WGS-84）在显示与选点时需经 [CoordinateUtils] 转换。
 */
class GaodeTileSource : OnlineTileSourceBase(
    "Gaode",
    3,
    18,
    256,
    "",
    arrayOf("https://wprd01.is.autonavi.com/appmaptile")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val z = MapTileIndex.getZoom(pMapTileIndex)
        // 4 个服务器轮询，分散请求压力
        val server = 1 + (x + y) % 4
        return "https://wprd0$server.is.autonavi.com/appmaptile" +
            "?lang=zh_cn&size=1&scl=1&style=7&x=$x&y=$y&z=$z"
    }
}
