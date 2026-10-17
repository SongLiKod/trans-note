package com.transnote.traffic.utils

import android.content.Context
import com.transnote.traffic.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** 应用内更新状态机 */
sealed interface UpdateState {

    /** 尚未检查 */
    data object Idle : UpdateState

    /** 正在检查更新 */
    data object Checking : UpdateState

    /** 已是最新版本 */
    data object UpToDate : UpdateState

    /** 发现新版本 */
    data class Available(val info: UpdateInfo) : UpdateState

    /** 正在下载（percent 为 -1 表示总长未知） */
    data class Downloading(
        val info: UpdateInfo,
        val percent: Int,
        val downloaded: Long,
        val total: Long
    ) : UpdateState

    /** 安装包已下载并校验通过，可调起安装 */
    data class Ready(val info: UpdateInfo, val apkFile: File) : UpdateState

    /** 检查或下载失败；info 非空表示下载失败，可重试下载 */
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

/** 是否应展示全局更新弹窗（检查过程中不展示） */
val UpdateState.showInDialog: Boolean
    get() = when (this) {
        is UpdateState.Available,
        is UpdateState.Downloading,
        is UpdateState.Ready,
        is UpdateState.Failed -> true
        else -> false
    }

/**
 * 应用更新管理器：启动时后台静默检查 GitHub Release，发现新版后在软件内下载并调起安装。
 *
 * 状态经 [state] / [dialogVisible] 暴露给 UI：
 * [TransNoteRoot] 渲染全局更新弹窗，设置页展示检查入口与进度。
 * 自有作用域（非 ViewModel），随进程存活。
 */
class UpdateManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _dialogVisible = MutableStateFlow(false)
    val dialogVisible: StateFlow<Boolean> = _dialogVisible.asStateFlow()

    private var startupChecked = false
    private var downloadJob: Job? = null

    /** App 启动时后台静默检查一次；发现新版自动弹出更新弹窗，失败仅记录状态供设置页展示 */
    fun checkOnStartup() {
        if (startupChecked) return
        startupChecked = true
        check()
    }

    /** 立即检查更新（设置页按钮）；发现新版自动弹出更新弹窗 */
    fun check() {
        val previous = _state.value
        when (previous) {
            is UpdateState.Checking, is UpdateState.Downloading -> return
            // 已下载完成，直接展示安装弹窗
            is UpdateState.Ready -> {
                _dialogVisible.value = true
                return
            }
            else -> Unit
        }
        // 携带既有 info，检查失败时仍可从弹窗重试下载
        val carriedInfo = when (previous) {
            is UpdateState.Available -> previous.info
            is UpdateState.Failed -> previous.info
            else -> null
        }
        _state.value = UpdateState.Checking
        scope.launch {
            AppUpdater.checkLatest(BuildConfig.VERSION_NAME).fold(
                onSuccess = { info ->
                    if (info == null) {
                        _state.value = UpdateState.UpToDate
                        _dialogVisible.value = false
                    } else {
                        _state.value = UpdateState.Available(info)
                        _dialogVisible.value = true
                    }
                },
                onFailure = { e ->
                    _state.value = UpdateState.Failed(
                        e.message ?: "检查更新失败",
                        info = carriedInfo
                    )
                }
            )
        }
    }

    /** 下载更新：按候选包顺序尝试，每个包下载后先校验签名，不匹配则顺延下一个 */
    fun download() {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Failed -> s.info ?: return
            else -> return
        }
        val candidates = AppUpdater.selectApkAssets(info.versionName, info.assets)
        if (candidates.isEmpty()) {
            _state.value = UpdateState.Failed("该版本没有可下载的安装包", info)
            return
        }
        _dialogVisible.value = true
        downloadJob = scope.launch {
            var lastPercent = Int.MIN_VALUE
            var lastPublishedBytes = 0L
            var error: String? = null
            var readyFile: File? = null
            try {
                for (asset in candidates) {
                    val result = AppUpdater.download(context, asset) { downloaded, total ->
                        val percent =
                            if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                            else -1
                        if (percent != lastPercent || downloaded - lastPublishedBytes >= 1_048_576L) {
                            lastPercent = percent
                            lastPublishedBytes = downloaded
                            _state.value =
                                UpdateState.Downloading(info, percent, downloaded, total)
                        }
                    }
                    val e = result.exceptionOrNull()
                    if (e is CancellationException) throw e
                    if (e != null) {
                        error = e.message ?: "下载失败"
                        break
                    }
                    val file = result.getOrThrow()
                    if (AppUpdater.verifyApk(context, file)) {
                        readyFile = file
                        break
                    }
                    // 签名不匹配（如 debug 安装正式包），删除后尝试下一个候选
                    file.delete()
                }
                if (readyFile == null && error == null) {
                    error = "安装包与当前应用签名不一致，无法覆盖安装"
                }
            } catch (e: CancellationException) {
                _state.value = UpdateState.Available(info)
                throw e
            }
            _state.value = readyFile?.let { UpdateState.Ready(info, it) }
                ?: UpdateState.Failed(error ?: "下载失败", info)
        }
    }

    /** 取消下载，回到「发现新版本」状态 */
    fun cancelDownload() {
        val info = (_state.value as? UpdateState.Downloading)?.info ?: return
        downloadJob?.cancel()
        downloadJob = null
        _state.value = UpdateState.Available(info)
    }

    /** 调起系统安装确认页（需已获得安装未知应用授权，见 [AppUpdater.canInstall]） */
    fun install(context: Context, apkFile: File) {
        AppUpdater.install(context, apkFile)
    }

    /** 展示更新弹窗（设置页「立即更新」入口） */
    fun showUpdateDialog() {
        if (_state.value.showInDialog) _dialogVisible.value = true
    }

    /** 关闭更新弹窗（不改变状态，可再次打开） */
    fun dismissDialog() {
        _dialogVisible.value = false
    }
}
