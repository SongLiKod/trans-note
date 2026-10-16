package com.transnote.traffic.utils

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR 工具（TechDoc 5.2）：ML Kit 离线中文文字识别，本地识别，图片不上传。
 */
class OcrHelper {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /** 识别图片中的文字，返回按行拆分的文本列表 */
    suspend fun recognize(context: Context, uri: Uri): List<String> =
        suspendCancellableCoroutine { cont ->
            val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrElse {
                cont.resumeWithException(it)
                return@suspendCancellableCoroutine
            }
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val lines = mutableListOf<String>()
                    for (block in result.textBlocks) {
                        for (line in block.lines) {
                            val t = line.text.trim()
                            if (t.isNotEmpty()) lines.add(t)
                        }
                    }
                    cont.resume(lines)
                }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    fun close() {
        recognizer.close()
    }
}
