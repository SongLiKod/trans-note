package com.transnote.traffic.utils

/**
 * 批量站点输入解析（PRD 3.2 / 3.3 批量添加）。
 *
 * 支持换行、顿号、中英文逗号、箭头等常见分隔符，
 * **严格保持用户在文本中录入的先后顺序**，只去掉首尾空白与空项。
 */
object StationNameParser {

    /** 分隔符：换行 / 顿号 / 逗号 / 箭头 */
    private val Separator = Regex("[\\n、,，→>]+")

    fun parse(text: String): List<String> =
        text.split(Separator).map { it.trim() }.filter { it.isNotEmpty() }
}
