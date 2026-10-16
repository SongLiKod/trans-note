package com.transnote.traffic.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    private val amapKeyKey = stringPreferencesKey("amap_api_key")
    private val showStationIndexKey = booleanPreferencesKey("show_station_index")
    private val showPassingLinesKey = booleanPreferencesKey("show_passing_lines")

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

    /** 高德 Web 服务 API Key（用于在线导入公交线路，空表示未配置） */
    val amapKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[amapKeyKey].orEmpty()
    }

    suspend fun setAmapKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[amapKeyKey] = key.trim()
        }
    }

    /** 是否在站点列表的小圆圈内显示序号（默认开启） */
    val showStationIndex: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[showStationIndexKey] ?: true
    }

    suspend fun setShowStationIndex(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[showStationIndexKey] = show
        }
    }

    /** 是否在站点列表显示「经过线路」（站点右侧按钮，点击查看经过该站的所有线路，默认开启） */
    val showPassingLines: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[showPassingLinesKey] ?: true
    }

    suspend fun setShowPassingLines(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[showPassingLinesKey] = show
        }
    }
}
