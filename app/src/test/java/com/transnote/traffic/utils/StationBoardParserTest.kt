package com.transnote.traffic.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 站牌 OCR 文本解析单元测试（PRD 3.1）。 */
class StationBoardParserTest {

    @Test
    fun parsesLineNameStationsAndTimes() {
        val lines = listOf(
            "302路 火车站 → 人民广场",
            "首班 06:00",
            "末班 22:30",
            "票价 2元",
            "途经：火车站、人民广场、市图书馆"
        )
        val parsed = StationBoardParser.parse(lines)
        assertEquals("302路", parsed.lineName)
        assertEquals("06:00", parsed.firstBus)
        assertEquals("22:30", parsed.lastBus)
        assertTrue(parsed.stations.contains("火车站"))
        assertTrue(parsed.stations.contains("人民广场"))
    }

    @Test
    fun emptyInputReturnsEmptyResult() {
        val parsed = StationBoardParser.parse(emptyList())
        assertEquals("", parsed.lineName)
        assertTrue(parsed.stations.isEmpty())
    }
}
