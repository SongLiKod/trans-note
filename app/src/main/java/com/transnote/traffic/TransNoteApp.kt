package com.transnote.traffic

import android.app.Application
import android.content.Context
import androidx.preference.PreferenceManager
import com.transnote.traffic.data.database.AppDatabase
import com.transnote.traffic.data.file.BackupManager
import com.transnote.traffic.data.file.ImageStorage
import com.transnote.traffic.repository.TransNoteRepository
import com.transnote.traffic.utils.ThemeManager
import com.transnote.traffic.utils.UpdateManager
import org.osmdroid.config.Configuration

/**
 * 应用入口。[AppContainer] 采用轻量手动依赖注入（纯本地应用，无需 DI 框架）。
 */
class TransNoteApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        initOsmdroid()
        // 启动时后台静默检查 GitHub Release 是否有新版，有则由根容器弹出更新弹窗
        container.updateManager.checkOnStartup()
    }

    /**
     * osmdroid 初始化（TechDoc 5.4 地图组件）。
     * 瓦片缓存与用户代理均指向 App 私有目录，业务数据不联网。
     */
    private fun initOsmdroid() {
        val config = Configuration.getInstance()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        config.load(this, prefs)
        // 使用 App 私有目录存放瓦片缓存，无需外部存储权限
        config.osmdroidBasePath = cacheDir.resolve("osmdroid")
        config.osmdroidTileCache = cacheDir.resolve("osmdroid/tiles")
        // 显式设置 UA，避免被瓦片服务拒绝
        config.userAgentValue = "TransNote/1.0 ($packageName)"
        config.save(this, prefs)
    }
}

/** 全局依赖容器 */
class AppContainer(private val app: Context) {
    /** 通过 provider 获取数据库实例，导入备份后会自动重建连接 */
    private val databaseProvider: () -> AppDatabase = { AppDatabase.get(app) }
    val database: AppDatabase get() = databaseProvider()
    val imageStorage: ImageStorage by lazy { ImageStorage(app) }
    val backupManager: BackupManager by lazy { BackupManager(app) }
    val themeManager: ThemeManager by lazy { ThemeManager(app) }
    /** 应用内更新：检查 GitHub Release / 下载安装包 / 调起安装 */
    val updateManager: UpdateManager by lazy { UpdateManager(app) }
    val repository: TransNoteRepository by lazy {
        TransNoteRepository(databaseProvider, imageStorage, backupManager)
    }
}
