package com.transnote.traffic.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.transnote.traffic.utils.ThemeMode

val LocalTransNoteColors = androidx.compose.runtime.staticCompositionLocalOf { LightExtras }

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightTransferTagBg,
    onPrimaryContainer = LightPrimary,
    secondary = LightTextSecondary,
    onSecondary = Color.White,
    tertiary = LightWarning,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightBackground,
    onSurfaceVariant = LightTextSecondary,
    outline = LightDivider,
    outlineVariant = LightDivider,
    error = LightDanger,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color.White,
    primaryContainer = DarkTransferTagBg,
    onPrimaryContainer = DarkPrimary,
    secondary = DarkTextSecondary,
    onSecondary = DarkBackground,
    tertiary = DarkWarning,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkDivider,
    outlineVariant = DarkDivider,
    error = DarkDanger,
    onError = DarkBackground
)

/**
 * 扩展语义色：成功色 / 警示色 / 危险色 / 三级提示文字 / 接驳标签底色。
 * 通过 [TransNoteColors] 组合局部提供，组件中使用 `TransNoteTheme.colors` 获取。
 */
data class TransNoteColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val hintText: Color,
    val divider: Color,
    val navBar: Color,
    val ripple: Color,
    val transferTagBg: Color
)

private val LightExtras = TransNoteColors(
    success = LightSuccess,
    warning = LightWarning,
    danger = LightDanger,
    hintText = LightTextHint,
    divider = LightDivider,
    navBar = LightNavBar,
    ripple = LightRipple,
    transferTagBg = LightTransferTagBg
)

private val DarkExtras = TransNoteColors(
    success = DarkSuccess,
    warning = DarkWarning,
    danger = DarkDanger,
    hintText = DarkTextHint,
    divider = DarkDivider,
    navBar = DarkNavBar,
    ripple = DarkRipple,
    transferTagBg = DarkTransferTagBg
)

object TransNoteTheme {
    val colors: TransNoteColors
        @Composable get() = LocalTransNoteColors.current
}

/**
 * 全局主题。[themeMode] 三选一，切换后 Compose 自动重组，实时生效，无需重启 APP（PRD 3.6）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TransNoteTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (dark) DarkColors else LightColors
    val extras = if (dark) DarkExtras else LightExtras

    // 主题切换全局淡入淡出：对关键颜色做补间动画
    val animatedBackground = animateColorAsState(
        targetValue = colorScheme.background,
        animationSpec = tween(durationMillis = 300),
        label = "background"
    )
    val animatedPrimary = animateColorAsState(
        targetValue = colorScheme.primary,
        animationSpec = tween(durationMillis = 300),
        label = "primary"
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = animatedBackground.value.toArgb()
            window.navigationBarColor = animatedBackground.value.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalTransNoteColors provides extras,
        androidx.compose.material3.LocalRippleConfiguration provides
            androidx.compose.material3.RippleConfiguration(color = extras.ripple)
    ) {
        MaterialTheme(
            colorScheme = colorScheme.copy(
                background = animatedBackground.value,
                primary = animatedPrimary.value
            ),
            typography = TransNoteTypography,
            shapes = TransNoteShapes,
            content = content
        )
    }

    // 全局页面切换/列表渐入动画时长统一常量（PRD 4 动画）
    GlobalAnimation.durationMillis = 300
}

/** 全局动画配置（页面左右切换、列表渐入、主题淡入淡出） */
object GlobalAnimation {
    var durationMillis: Int = 300
}
