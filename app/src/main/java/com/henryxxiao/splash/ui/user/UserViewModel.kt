package com.henryxxiao.splash.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.henryxxiao.splash.data.SplashPhoto
import com.henryxxiao.splash.repository.LoadPhotoPagingSource
import com.henryxxiao.splash.repository.RetrofitClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest

/**
 * 个人主页专用的 ViewModel
 * 职责：接收特定用户名/端点，提供该用户的照片流
 */
class UserViewModel : ViewModel() {

    private val apiService = RetrofitClient.apiService

    // 状态流：记录当前的加载路径
    private val endpointFlow = MutableStateFlow<String?>(null)

    /**
     * 触发加载，传入要加载的 type 路径，例如调用时传入： "users/milad/photos"
     */
    fun loadUserPhotos(type: String) {
        endpointFlow.value = type
    }

    /**
     * 核心 Paging 数据流 只有当 endpointFlow 收到了真实的值（不是null）时才开始触发网络请求。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val photosFlow: Flow<PagingData<SplashPhoto>> = endpointFlow
        .filterNotNull() // 过滤掉初始的空值
        .flatMapLatest { endpoint ->
            Pager(
                config = PagingConfig(pageSize = 10, enablePlaceholders = false),
                pagingSourceFactory = { LoadPhotoPagingSource(apiService, endpoint) }
            ).flow
        }
        .cachedIn(viewModelScope)
}