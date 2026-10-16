package com.transnote.traffic.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "transnote_settings")

/**
 * 主题管理器（TechDoc 5.1）：用户配置存储在 DataStore，三个选项：跟随系统 / 浅色 / 深色。
 * 切换后流程自动通知 UI 重构，实时生效，无需重启 APP。
 */
class ThemeManager(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")

    /** 当前主题模式（默认跟随系统） */
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.fromKey(prefs[themeKey])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = mode.key
        }
    }
}
