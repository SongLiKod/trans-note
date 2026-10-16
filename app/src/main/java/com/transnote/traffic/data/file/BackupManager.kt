package com.transnote.traffic.data.file

import android.content.Context
import android.net.Uri
import android.util.Log
import com.transnote.traffic.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 本地备份导入导出（TechDoc 5.6）：
 * - 导出：读取 Room 数据库文件 + APP 私有目录图片，压缩为 ZIP 包，保存到用户选择的本地文件；
 * - 导入：读取本地 ZIP，解压覆盖数据库和图片目录，全部为本地文件 IO 操作。
 * 全程离线，不含任何网络请求。
 */
class BackupManager(private val context: Context) {

    private val imageStorage = ImageStorage(context)
    private val dbFile: File get() = context.getDatabasePath(AppDatabase.DB_NAME)
    private val dbWal: File get() = File(dbFile.path + "-wal")
    private val dbShm: File get() = File(dbFile.path + "-shm")

    /** 建议的备份文件名 */
    fun suggestedBackupName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "TransNote_backup_$stamp.zip"
    }

    /**
     * 导出备份：数据库 + 图片 → ZIP。
     * @param destUri 用户通过系统文件选择器选定的目标文件 Uri
     */
    suspend fun exportBackup(destUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(destUri)
                ?: return@runCatching BackupResult.Failure("无法写入目标文件")

            var dbCount = 0
            var imageCount = 0

            ZipOutputStream(out.buffered()).use { zip ->
                // 1) 数据库文件（含 WAL 日志，保证数据一致）
                listOf(dbFile, dbWal, dbShm).forEach { file ->
                    if (file.exists()) {
                        zip.putNextEntry(ZipEntry(file.name))
                        FileInputStream(file).use { it.copyTo(zip) }
                        zip.closeEntry()
                        dbCount++
                    }
                }
                // 2) 备份元信息
                zip.putNextEntry(ZipEntry(META_ENTRY))
                zip.write(
                    """
                    {"app":"TransNote","package":"${context.packageName}","version":1,"createdAt":${System.currentTimeMillis()}}
                    """.trimIndent().toByteArray()
                )
                zip.closeEntry()

                // 3) 图片目录
                imageStorage.listAllImages().forEach { image ->
                    zip.putNextEntry(ZipEntry("$IMAGE_ENTRY/${image.name}"))
                    FileInputStream(image).use { it.copyTo(zip) }
                    zip.closeEntry()
                    imageCount++
                }
            }
            BackupResult.Success(dbCount, imageCount)
        }.onFailure { Log.e(TAG, "exportBackup failed", it) }
            .getOrElse { BackupResult.Failure(it.message ?: "导出失败") }
    }

    /**
     * 导入备份：解压 ZIP，覆盖数据库与图片目录。
     * 调用方需在此之前关闭 Room 连接（AppDatabase.close()）。
     * @param zipUri 用户选定的本地 ZIP 备份文件
     */
    suspend fun importBackup(zipUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val staging = File(context.cacheDir, "restore_staging").apply {
                deleteRecursively()
                mkdirs()
            }
            var hasDb = false
            var imageCount = 0

            context.contentResolver.openInputStream(zipUri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        when {
                            name.startsWith("images/") && !entry.isDirectory -> {
                                val target = File(staging, name)
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { zip.copyTo(it) }
                                imageCount++
                            }
                            name == dbFile.name || name == dbWal.name || name == dbShm.name -> {
                                val target = File(staging, name)
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { zip.copyTo(it) }
                                if (name == dbFile.name) hasDb = true
                            }
                            name == META_ENTRY -> { /* 元信息，忽略 */ }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: return@runCatching BackupResult.Failure("无法读取备份文件")

            if (!hasDb) {
                staging.deleteRecursively()
                return@runCatching BackupResult.Failure("备份文件无效：缺少数据库")
            }

            // ---- 覆盖数据库 ----
            dbWal.delete()
            dbShm.delete()
            dbFile.delete()
            File(staging, dbFile.name).copyTo(dbFile, overwrite = true)
            File(staging, dbWal.name).takeIf { it.exists() }?.copyTo(dbWal, overwrite = true)
            File(staging, dbShm.name).takeIf { it.exists() }?.copyTo(dbShm, overwrite = true)

            // ---- 覆盖图片目录 ----
            imageStorage.imagesDir.listFiles()?.forEach { it.delete() }
            File(staging, "images").takeIf { it.exists() }?.listFiles()?.forEach { img ->
                img.copyTo(File(imageStorage.imagesDir, img.name), overwrite = true)
            }

            staging.deleteRecursively()
            BackupResult.Success(if (hasDb) 1 else 0, imageCount)
        }.onFailure { Log.e(TAG, "importBackup failed", it) }
            .getOrElse { BackupResult.Failure(it.message ?: "导入失败") }
    }

    /** 备份包中的元信息条目 */
    sealed interface BackupResult {
        data class Success(val dbFiles: Int, val imageCount: Int) : BackupResult
        data class Failure(val message: String) : BackupResult
    }

    companion object {
        private const val TAG = "BackupManager"
        private const val META_ENTRY = "backup_info.json"
        private const val IMAGE_ENTRY = "images"
    }
}
