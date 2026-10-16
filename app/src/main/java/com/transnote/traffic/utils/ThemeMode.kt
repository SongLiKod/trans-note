package com.transnote.traffic.utils

/**
 * 主题模式：跟随系统 / 浅色 / 深色（PRD 3.6）。
 */
enum class ThemeMode(val key: String, val label: String) {
    FOLLOW_SYSTEM("follow_system", "跟随系统"),
    LIGHT("light", "浅色模式"),
    DARK("dark", "深色模式");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: FOLLOW_SYSTEM
    }
}
