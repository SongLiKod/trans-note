package com.transnote.traffic.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.transnote.traffic.utils.CoordinateUtils
import com.transnote.traffic.ui.theme.TransNoteTheme

/**
 * 地图选点弹窗（PRD 3.2 / 3.3）：在地图上点击选择经纬度后确认。
 */
@Composable
fun MapPickerDialog(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onConfirm: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
    defaultCenter: Pair<Double, Double>? = null
) {
    var picked by remember {
        mutableStateOf(
            if (initialLatitude != null && initialLongitude != null) initialLatitude to initialLongitude
            else null
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("选择地图位置", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                LocationPickerMap(
                    initialLatitude = initialLatitude,
                    initialLongitude = initialLongitude,
                    onPick = { lat, lng -> picked = lat to lng },
                    defaultCenter = defaultCenter,
                    modifier = Modifier.fillMaxWidth().height(320.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    picked?.let { CoordinateUtils.format(it.first, it.second) } ?: "尚未选点",
                    style = MaterialTheme.typography.bodySmall,
                    color = TransNoteTheme.colors.hintText
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = TransNoteTheme.colors.hintText)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            picked?.let { (lat, lng) -> onConfirm(lat, lng) }
                            onDismiss()
                        }
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}
