package com.henryxxiao.splash.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.henryxxiao.splash.data.SplashPhoto
import com.henryxxiao.splash.data.SplashTopic
import com.henryxxiao.splash.data.SplashUser
import com.henryxxiao.splash.repository.RetrofitClient
import com.henryxxiao.splash.repository.SearchPhotoPagingSource
import com.henryxxiao.splash.repository.SearchUserPagingSource
import com.henryxxiao.splash.utils.PhotoCacheManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

// 封装搜索参数
data class SearchParams(
    val query: String = "",
    val orderBy: String = "relevant",
    val color: String? = null,
    val orientation: String? = null
)

class SearchViewModel : ViewModel() {
    private val apiService = RetrofitClient.apiService

    // 搜索参数状态流
    // 赋初始默认值，不再是 null
    private val _searchParams = MutableStateFlow(SearchParams())
    val currentSearchParams: StateFlow<SearchParams?> = _searchParams
    // 两个 Tab 的总结果数状态流 (用于更新 UI)
    val photoTotalCount = MutableStateFlow(0)
    val userTotalCount = MutableStateFlow(0)

    private val _topics = MutableStateFlow<List<SplashTopic>?>(null)
    val topics: StateFlow<List<SplashTopic>?> = _topics

    // ==========================================
    // 独立修改“搜索词”
    // 使用 Kotlin data class 的 copy() ，只改 query，保留之前选好的筛选条件！
    // ==========================================
    fun searchByQuery(query: String) {
        _searchParams.value = _searchParams.value.copy(query = query)
    }
    // ==========================================
    // 修改“筛选条件”
    // 同样使用 copy()，只改条件，保留用户刚刚输入的 query
    // ==========================================
    fun updateFilters(orderBy: String, color: String?, orientation: String?) {
        _searchParams.value = _searchParams.value.copy(
            orderBy = orderBy,
            color = color,
            orientation = orientation
        )
    }

    /**
     * 照片搜索结果流
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val photosFlow: Flow<PagingData<SplashPhoto>> = _searchParams
        // 拦截器：只有当搜索词不为空时，才请求网络，用户可能先选条件，等带上条件发请求
        .filter { params -> params.query.isNotEmpty() }
        .flatMapLatest { params ->
            Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
                SearchPhotoPagingSource(apiService, params.query, params.orderBy, params.color, params.orientation) { total ->
                    photoTotalCount.value = total // 接收到总数，推给 UI
                }
            }.flow
        }.cachedIn(viewModelScope)

    /**
     * 用户搜索结果流
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val usersFlow: Flow<PagingData<SplashUser>> = _searchParams
        .filter { params -> params.query.isNotEmpty() }
        .flatMapLatest { params ->
            Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
                SearchUserPagingSource(apiService, params.query) { total ->
                    userTotalCount.value = total
                }
            }.flow
        }.cachedIn(viewModelScope)

    fun fetchTopics(context: Context) {
        // 防止重复拉取
        if (_topics.value != null) return

        viewModelScope.launch {
            // 第一层：找本地 JSON 缓存 (即使 App 重启也在！)
            val cachedTopics = PhotoCacheManager.getTopics(context)
            if (!cachedTopics.isNullOrEmpty()) {
                _topics.value = cachedTopics
                return@launch
            }

            // 第二层：本地没有，去网络请求聚合接口
            try {
                // apiService 需增加: @GET("topics") suspend fun getTopics(): List<SplashTopic>
                val networkTopics = apiService.getTopics()
                _topics.value = networkTopics
                PhotoCacheManager.saveTopics(context, networkTopics) // 存入本地，下次就不用请求了
            } catch (e: Exception) {}
        }
    }
}