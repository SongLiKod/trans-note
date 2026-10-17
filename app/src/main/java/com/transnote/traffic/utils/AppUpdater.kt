package com.transnote.traffic.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** GitHub Release 中的一个 APK 安装包资源 */
data class ApkAsset(
    /** 文件名，如 TransNote-1.3.0.apk */
    val name: String,
    /** 下载地址（browser_download_url） */
    val url: String,
    /** 文件大小（字节） */
    val size: Long
)

/** GitHub Release 更新信息 */
data class UpdateInfo(
    /** 版本号，如 1.3.0（tag 去掉 v 前缀） */
    val versionName: String,
    /** 原始 tag，如 v1.3.0 */
    val tag: String,
    /** Release 标题 */
    val releaseName: String,
    /** Release 说明（Markdown 原文，按纯文本展示） */
    val releaseNotes: String,
    /** 发布时间（ISO 8601） */
    val publishedAt: String,
    /** Release 附带的安装包列表 */
    val assets: List<ApkAsset>
)

/**
 * 应用内更新客户端：检查 GitHub Release 是否有新版、下载安装包并调起系统安装。
 *
 * 仅使用 `HttpURLConnection` 与系统 `org.json`，不引入额外网络库（与 [AmapApi] 一致）。
 * 检查接口：`GET https://api.github.com/repos/SongLiKod/trans-note/releases/latest`
 * （`latest` 只返回最新正式版，自动排除草稿与预发布）。
 */
object AppUpdater {

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/SongLiKod/trans-note/releases/latest"
    private const val USER_AGENT = "TransNote-Updater"
    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 30_000

    // ---------------------------------------------------------------- 版本比较

    /**
     * 比较两个语义化版本号：a > b 返回正数，相等返回 0，a < b 返回负数。
     * 按 `.` 分段数值比较，缺失段补 0；忽略 v 前缀与 `-beta` 之类后缀。
     */
    fun compareVersions(a: String, b: String): Int {
        val pa = parseVersion(a)
        val pb = parseVersion(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** 远程版本 [remote] 是否比当前版本 [current] 新 */
    fun isNewer(remote: String, current: String): Boolean = compareVersions(remote, current) > 0

    private fun parseVersion(version: String): List<Int> = version.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
        .split('.')
        .map { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    // ---------------------------------------------------------------- 资源挑选

    /**
     * 从 Release 资源中挑选可安装的候选 APK（按优先级升序）：
     * 排除 unsigned（系统无法安装未签名包）→ 正式包名精确匹配 → debug 精确匹配 → 其它正式包 → 其它包。
     *
     * 下载后会先做签名校验（见 [verifyApk]）：正式签名的用户装正式包命中第一候选；
     * 本地 debug 签名的用户装正式包校验失败，会自动顺延到 `-debug` 包。
     */
    fun selectApkAssets(version: String, assets: List<ApkAsset>): List<ApkAsset> {
        val pool = assets.filter {
            it.name.endsWith(".apk", ignoreCase = true) &&
                !it.name.contains("unsigned", ignoreCase = true)
        }
        if (pool.isEmpty()) return emptyList()
        val base = "TransNote-$version"
        fun rank(asset: ApkAsset): Int = when {
            asset.name.equals("$base.apk", ignoreCase = true) -> 0
            asset.name.equals("$base-debug.apk", ignoreCase = true) -> 1
            !asset.name.contains("debug", ignoreCase = true) -> 2
            else -> 3
        }
        return pool.sortedWith(compareBy({ rank(it) }, { it.name.lowercase() }))
    }

    // ---------------------------------------------------------------- 检查更新

    /**
     * 检查 GitHub 最新正式 Release 是否比 [currentVersion] 更新。
     *
     * 返回 `null` 表示已是最新；返回 [UpdateInfo] 表示有新版
     * （其 `assets` 可能没有 APK，此时不允许下载）。
     */
    suspend fun checkLatest(currentVersion: String): Result<UpdateInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = JSONObject(httpGet(LATEST_RELEASE_URL))
                val tag = json.optString("tag_name").trim()
                check(tag.isNotEmpty()) { "未找到版本标签" }
                val version = tag.removePrefix("v").removePrefix("V")
                if (!isNewer(version, currentVersion)) return@runCatching null
                UpdateInfo(
                    versionName = version,
                    tag = tag,
                    releaseName = json.optString("name").ifEmpty { tag },
                    releaseNotes = json.optString("body").orEmpty(),
                    publishedAt = json.optString("published_at"),
                    assets = parseAssets(json.optJSONArray("assets"))
                )
            }
        }

    private fun parseAssets(array: JSONArray?): List<ApkAsset> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val asset = array.optJSONObject(i) ?: return@mapNotNull null
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.isEmpty() || url.isEmpty() || !name.endsWith(".apk", ignoreCase = true)) null
            else ApkAsset(name, url, asset.optLong("size"))
        }
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("网络错误（HTTP $code）")
            text
        } finally {
            connection.disconnect()
        }
    }

    // ---------------------------------------------------------------- 下载安装包

    /**
     * 下载 [asset] 到应用缓存目录 `cacheDir/updates/`，返回目标文件。
     * [onProgress] 回调（已下载字节数, 总字节数；总长未知时为 -1）。
     */
    suspend fun download(
        context: Context,
        asset: ApkAsset,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, asset.name.substringAfterLast('/'))
            val tmp = File(dir, "${target.name}.part")
            val connection = (URL(asset.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/octet-stream")
            }
            var downloaded = 0L
            var declared = -1L
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("下载失败（HTTP $code）")
                declared = connection.contentLengthLong
                val total = if (declared > 0) declared else asset.size
                connection.inputStream.use { input ->
                    FileOutputStream(tmp).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            downloaded += n
                            onProgress(downloaded, total)
                        }
                        out.fd.sync()
                    }
                }
            } finally {
                connection.disconnect()
            }
            if (declared > 0 && downloaded != declared) {
                throw IllegalStateException("下载不完整（$downloaded/$declared）")
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            target
        }
    }

    // ---------------------------------------------------------------- 校验与安装

    /**
     * 校验 [apkFile] 是合法安装包且与当前应用签名一致，
     * 避免下载到不匹配的包后到系统安装阶段才报错。
     * 无法读取任一侧签名时放行（系统安装阶段仍会强制校验签名）。
     */
    @Suppress("DEPRECATION")
    fun verifyApk(context: Context, apkFile: File): Boolean = runCatching {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val pm = context.packageManager
        val installed = pm.getPackageInfo(context.packageName, flags)
        val archive = pm.getPackageArchiveInfo(apkFile.path, flags) ?: return false
        if (archive.packageName != context.packageName) return false
        val installedSigners = signerFingerprints(installed)
        val archiveSigners = signerFingerprints(archive)
        if (installedSigners == null || archiveSigners == null) return true
        installedSigners == archiveSigners
    }.getOrDefault(false)

    private fun signerFingerprints(info: PackageInfo): Set<String>? {
        val signingInfo = info.signingInfo ?: return null
        val signers = signingInfo.apkContentsSigners ?: return null
        return signers.map { sig ->
            sig.toByteArray().joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    /** 是否已获得「安装未知应用」授权（Android 8.0+） */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** 跳转系统「允许安装未知应用」授权页（仅本应用）的 Intent */
    fun installPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    /** 调起系统安装确认页；未获得安装授权时返回 false（先走 [installPermissionIntent]） */
    fun install(context: Context, apkFile: File): Boolean {
        if (!canInstall(context)) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    // ---------------------------------------------------------------- 展示辅助

    /** 字节数转人类可读大小，如 12.3 MB */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "${value.toLong()} B"
        else String.format(Locale.CHINA, "%.1f %s", value, units[unit])
    }

    /** ISO 8601 时间取日期部分，如 2026-10-17 */
    fun formatPublishDate(iso: String): String = if (iso.length >= 10) iso.substring(0, 10) else iso
}
