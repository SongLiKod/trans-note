package com.transnote.traffic.utils

/**
 * 首末班展示文本：
 * - 两端都有 →「首末班 06:00~22:30」
 * - 只有首班 →「首班 06:00」；只有末班 →「末班 22:30」
 * - 都没有 → 空串（调用方应据此不展示）
 */
fun formatFirstLastBus(first: String, last: String): String {
    val f = first.trim()
    val l = last.trim()
    return when {
        f.isNotBlank() && l.isNotBlank() -> "首末班 $f~$l"
        f.isNotBlank() -> "首班 $f"
        l.isNotBlank() -> "末班 $l"
        else -> ""
    }
}
