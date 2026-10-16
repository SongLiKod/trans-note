package com.transnote.traffic.utils

/**
 * 站牌 OCR 结果解析（PRD 3.1）：自动填充线路、站点、运营时间，识别结果支持人工修改。
 * 纯本地规则解析，不联网。
 */
object StationBoardParser {

    data class ParsedBoard(
        val lineName: String = "",
        val firstBus: String = "",
        val lastBus: String = "",
        val fare: String = "",
        val stations: List<String> = emptyList(),
        val rawText: List<String> = emptyList()
    )

    private val timeRegex = Regex("(\\d{1,2})\\s*[:：]\\s*(\\d{2})")
    private val lineNameRegex = Regex("([0-9]{1,4}\\s*路|\\d{1,3}\\s*号线|[A-Za-z]?\\d{1,4}\\s*路|[\\u4e00-\\u9fa5]{2,6}线)")
    private val fareRegex = Regex("((全程)?票价[:：]?\\s*[¥￥]?\\d+(\\.\\d+)?\\s*元?|[¥￥]\\s*\\d+(\\.\\d+)?)")

    /**
     * 解析 OCR 文本行，自动填充表单字段。
     */
    fun parse(lines: List<String>): ParsedBoard {
        if (lines.isEmpty()) return ParsedBoard()

        val joined = lines.joinToString("\n")

        // 1) 线路名
        val lineName = lineNameRegex.findAll(joined)
            .map { it.value.replace(" ", "") }
            .firstOrNull { it.length in 2..8 }
            .orEmpty()

        // 2) 运营时间 / 首末班
        val times = timeRegex.findAll(joined).map {
            "%02d:%02d".format(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }.toList()

        var firstBus = ""
        var lastBus = ""
        lines.forEach { line ->
            val t = timeRegex.find(line)?.let {
                "%02d:%02d".format(it.groupValues[1].toInt(), it.groupValues[2].toInt())
            } ?: ""
            if (t.isNotEmpty()) {
                if (line.contains("首") || line.contains("始")) firstBus = t
                if (line.contains("末") || line.contains("终")) lastBus = t
            }
        }
        if (firstBus.isEmpty() && times.isNotEmpty()) firstBus = times.first()
        if (lastBus.isEmpty() && times.size >= 2) lastBus = times.last()

        // 3) 票价
        val fare = fareRegex.find(joined)?.value?.replace(" ", "").orEmpty()

        // 4) 站点
        val stations = extractStations(lines)

        return ParsedBoard(
            lineName = lineName,
            firstBus = firstBus,
            lastBus = lastBus,
            fare = fare,
            stations = stations,
            rawText = lines
        )
    }

    /** 提取站点名称列表：优先按分隔符拆分含“站”的文本行 */
    private fun extractStations(lines: List<String>): List<String> {
        val separators = Regex("[→➜>\\-—–、,，/|]")
        val result = linkedSetOf<String>()

        for (line in lines) {
            if (line.contains("站") || line.contains(separators)) {
                val parts = line.split(separators)
                for (raw in parts) {
                    val name = cleanStationName(raw)
                    if (name.isNotEmpty()) result.add(name)
                }
            }
        }
        // 若没有拆分出站点，退化为整行含“站”的文本
        if (result.isEmpty()) {
            lines.filter { it.contains("站") && it.length in 2..20 }
                .forEach { result.add(cleanStationName(it)) }
        }
        return result.toList()
    }

    private fun cleanStationName(raw: String): String {
        var s = raw.trim()
        // 去噪：标签前缀（途经：/经过：/沿线：）
        s = s.replace(Regex("^[\\u4e00-\\u9fa5]{0,4}[:：]\\s*"), "")
        // 去噪：行首线路编号（如 “302路 火车站” → “火车站”）
        s = s.replace(Regex("^[A-Za-z]?\\d{1,4}\\s*路\\s*"), "")
        // 去噪：序号
        s = s.replace(Regex("^[0-9]+\\s*[.、)）]\\s*"), "")
        // 去噪：括号内容、引号
        s = s.replace(Regex("[（(【\\[].*?[)）】\\]]"), "")
        s = s.replace(Regex("[\"'“”‘’]"), "")
        s = s.replace("公交站", "").replace("站台", "").trim()
        if (s.isEmpty() || s.length > 20) return ""
        if (listOf("首班", "末班", "票价", "运营", "时间", "途经", "经过", "沿线", "方向", "开往")
                .any { s.contains(it) } && s.length <= 6
        ) return ""
        return s.trim(' ', ':', '：', '、', ',', '，', '-', '—', '→', '>', '.')
    }
}
