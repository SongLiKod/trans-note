package com.transnote.traffic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 线路 / 站点配色（PRD 视觉规范扩展）。
 *
 * 颜色以 HEX 字符串存储（如 `#2563EB`）：
 * - 线路颜色为空时使用 [DEFAULT]；
 * - 站点颜色为空时「跟随线路颜色」，实现站点默认继承、可单独覆盖。
 */
object LineColors {

    /** 默认线路色（与主题主色一致） */
    const val DEFAULT = "#2563EB"

    /** 预设调色板：便于把不同线路 / 站点区分开 */
    val palette: List<String> = listOf(
        "#2563EB", // 蓝
        "#0EA5E9", // 天蓝
        "#14B8A6", // 青
        "#22C55E", // 绿
        "#84CC16", // 柠檬
        "#EAB308", // 黄
        "#F97316", // 橙
        "#EF4444", // 红
        "#EC4899", // 粉
        "#A855F7", // 紫
        "#6366F1", // 靛
        "#64748B"  // 灰
    )

    /** 解析 HEX 颜色，失败返回 [fallback] */
    fun parse(hex: String?, fallback: Color): Color {
        val raw = hex?.trim()?.removePrefix("#") ?: return fallback
        if (raw.length != 6) return fallback
        val value = raw.toLongOrNull(16) ?: return fallback
        return Color(0xFF000000L or value)
    }

    /** 线路颜色：为空时回退到默认色 */
    fun line(hex: String?, fallback: Color): Color =
        if (hex.isNullOrBlank()) parse(DEFAULT, fallback) else parse(hex, fallback)

    /** 站点颜色：为空时跟随线路颜色 */
    fun station(stationHex: String?, lineHex: String?, fallback: Color): Color =
        if (stationHex.isNullOrBlank()) line(lineHex, fallback) else parse(stationHex, line(lineHex, fallback))

    /**
     * 线路图元素颜色（连线 / 圆点）：为空时「跟随线路颜色」。
     *
     * 与 [line] 的区别：本方法在为空时返回 [fallback]（即线路颜色），
     * 而不是 [DEFAULT] 默认蓝，因此线路连线默认与线路同色。
     */
    fun inherit(hex: String?, fallback: Color): Color =
        if (hex.isNullOrBlank()) fallback else parse(hex, fallback)

    /** 在给定底色上可读的前景色（用于色块上的对勾） */
    fun onColor(background: Color): Color =
        if (background.luminance() > 0.6f) Color(0xFF1F2937) else Color.White
}
