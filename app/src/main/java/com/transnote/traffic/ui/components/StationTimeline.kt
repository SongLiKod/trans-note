package com.transnote.traffic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 站点行固定高度：保证左侧竖线在各行之间连贯 */
private val StationRowHeight = 72.dp

/**
 * 线路站点时间轴（地铁线路图样式，PRD 3.2 线路预览）：
 * 左侧一条竖直连线，每个站点绘制为一个圆点，换乘站点用实心大圆点高亮。
 *
 * @param index 当前站点序号（0 起）
 * @param total 站点总数
 * @param isTransfer 是否为地铁换乘站（圆点高亮）
 * @param lineColor 竖线与圆点颜色
 * @param isDragging 拖拽中的行（编辑模式）
 * @param isSelected 是否为当前选中站点（在地图上高亮跟随）
 * @param onClick 点击整行回调（为空则不可点击 = 只读展示）
 * @param trailing 行尾插槽（标签、删除按钮等）
 * @param content 站点文字内容
 */
@Composable
fun StationTimelineRow(
    index: Int,
    total: Int,
    isTransfer: Boolean,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    isDragging: Boolean = false,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val isFirst = index == 0
    val isLast = index == total - 1
    val railColor = lineColor.copy(alpha = 0.55f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = StationRowHeight)
            .background(
                when {
                    isSelected -> lineColor.copy(alpha = 0.12f)
                    isDragging -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                }
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // -------- 左侧轨道：竖线 + 圆点 --------
        Column(
            modifier = Modifier.width(42.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .weight(1f)
                    .background(if (isFirst) Color.Transparent else railColor)
            )
            if (isTransfer) {
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 22.dp else 20.dp)
                        .clip(CircleShape)
                        .background(lineColor)
                        .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 16.dp else 14.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) lineColor else MaterialTheme.colorScheme.surface)
                        .border(3.dp, lineColor, CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else railColor)
            )
        }

        // -------- 右侧站点文字 --------
        Column(modifier = Modifier.weight(1f)) { content() }

        trailing()
    }
}
