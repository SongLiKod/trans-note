package com.transnote.traffic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.transnote.traffic.AppContainer
import com.transnote.traffic.appContainer

/**
 * ViewModel 工厂（TechDoc 第 3 节 MVVM）：从全局容器注入本地仓库。
 */
inline fun <reified VM : ViewModel> containerViewModelFactory(
    crossinline create: (AppContainer) -> VM
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        create((this as CreationExtras).appContainer)
    }
}
