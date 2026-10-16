package com.transnote.traffic.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.transnote.traffic.ui.theme.TransNoteTheme
import com.transnote.traffic.ui.theme.TransNoteTypography
import java.io.File

/** 通用卡片（PRD 4：卡片圆角 12dp） */
@Composable
fun TransNoteCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = TransNoteTheme.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, colors.divider)
    ) {
        Box(Modifier.padding(12.dp)) { content() }
    }
}

/** 分组小标题（PRD 4：全局外间距 16dp） */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(top = 4.dp, bottom = 8.dp)
    )
}

/** 标签/徽章 */
@Composable
fun Tag(
    text: String,
    background: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Surface(
        modifier = modifier,
        color = background,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(2.dp))
            }
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = textColor)
        }
    }
}

/** 地铁换乘标签：高亮显示【地铁换乘】（PRD 3.2.3） */
@Composable
fun TransferTag(modifier: Modifier = Modifier) {
    val colors = TransNoteTheme.colors
    Tag(
        text = "地铁换乘",
        background = colors.transferTagBg,
        textColor = MaterialTheme.colorScheme.primary,
        icon = Icons.Filled.DirectionsSubway,
        modifier = modifier
    )
}

/**
 * 换乘站两线颜色徽标：圆形左半为当前线路色、右半为换乘线路色
 * （参照 docs/temp/pic1、pic2 的地铁换乘图标样式）。
 */
@Composable
fun TransferColorBadge(
    firstColor: Color,
    secondColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
    ) {
        drawArc(firstColor, startAngle = 90f, sweepAngle = 180f, useCenter = true, size = this.size)
        drawArc(secondColor, startAngle = 270f, sweepAngle = 180f, useCenter = true, size = this.size)
    }
}

/** 空态占位 */
@Composable
fun EmptyState(
    title: String,
    hint: String = "",
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = TransNoteTheme.colors.hintText,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(title, style = MaterialTheme.typography.bodyLarge, color = TransNoteTheme.colors.hintText)
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = TransNoteTheme.colors.hintText,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/** 带标题的输入框 */
@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String = "",
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder, color = TransNoteTheme.colors.hintText) }
        } else null,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small
    )
}

/** 确认弹窗（PRD 4：弹窗圆角 16dp） */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmText, color = if (danger) TransNoteTheme.colors.danger else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText, color = TransNoteTheme.colors.hintText)
            }
        }
    )
}

/** 信息行 */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TransNoteTheme.colors.hintText,
            modifier = Modifier.width(84.dp)
        )
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 统一样式顶部栏 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransNoteTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                title,
                style = TransNoteTypography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = TransNoteTheme.colors.navBar,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/** 分割线（引用主题分割线色） */
@Composable
fun ThemedDivider(modifier: Modifier = Modifier) {
    Divider(modifier = modifier, color = TransNoteTheme.colors.divider)
}

/**
 * 图片网格：展示站点/出入口实景图片，支持新增、删除、点击预览（PRD 3.2）。
 */
@Composable
fun ImageGrid(
    paths: List<String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxVisible: Int = 9
) {
    var previewPath by remember { mutableStateOf<String?>(null) }
    val shown = paths.take(maxVisible)

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth().height(((shown.size + 2) / 3 * 96).dp.coerceAtLeast(96.dp)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false
    ) {
        itemsIndexed(shown) { _, path ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { previewPath = path }
            ) {
                AsyncImage(
                    model = File(path),
                    contentDescription = "站点图片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { onRemove(path) },
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "删除图片",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Add, contentDescription = "添加图片", tint = TransNoteTheme.colors.hintText)
                    Text("添加", style = MaterialTheme.typography.bodySmall, color = TransNoteTheme.colors.hintText)
                }
        }
    }
}

    previewPath?.let { path ->
        Dialog(onDismissRequest = { previewPath = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = File(path),
                    contentDescription = "图片预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** 列表项右侧危险操作按钮 */
@Composable
fun DeleteIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = "删除",
            tint = TransNoteTheme.colors.danger
        )
    }
}

/** 危险操作卡片样式（删除按钮） */
val dangerCardColors
    @Composable get() = CardDefaults.outlinedCardColors(
        containerColor = MaterialTheme.colorScheme.surface
    )

/** 文本强调 */
@Composable
fun StrongText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

/**
 * 时刻表内容：把发车时间拆分为时间胶囊展示；若为整段文字则原样展示。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TimetableContent(text: String, modifier: Modifier = Modifier) {
    val tokens = text.split(Regex("[\\s,，、;；\\n]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (tokens.size <= 1) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier.fillMaxWidth()
        )
    } else {
        androidx.compose.foundation.layout.FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tokens.forEach { token ->
                Surface(
                    color = TransNoteTheme.colors.transferTagBg,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        token,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * 紧凑图标按钮：比默认 [IconButton] 更小，用于列表行尾，
 * 让中间内容（站名 / 备注）有更多展示空间。
 */
@Composable
fun CompactIconButton(
    onClick: () -> Unit,
    contentDescription: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    IconButton(onClick = onClick, modifier = modifier.size(34.dp)) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
