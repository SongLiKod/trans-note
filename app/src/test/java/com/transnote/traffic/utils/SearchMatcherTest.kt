package com.transnote.traffic.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 本地离线检索工具单元测试（TechDoc 5.5）。 */
class SearchMatcherTest {

    @Test
    fun exactMatchScoresHighest() {
        val exact = SearchMatcher.score("302路", "302路")
        val prefix = SearchMatcher.score("302", "302路")
        val contains = SearchMatcher.score("02路", "302路")
        assertTrue(exact > prefix)
        assertTrue(prefix > contains)
        assertTrue(contains > SearchMatcher.NO_MATCH)
    }

    @Test
    fun noMatchReturnsZero() {
        assertEquals(SearchMatcher.NO_MATCH, SearchMatcher.score("人民广场", "火车站"))
    }

    @Test
    fun multiKeywordRequiresAllKeywords() {
        assertTrue(SearchMatcher.matches("人民 广场", "人民广场东侧"))
        assertFalse(SearchMatcher.matches("人民 火车站", "人民广场东侧"))
    }

    @Test
    fun fieldWeightAffectsScore() {
        val asName = SearchMatcher.weightedScore("医院", "市医院", SearchMatcher.Weight.NAME)
        val asNote = SearchMatcher.weightedScore("医院", "市医院", SearchMatcher.Weight.NOTE)
        assertTrue(asName > asNote)
    }

    @Test
    fun snippetTruncatesLongText() {
        val text = "本市最大的综合交通枢纽，可换乘地铁1号线与多路公交线路"
        val snippet = SearchMatcher.snippet(text, "地铁", maxLength = 12)
        assertTrue(snippet.contains("地铁"))
        assertTrue(snippet.length <= text.length)
    }
}
