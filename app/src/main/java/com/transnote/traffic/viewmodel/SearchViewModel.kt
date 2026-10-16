package com.transnote.traffic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.transnote.traffic.repository.TransNoteRepository
import com.transnote.traffic.utils.AmapApi
import com.transnote.traffic.utils.AmapNearbyStop
import com.transnote.traffic.utils.SearchResult
import com.transnote.traffic.utils.ThemeManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 检索模式（PRD 3.4）：正向（线路）/ 反向（地点）/ 全部 */
enum class SearchMode(val label: String) {
    FORWARD("正向·线路"),
    REVERSE("反向·地点"),
    ALL("全部")
}

/**
 * 检索 ViewModel（TechDoc 5.5）：
 * - 本地（离线）检索：Kotlin 本地字符串匹配，秒级响应；
 * - 在线检索：调用高德，检索某个地点附近的公交站 / 地铁站。
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val repo: TransNoteRepository,
    private val settings: ThemeManager
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _mode = MutableStateFlow(SearchMode.ALL)
    val mode: StateFlow<SearchMode> = _mode.asStateFlow()

    /** 是否在线检索（默认离线） */
    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online.asStateFlow()

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

    // ---- 在线检索：某个地点附近的公交站 / 地铁站 ----
    private val _onlineResults = MutableStateFlow<List<AmapNearbyStop>>(emptyList())
    val onlineResults: StateFlow<List<AmapNearbyStop>> = _onlineResults.asStateFlow()
    private val _onlineBusy = MutableStateFlow(false)
    val onlineBusy: StateFlow<Boolean> = _onlineBusy.asStateFlow()
    private val _onlineMessage = MutableStateFlow<String?>(null)
    val onlineMessage: StateFlow<String?> = _onlineMessage.asStateFlow()

    init {
        viewModelScope.launch {
            combine(_query.debounce(300), _online) { q, on -> q to on }
                .collect { (q, on) ->
                    if (!on || q.isBlank()) {
                        _onlineResults.value = emptyList()
                        _onlineMessage.value = null
                        _onlineBusy.value = false
                        return@collect
                    }
                    val key = settings.amapKey.first()
                    if (key.isBlank()) {
                        _onlineResults.value = emptyList()
                        _onlineMessage.value = "请先在「设置 → 高德地图 API」填写 Key"
                        return@collect
                    }
                    _onlineBusy.value = true
                    _onlineMessage.value = null
                    val cityId = settings.selectedCityId.first()
                    val city = if (cityId > 0L) repo.getCityName(cityId) else ""
                    val list = AmapApi.searchNearbyTransit(key, q, city)
                    _onlineResults.value = list
                    _onlineMessage.value = if (list.isEmpty()) "未找到附近的公交 / 地铁站" else null
                    _onlineBusy.value = false
                }
        }
    }

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

    fun setOnline(value: Boolean) {
        if (_online.value != value) {
            _online.value = value
            _onlineResults.value = emptyList()
            _onlineMessage.value = null
        }
    }
}
