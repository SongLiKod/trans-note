package com.transnote.traffic.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex

/**
 * 可拖拽排序列表（PRD 3.2 / TechDoc 5.2：拖拽调整站点顺序）。
 * 长按条目后上下拖动即可改变顺序。
 */
@Composable
fun <T> DragReorderColumn(
    items: List<T>,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (index: Int, item: T, isDragging: Boolean) -> Unit
) {
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            val isDragging = index == draggingIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                    .onSizeChanged { size ->
                        if (itemHeight <= 0f && size.height > 0) itemHeight = size.height.toFloat()
                    }
                    .pointerInput(items.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val h = itemHeight
                                if (h > 0f) {
                                    if (dragOffset > h / 2f && draggingIndex < items.lastIndex) {
                                        val from = draggingIndex
                                        onMove(from, from + 1)
                                        draggingIndex = from + 1
                                        dragOffset -= h
                                    } else if (dragOffset < -h / 2f && draggingIndex > 0) {
                                        val from = draggingIndex
                                        onMove(from, from - 1)
                                        draggingIndex = from - 1
                                        dragOffset += h
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingIndex = -1
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggingIndex = -1
                                dragOffset = 0f
                            }
                        )
                    }
            ) {
                itemContent(index, item, isDragging)
            }
        }
    }
}
