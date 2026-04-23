package com.henryxxiao.splash.ui.search

import android.app.Application
import android.content.Context
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// 继承 AndroidViewModel，生命周期与 MainActivity 绑定
class SharedSearchViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = RetrofitClient.apiService

    // ==========================================
    // 1. 会话池 (Session Pools)
    // ==========================================
    // 存储每个 Session 对应的搜索参数
    private val sessionParamsPool = LruCache<String, MutableStateFlow<SearchParams>>(10)

    // 存储每个 Session 对应的“滚回顶部”事件
    private val sessionScrollEvents = LruCache<String, MutableSharedFlow<Unit>>(10)

    // 用于缓存 Paging 数据流实例的池子
    private val sessionPhotosFlows = LruCache<String, Flow<PagingData<SplashPhoto>>>(10)
    private val sessionUsersFlows = LruCache<String, Flow<PagingData<SplashUser>>>(10)

    // 存储每个 Session 对应的总量
    val photoTotalCounts =  LruCache<String, MutableStateFlow<Int>>(10)
    val userTotalCounts =  LruCache<String, MutableStateFlow<Int>>(10)

    // LRU 安全获取与创建扩展
    // ==========================================
    fun getParamsFlow(sessionId: String): MutableStateFlow<SearchParams> {
        var flow = sessionParamsPool.get(sessionId)
        if (flow == null) {
            // 如果被 LRU 淘汰了，或者全新进入，就重新建一个
            flow = MutableStateFlow(SearchParams())
            sessionParamsPool.put(sessionId, flow)
        }
        return flow
    }

    fun getScrollEvent(sessionId: String): MutableSharedFlow<Unit> {
        var event = sessionScrollEvents.get(sessionId)
        if (event == null) {
            event = MutableSharedFlow()
            sessionScrollEvents.put(sessionId, event)
        }
        return event
    }
    // ==========================================
    // 2. 获取专属状态流
    // ==========================================
    fun getTotalPhotoCount(sessionId: String): MutableStateFlow<Int> {
        var count = photoTotalCounts.get(sessionId)
        if (count == null) {
            count = MutableStateFlow(0)
            photoTotalCounts.put(sessionId, count)
        }
        return count
    }

    fun getTotalUserCount(sessionId: String): MutableStateFlow<Int> {
        var count = userTotalCounts.get(sessionId)
        if (count == null) {
            count = MutableStateFlow(0)
            userTotalCounts.put(sessionId, count)
        }
        return count
    }

    // ==========================================
    // 3. 触发搜索与更新
    // ==========================================
    fun searchByQuery(sessionId: String, query: String) {
        val flow = getParamsFlow(sessionId)
        flow.value = flow.value.copy(query = query)
        viewModelScope.launch { getScrollEvent(sessionId).emit(Unit) }
    }

    fun updateFilters(sessionId: String, orderBy: String, color: String?, orientation: String?) {
        val flow = getParamsFlow(sessionId)
        flow.value = flow.value.copy(orderBy = orderBy, color = color, orientation = orientation)
        viewModelScope.launch { getScrollEvent(sessionId).emit(Unit) }
    }

    // ==========================================
    // 4. 专属 Paging 数据流生成器
    // ==========================================
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getPhotosFlow(sessionId: String): Flow<PagingData<SplashPhoto>> {
        var flow = sessionPhotosFlows.get(sessionId)
        if (flow == null) {
            // 如果池子里没有（被淘汰或新建），重新创建并放入池中
            flow = getParamsFlow(sessionId)
                .filter { it.query.isNotEmpty() }
                .flatMapLatest { params ->
                    Pager(PagingConfig(pageSize = 30, initialLoadSize = 30, enablePlaceholders = false)) {
                        SearchPhotoPagingSource(apiService, params.query, params.orderBy, params.color, params.orientation) { total ->
                            getTotalPhotoCount(sessionId).value = total
                        }
                    }.flow
                }
                .cachedIn(viewModelScope)

            sessionPhotosFlows.put(sessionId, flow)
        }
        return flow
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getUsersFlow(sessionId: String): Flow<PagingData<SplashUser>> {
        var flow = sessionUsersFlows.get(sessionId)
        if (flow == null) {
            flow = getParamsFlow(sessionId)
                .filter { it.query.isNotEmpty() }
                .flatMapLatest { params ->
                    Pager(PagingConfig(pageSize = 30, initialLoadSize = 30, enablePlaceholders = false)) {
                        SearchUserPagingSource(apiService, params.query) { total ->
                            getTotalUserCount(sessionId).value = total
                        }
                    }.flow
                }
                .cachedIn(viewModelScope)

            sessionUsersFlows.put(sessionId, flow)
        }
        return flow
    }

    // 全局精选主题 (Topics)
    // Topics 是全局唯一的，不跟 Session 走，作为单例缓存
    private val _topics = MutableStateFlow<List<SplashTopic>?>(null)
    val topics: StateFlow<List<SplashTopic>?> = _topics
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
            } catch (_: Exception) {}
        }
    }
}