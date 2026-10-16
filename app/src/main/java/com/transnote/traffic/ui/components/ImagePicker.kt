package com.transnote.traffic.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * 图片选择控制器：拍照 / 相册（PRD 3.1、3.2、3.3）。
 */
class ImagePickerController(
    val launchGallery: () -> Unit,
    val launchCamera: () -> Unit
)

/**
 * 统一的图片选择入口，自动处理相机权限与 FileProvider 输出 Uri。
 */
@Composable
fun rememberImagePicker(onImagePicked: (Uri) -> Unit): ImagePickerController {
    val context = LocalContext.current
    val pendingCameraUri = remember { mutableStateOf<Uri?>(null) }
    var pendingAfterPermission by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onImagePicked) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) pendingCameraUri.value?.let(onImagePicked) }

    val openCamera = {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "cap_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraUri.value = uri
        cameraLauncher.launch(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingAfterPermission) openCamera()
        pendingAfterPermission = false
    }

    return remember(galleryLauncher, cameraLauncher, permissionLauncher) {
        ImagePickerController(
            launchGallery = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            launchCamera = {
                val granted = ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.CAMERA
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (granted) {
                    openCamera()
                } else {
                    pendingAfterPermission = true
                    permissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
            }
        )
    }
}
