package com.transnote.traffic.utils

/**
 * 本地模糊搜索工具（TechDoc 5.5）。
 * 纯 Kotlin 字符串匹配：完全相等 > 前缀 > 包含 > 子序列模糊，返回匹配度得分，0 表示不匹配。
 */
object SearchMatcher {

    const val NO_MATCH = 0

    /** 权重：字段重要程度（站点名/线路名权重高，备注权重低） */
    object Weight {
        const val NAME = 100
        const val ROAD = 80
        const val LANDMARK = 70
        const val NOTE = 50
    }

    /**
     * 计算 [query] 与 [candidate] 的匹配得分。
     * 返回 0 表示不匹配；得分越高匹配度越高。
     */
    fun score(query: String, candidate: String?): Int {
        val q = query.trim().lowercase()
        val c = candidate?.trim()?.lowercase().orEmpty()
        if (q.isEmpty() || c.isEmpty()) return NO_MATCH
        return when {
            c == q -> 100
            c.startsWith(q) -> 88
            c.contains(q) -> 72
            fuzzySubsequence(q, c) -> 45
            else -> NO_MATCH
        }
    }

    /** 反向搜索：地名可能以多个关键词（空格分隔）出现，全部命中才算匹配，取平均分 */
    fun scoreMultiKeyword(query: String, candidate: String?): Int {
        val keywords = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (keywords.isEmpty()) return NO_MATCH
        if (keywords.size == 1) return score(keywords.first(), candidate)
        var total = 0
        for (kw in keywords) {
            val s = score(kw, candidate)
            if (s == NO_MATCH) return NO_MATCH
            total += s
        }
        return total / keywords.size
    }

    /** 名称/编号模糊匹配：支持多关键词（空格分隔） */
    fun matches(query: String, candidate: String?): Boolean =
        scoreMultiKeyword(query, candidate) > NO_MATCH

    /** 带字段权重的综合得分 */
    fun weightedScore(query: String, candidate: String?, weight: Int): Int {
        val base = scoreMultiKeyword(query, candidate)
        if (base == NO_MATCH) return NO_MATCH
        return base * weight / Weight.NAME
    }

    /** 子序列匹配：query 的字符按顺序出现在 candidate 中（如 "gjk" 命中 "guangjiake"） */
    private fun fuzzySubsequence(query: String, candidate: String): Boolean {
        if (query.length < 2) return false
        var qi = 0
        for (ch in candidate) {
            if (qi < query.length && ch == query[qi]) qi++
            if (qi == query.length) return true
        }
        return false
    }

    /** 高亮用：返回命中片段（若过长则截取上下文） */
    fun snippet(text: String?, query: String, maxLength: Int = 40): String {
        val t = text.orEmpty()
        if (t.isEmpty()) return ""
        val idx = t.lowercase().indexOf(query.trim().lowercase())
        if (idx < 0 || t.length <= maxLength) return t.take(maxLength)
        val start = (idx - 8).coerceAtLeast(0)
        val end = (start + maxLength).coerceAtMost(t.length)
        return (if (start > 0) "…" else "") + t.substring(start, end) + (if (end < t.length) "…" else "")
    }
}
