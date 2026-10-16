package com.transnote.traffic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.transnote.traffic.ui.TransNoteRoot
import com.transnote.traffic.ui.theme.TransNoteTheme
import com.transnote.traffic.viewmodel.ThemeViewModel
import kotlinx.coroutines.launch

/**
 * APP 入口（TechDoc 第 6 节）：挂载 Compose，应用全局主题。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as TransNoteApp
            val themeViewModel: ThemeViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { ThemeViewModel(app.container.themeManager) }
                }
            )
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            // 主题切换全局淡入淡出，实时生效
            TransNoteTheme(themeMode = themeMode) {
                TransNoteRoot(
                    container = app.container,
                    themeMode = themeMode,
                    onThemeChange = { mode ->
                        scope.launch { app.container.themeManager.setThemeMode(mode) }
                    }
                )
            }
        }
    }
}

/** 供 ViewModel 工厂获取 Application 容器 */
val CreationExtras.appContainer: AppContainer
    get() = (this[APPLICATION_KEY] as TransNoteApp).container
