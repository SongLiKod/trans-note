package com.transnote.traffic.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 应用内更新工具单元测试：版本比较、Release 安装包挑选、大小格式化。 */
class AppUpdaterTest {

    private fun asset(name: String) = ApkAsset(name, "https://example.com/$name", 1_000L)

    // ------------------------------------------------------------- 版本比较

    @Test
    fun versionsCompareNumericallyNotLexically() {
        assertTrue(AppUpdater.compareVersions("1.10.0", "1.9.0") > 0)
        assertTrue(AppUpdater.compareVersions("1.2.0", "1.10.0") < 0)
        assertEquals(0, AppUpdater.compareVersions("1.2.0", "1.2.0"))
    }

    @Test
    fun vPrefixIsIgnored() {
        assertTrue(AppUpdater.isNewer("v1.3.0", "1.2.0"))
        assertTrue(AppUpdater.isNewer("V1.3.0", "1.2.0"))
        assertFalse(AppUpdater.isNewer("v1.2.0", "1.2.0"))
    }

    @Test
    fun prereleaseSuffixIsIgnored() {
        assertEquals(0, AppUpdater.compareVersions("1.2.0-beta", "1.2.0"))
        assertTrue(AppUpdater.isNewer("1.3.0-rc.1", "1.2.9"))
    }

    @Test
    fun equalOrOlderIsNotNewer() {
        assertFalse(AppUpdater.isNewer("1.2.0", "1.2.0"))
        assertFalse(AppUpdater.isNewer("1.1.9", "1.2.0"))
        assertFalse(AppUpdater.isNewer("", "1.0.0"))
    }

    // ------------------------------------------------------------- 安装包挑选

    @Test
    fun prefersExactSignedReleaseAsset() {
        val assets = listOf(
            asset("TransNote-1.3.0-debug.apk"),
            asset("TransNote-1.3.0-unsigned.apk"),
            asset("TransNote-1.3.0.apk"),
            asset("TransNote-1.3.0-notes.txt")
        )
        val candidates = AppUpdater.selectApkAssets("1.3.0", assets)
        assertEquals("TransNote-1.3.0.apk", candidates.first().name)
        // 未签名包被排除，非 APK 文件被排除
        assertEquals(2, candidates.size)
        assertTrue(candidates.none { it.name.contains("unsigned") })
    }

    @Test
    fun fallsBackToDebugAsset() {
        val assets = listOf(
            asset("TransNote-1.3.0-unsigned.apk"),
            asset("TransNote-1.3.0-debug.apk")
        )
        val candidates = AppUpdater.selectApkAssets("1.3.0", assets)
        assertEquals(listOf("TransNote-1.3.0-debug.apk"), candidates.map { it.name })
    }

    @Test
    fun returnsEmptyWhenNoApkAsset() {
        assertTrue(AppUpdater.selectApkAssets("1.3.0", emptyList()).isEmpty())
        assertTrue(
            AppUpdater.selectApkAssets("1.3.0", listOf(asset("TransNote-1.3.0.txt"))).isEmpty()
        )
    }

    // ------------------------------------------------------------- 展示辅助

    @Test
    fun formatBytesProducesReadableUnits() {
        assertEquals("0 B", AppUpdater.formatBytes(0))
        assertEquals("512 B", AppUpdater.formatBytes(512))
        assertEquals("1.0 KB", AppUpdater.formatBytes(1024))
        assertEquals("1.5 KB", AppUpdater.formatBytes(1536))
        assertEquals("1.0 MB", AppUpdater.formatBytes(1024L * 1024))
    }

    @Test
    fun publishDateTrimsToDay() {
        assertEquals("2026-10-17", AppUpdater.formatPublishDate("2026-10-17T03:00:00Z"))
        assertEquals("2026-10", AppUpdater.formatPublishDate("2026-10"))
        assertEquals("", AppUpdater.formatPublishDate(""))
    }
}
