package com.transnote.traffic.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角规范（PRD 4）：卡片 12dp，按钮 8dp，弹窗 16dp。
 */
val TransNoteShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),      // 按钮
    medium = RoundedCornerShape(12.dp),    // 卡片
    large = RoundedCornerShape(16.dp),     // 弹窗
    extraLarge = RoundedCornerShape(16.dp)
)
