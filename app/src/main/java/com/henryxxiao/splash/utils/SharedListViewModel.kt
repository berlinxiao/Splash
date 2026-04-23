package com.henryxxiao.splash.utils

import android.app.Application
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.henryxxiao.splash.data.SplashPhoto
import com.henryxxiao.splash.repository.LoadPhotoPagingSource
import com.henryxxiao.splash.repository.RetrofitClient
import kotlinx.coroutines.flow.Flow

/**
 * 通用列表
 * 负责管理所有基于 endpoint（路由后缀）的瀑布流列表
 */
class SharedListViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = RetrofitClient.apiService

    // ==========================================
    // LRU 全局流缓存池
    // 容量设定为 15。意味着能同时保持 15 个不同的列表进度！
    // 比如：用户的 Photos、用户的 Likes、3 个不同 Topic 的列表...
    // 超过 15 个最老的流会被自动销毁，完美控制内存
    // ==========================================

    // 以 API 路由 (endpoint) 为 Key 的全局流缓存池
    // 例如 Key: "topics/wallpapers/photos" 或 "users/henry/likes"
    private val endpointFlowsPool = LruCache<String, Flow<PagingData<SplashPhoto>>>(15)

    fun getPhotosFlowByEndpoint(endpoint: String): Flow<PagingData<SplashPhoto>> {
        // 尝试从 LRU 缓存中获取已存在的流
        var flow = endpointFlowsPool.get(endpoint)

        // 双重检查：如果被淘汰了，或者全新进入，就重新建一个
        if (flow == null) {
            flow = Pager(
                config = PagingConfig(
                    pageSize = 30,
                    initialLoadSize = 30,
                    enablePlaceholders = false
                )
            ) {
                // 实例化 PagingSource
                LoadPhotoPagingSource(apiService, endpoint)
            }.flow.cachedIn(viewModelScope) // 缓存与 Activity 同寿

            // 放入 LRU 缓存池
            endpointFlowsPool.put(endpoint, flow)
        }

        return flow
    }
}