package com.transnote.traffic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.transnote.traffic.utils.ThemeManager
import com.transnote.traffic.utils.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 主题 ViewModel（TechDoc 5.1）：读取/切换主题模式，持久化到 DataStore。
 */
class ThemeViewModel(private val themeManager: ThemeManager) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themeManager.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.FOLLOW_SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themeManager.setThemeMode(mode) }
    }
}
