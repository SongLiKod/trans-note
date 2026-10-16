package com.transnote.traffic.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.transnote.traffic.data.file.BackupManager.BackupResult
import com.transnote.traffic.repository.TransNoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val busLineCount: Int = 0,
    val metroLineCount: Int = 0,
    val entranceCount: Int = 0,
    val busy: Boolean = false,
    val message: String? = null
)

/**
 * 设置 ViewModel：数据统计 + 本地备份导入导出（PRD 3.5）。
 */
class SettingsViewModel(private val repo: TransNoteRepository) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch {
            val (bus, metro, entrances) = repo.totalCounts()
            _state.update {
                it.copy(busLineCount = bus, metroLineCount = metro, entranceCount = entrances)
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun suggestedBackupName(): String = repo.backupManager.suggestedBackupName()

    /** 导出备份到用户选择的本地文件 */
    fun exportBackup(destUri: Uri) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val result = repo.exportBackup(destUri)) {
                is BackupResult.Success ->
                    _state.update {
                        it.copy(
                            busy = false,
                            message = "导出成功：数据库文件 ${result.dbFiles} 个，图片 ${result.imageCount} 张"
                        )
                    }
                is BackupResult.Failure ->
                    _state.update { it.copy(busy = false, message = "导出失败：${result.message}") }
            }
        }
    }

    /** 从本地 ZIP 备份恢复 */
    fun importBackup(zipUri: Uri) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val result = repo.importBackup(zipUri)) {
                is BackupResult.Success -> {
                    refreshStats()
                    _state.update {
                        it.copy(busy = false, message = "导入成功：已恢复图片 ${result.imageCount} 张")
                    }
                }
                is BackupResult.Failure ->
                    _state.update { it.copy(busy = false, message = "导入失败：${result.message}") }
            }
        }
    }
}

/**
 * OCR ViewModel（TechDoc 5.2）：ML Kit 离线识别站牌文字，结果供表单自动填充。
 */
class OcrViewModel(private val repo: TransNoteRepository) : ViewModel() {

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val helper = com.transnote.traffic.utils.OcrHelper()

    fun recognize(context: Context, uri: Uri) {
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            runCatching { helper.recognize(context.applicationContext, uri) }
                .onSuccess { _lines.value = it }
                .onFailure { _error.value = it.message ?: "识别失败" }
            _busy.value = false
        }
    }

    fun clear() {
        _lines.value = emptyList()
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        helper.close()
    }

    /** 便于外部把识别结果回传 */
    val repository: TransNoteRepository get() = repo
}
