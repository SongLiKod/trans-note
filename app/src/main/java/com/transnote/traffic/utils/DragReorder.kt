package com.transnote.traffic.utils

/**
 * 拖拽排序工具（PRD 3.2 / TechDoc 5.2：拖拽调整站点顺序）。
 */
object DragReorder {

    /** 将 [from] 位置元素移动到 [to] 位置，返回新列表 */
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from == to) return items
        if (from !in items.indices || to !in items.indices) return items
        val mutable = items.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        return mutable
    }

    /**
     * 根据拖拽偏移量计算目标下标（列表项等高的简单实现）。
     * @param currentIndex 当前项下标
     * @param dragOffsetY 拖拽的 Y 方向偏移（像素）
     * @param itemHeight 单项高度（像素）
     */
    fun targetIndex(currentIndex: Int, dragOffsetY: Float, itemHeight: Float, itemCount: Int): Int {
        if (itemHeight <= 0f) return currentIndex
        val shift = Math.round(dragOffsetY / itemHeight)
        return (currentIndex + shift).coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    }
}
