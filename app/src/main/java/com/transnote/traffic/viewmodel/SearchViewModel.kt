package com.transnote.traffic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.transnote.traffic.repository.TransNoteRepository
import com.transnote.traffic.utils.SearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 检索模式（PRD 3.4）：正向（线路）/ 反向（地点）/ 全部 */
enum class SearchMode(val label: String) {
    FORWARD("正向·线路"),
    REVERSE("反向·地点"),
    ALL("全部")
}

/**
 * 离线检索 ViewModel（TechDoc 5.5）：全部使用 Kotlin 本地字符串匹配，秒级响应。
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(private val repo: TransNoteRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _mode = MutableStateFlow(SearchMode.ALL)
    val mode: StateFlow<SearchMode> = _mode.asStateFlow()

    val results: StateFlow<List<SearchResult>> = combine(
        _query.debounce(200),
        _mode
    ) { q, m -> q to m }
        .flatMapLatest { (q, m) ->
            if (q.isBlank()) flowOf(emptyList())
            else flowOf(
                when (m) {
                    SearchMode.FORWARD -> repo.searchLines(q)
                    SearchMode.REVERSE -> repo.searchPlaces(q)
                    SearchMode.ALL -> repo.searchAll(q)
                }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasQuery: StateFlow<Boolean> = _query
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setQuery(value: String) {
        _query.value = value
    }

    fun clearQuery() {
        _query.value = ""
    }

    fun setMode(value: SearchMode) {
        _mode.value = value
    }
}
