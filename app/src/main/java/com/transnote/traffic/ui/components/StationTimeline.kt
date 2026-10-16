package com.transnote.traffic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 站点行最小高度：保证左侧竖线在各行之间连贯 */
private val StationRowHeight = 72.dp

/** 站名首行的垂直中心，用于让左侧圆点与站名对齐 */
private val FirstLineCenter = 15.dp

/**
 * 线路站点时间轴（地铁线路图样式，PRD 3.2 线路预览）：
 * 左侧一条竖直连线，每个站点绘制为一个圆点，换乘站点用实心大圆点高亮。
 *
 * 注意：行高使用 `height(IntrinsicSize.Min)` 由内容决定，行内的连线使用 `weight` 填满剩余空间，
 * 因此相邻两行的连线可以首尾相接。若改用 `heightIn`（高度无上界）会使带权重的连线高度塌缩为 0，
 * 连线就会消失。
 *
 * @param index 当前站点序号（0 起）
 * @param total 站点总数
 * @param isTransfer 是否为地铁换乘站（圆点高亮）
 * @param lineColor 连线颜色
 * @param pointColor 当前站点圆点颜色
 * @param railWidth 连线粗细
 * @param dotSize 站点圆点直径
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
    pointColor: Color = lineColor,
    railWidth: Dp = 4.dp,
    dotSize: Dp = 14.dp,
    isDragging: Boolean = false,
    isSelected: Boolean = false,
    /** 是否在小圆点内显示站点序号（可在设置中开关） */
    showIndex: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val isFirst = index == 0
    val isLast = index == total - 1
    // 圆点尺寸随配置变化：换乘站更大，选中态略放大
    val dotBorder = (dotSize.value * 0.25f).coerceIn(2f, 6f).dp
    // 圆点直径：换乘站略大；选中态不改圆点大小，仅在外部加高亮圆环，保证序号始终居中
    val dotDiameter = if (isTransfer) dotSize + 6.dp else dotSize

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
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
        // -------- 左侧轨道：竖线 + 圆点（圆点与站名首行对齐） --------
        Column(
            modifier = Modifier.width(42.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 上段连线：固定高度使圆点中心对齐站名首行的中心，而不是行中
            Box(
                modifier = Modifier
                    .width(railWidth)
                    .height((FirstLineCenter - dotDiameter / 2).coerceAtLeast(0.dp))
                    .background(if (isFirst) Color.Transparent else lineColor)
            )
            // 圆点：尺寸恒定（选中只在外部画高亮圆环，不改变布局），序号始终居中
            Box(
                modifier = Modifier
                    .size(dotDiameter)
                    .then(
                        if (isSelected) Modifier.drawBehind {
                            drawCircle(
                                color = lineColor,
                                radius = size.minDimension / 2f + 4.dp.toPx(),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        } else Modifier
                    )
                    .clip(CircleShape)
                    .background(
                        when {
                            showIndex -> pointColor
                            isTransfer -> pointColor
                            isSelected -> pointColor
                            else -> MaterialTheme.colorScheme.surface
                        }
                    )
                    .then(
                        when {
                            showIndex -> Modifier
                            isTransfer -> Modifier.border(
                                dotBorder, MaterialTheme.colorScheme.surface, CircleShape
                            )
                            else -> Modifier.border(dotBorder, pointColor, CircleShape)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (showIndex) {
                    IndexLabel(index + 1, pointColor, dotDiameter)
                }
            }
            Box(
                modifier = Modifier
                    .width(railWidth)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else lineColor)
            )
        }

        // -------- 右侧站点文字 --------
        Column(modifier = Modifier.weight(1f).defaultMinSize(minHeight = StationRowHeight)) { content() }

        trailing()
    }
}

/**
 * 圆点内的站点序号。去掉字体额外行距（includeFontPadding=false）并令行高等字号，
 * 使数字在圆内水平、垂直都真正居中（字号随圆点大小缩放，颜色按底色明暗取黑/白）。
 */
@Composable
private fun IndexLabel(number: Int, background: Color, dotSize: Dp) {
    val textColor = if (background.luminance() > 0.6f) Color(0xFF1F2937) else Color.White
    val size = (dotSize.value * 0.5f).coerceIn(8f, 13f).sp
    Text(
        text = "$number",
        color = textColor,
        fontSize = size,
        lineHeight = size,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        textAlign = TextAlign.Center,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
    )
}
