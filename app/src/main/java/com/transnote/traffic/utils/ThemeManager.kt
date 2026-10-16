package com.transnote.traffic.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "transnote_settings")

/**
 * 应用设置管理器（TechDoc 5.1）：用户配置存储在 DataStore。
 * - 主题：跟随系统 / 浅色 / 深色
 * - 当前城市：首页城市切换的持久化选择
 * 切换后流程自动通知 UI 重构，实时生效，无需重启 APP。
 */
class ThemeManager(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val cityKey = longPreferencesKey("selected_city_id")

    /** 当前主题模式（默认跟随系统） */
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.fromKey(prefs[themeKey])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = mode.key
        }
    }

    /** 当前选中的城市 ID（0 表示尚未选择，回退到第一个城市） */
    val selectedCityId: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[cityKey] ?: 0L
    }

    suspend fun setSelectedCityId(cityId: Long) {
        context.dataStore.edit { prefs ->
            prefs[cityKey] = cityId
        }
    }
}
