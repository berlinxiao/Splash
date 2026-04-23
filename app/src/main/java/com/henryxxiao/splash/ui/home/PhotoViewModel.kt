package com.henryxxiao.splash.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.henryxxiao.splash.data.SplashPhoto
import com.henryxxiao.splash.repository.LoadPhotoPagingSource
import com.henryxxiao.splash.repository.RetrofitClient
import com.henryxxiao.splash.utils.settings.LoadType
import com.henryxxiao.splash.utils.settings.SettingsManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * 负责连接 UI 与数据源的 ViewModel
 */
class PhotoViewModel(application: Application) : AndroidViewModel(application){

    // 1. 初始化 DataStore 设置管理器
    private val settingsManager = SettingsManager(application)

    // 2. 获取 API Service
    private val apiService = RetrofitClient.apiService

    /**
     * 获取照片分页数据流 (Flow)
     * UI 层（Activity/Fragment）只需要 collect 这个 photosFlow 即可
     *  @OptIn(ExperimentalCoroutinesApi::class) 是因为 flatMapLatest 属于协程的高级 API
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val photosFlow: Flow<PagingData<SplashPhoto>> = settingsManager.loadTypeFlow
        .flatMapLatest { loadType ->
            // 将 DataStore 里的 Int 转换为对应的 API 路径
            val endpoint = when (loadType) {
                LoadType.NEWEST -> "photos"
                LoadType.POPULAR -> "collections/1459961/photos"
                //else -> "collections/317099/photos"
            }

            // 每次 endpoint 改变，都会生成一个全新的 Pager
            Pager(
                config = PagingConfig(
                    pageSize = 30,
                    initialLoadSize = 30,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = {
                    LoadPhotoPagingSource(apiService, endpoint)
                }
            ).flow
        }
        .cachedIn(viewModelScope) // 保证横竖屏切换时数据不会丢失，不会重新请求网络
}