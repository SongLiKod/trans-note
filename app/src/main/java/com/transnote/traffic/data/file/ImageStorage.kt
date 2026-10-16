package com.transnote.traffic.data.file

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 图片读写工具（TechDoc 5.3）：
 * 图片保存到 APP 私有存储目录（filesDir/images），数据库仅保存图片文件路径。
 */
class ImageStorage(private val context: Context) {

    /** App 私有图片目录 */
    val imagesDir: File
        get() = File(context.filesDir, "images").apply { if (!exists()) mkdirs() }

    /** 从相册/相机 Uri 复制图片到私有目录，返回相对文件名 */
    suspend fun saveImageFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "img_${UUID.randomUUID()}.jpg"
            val target = File(imagesDir, fileName)
            context.contentResolver.openInputStream(uri)!!.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            if (target.length() == 0L) {
                target.delete()
                null
            } else {
                fileName
            }
        }.onFailure { Log.e(TAG, "saveImageFromUri failed", it) }.getOrNull()
    }

    /** 从已有文件复制到私有目录 */
    suspend fun saveImageFromFile(file: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@runCatching null
            val fileName = "img_${UUID.randomUUID()}.jpg"
            val target = File(imagesDir, fileName)
            file.copyTo(target, overwrite = true)
            fileName
        }.onFailure { Log.e(TAG, "saveImageFromFile failed", it) }.getOrNull()
    }

    fun imageFile(path: String): File = File(imagesDir, path)

    /** 解析图片绝对路径（兼容历史绝对路径与相对文件名） */
    fun resolve(path: String): File =
        if (path.startsWith("/")) File(path) else File(imagesDir, path)

    fun exists(path: String): Boolean = resolve(path).exists()

    suspend fun deleteImage(path: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { resolve(path).takeIf { it.exists() }?.delete() ?: true }
            .getOrDefault(false)
    }

    /** 列出全部图片文件（备份用） */
    fun listAllImages(): List<File> =
        imagesDir.listFiles()?.filter { it.isFile } ?: emptyList()

    /** 备份包中的图片解压目标目录 */
    val backupImagesEntry: String get() = "images"
    val backupDbEntry: String get() = "transnote.db"

    companion object {
        private const val TAG = "ImageStorage"
    }
}
