package com.transnote.traffic.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 批量站点解析测试：必须严格保持录入顺序（回归「批量添加顺序错乱」问题）。
 */
class StationNameParserTest {

    @Test
    fun `keeps the exact input order`() {
        val input = "火车站\n人民广场\n市图书馆\n体育馆"
        assertEquals(
            listOf("火车站", "人民广场", "市图书馆", "体育馆"),
            StationNameParser.parse(input)
        )
    }

    @Test
    fun `supports multiple separators while preserving order`() {
        val input = "A站、B站，C站, D站→E站>F站"
        assertEquals(
            listOf("A站", "B站", "C站", "D站", "E站", "F站"),
            StationNameParser.parse(input)
        )
    }

    @Test
    fun `drops blank tokens and trims whitespace`() {
        val input = "  火车站 \n\n 人民广场 \n 、 \n市图书馆  "
        assertEquals(
            listOf("火车站", "人民广场", "市图书馆"),
            StationNameParser.parse(input)
        )
    }

    @Test
    fun `empty input yields empty list`() {
        assertEquals(emptyList<String>(), StationNameParser.parse(""))
        assertEquals(emptyList<String>(), StationNameParser.parse("   \n 、， "))
    }
}
