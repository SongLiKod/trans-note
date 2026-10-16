package com.transnote.traffic.data.database

import androidx.room.TypeConverter

/** 图片路径数组与数据库字符串的转换（PRD 3.2：站点/出入口图片路径数组） */
class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        value?.joinToString(SEPARATOR) ?: ""

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList()
        else value.split(SEPARATOR).filter { it.isNotEmpty() }

    companion object {
        private const val SEPARATOR = "\u001F"
    }
}
