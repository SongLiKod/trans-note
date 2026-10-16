package com.transnote.traffic.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.transnote.traffic.ui.theme.LineColors
import com.transnote.traffic.ui.theme.TransNoteTheme

/**
 * 颜色选择器：横向色板 + 可选「跟随线路 / 默认」项。
 *
 * @param label 顶部说明文字
 * @param selectedHex 当前选中颜色（空串表示继承）
 * @param onSelect 选中某个预设颜色的回调
 * @param inheritLabel 继承项文案（为空则不显示继承项）
 * @param onInherit 点击继承项的回调
 */
@Composable
fun ColorPickerRow(
    label: String,
    selectedHex: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    inheritLabel: String? = null,
    onInherit: (() -> Unit)? = null
) {
    val effective = if (selectedHex.isBlank()) "" else "#" + selectedHex.trim().removePrefix("#").uppercase()

    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TransNoteTheme.colors.hintText
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (inheritLabel != null && onInherit != null) {
                item {
                    val selected = effective.isBlank()
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            TransNoteTheme.colors.navBar
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else TransNoteTheme.colors.divider
                        ),
                        modifier = Modifier.clickable { onInherit() }
                    ) {
                        Text(
                            inheritLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                }
            }
            items(LineColors.palette) { hex ->
                ColorSwatch(
                    hex = hex,
                    selected = effective.equals(hex, ignoreCase = true),
                    onClick = { onSelect(hex) }
                )
            }
        }
    }
}

/** 单个颜色圆点（选中时描边并显示对勾） */
@Composable
fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val color = LineColors.parse(hex, MaterialTheme.colorScheme.primary)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else TransNoteTheme.colors.divider,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "已选颜色",
                tint = LineColors.onColor(color),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 站点行上的小颜色点，点击可设置该站点颜色 */
@Composable
fun StationColorDot(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, TransNoteTheme.colors.divider, CircleShape)
            .clickable(onClick = onClick)
    )
}
