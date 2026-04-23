package com.henryxxiao.splash.utils

import android.app.Application
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.henryxxiao.splash.data.SplashPhoto
import com.henryxxiao.splash.data.SplashTopic
import com.henryxxiao.splash.data.SplashUser
import com.henryxxiao.splash.repository.RetrofitClient.apiService
import kotlinx.coroutines.launch

/**
 * 全局共享的 ViewModel，继承 AndroidViewModel，这样它自带全局 Application Context
 */
class SharedPhotoViewModel(application: Application) : AndroidViewModel(application) {

    //  L1 缓存：内存级 HashMap， 缓存池 (ConcurrentHashMap 保证线程安全)
    // 看过的图的 Exif等信息 存在这里，减少网络请求，App关闭后清空。
    private val detailCachePool = LruCache<String, SplashPhoto>(30)

    // 内存对象池！以 photoId 为 Key，SplashPhoto 为 Value
    private val photoMemoryPool = LruCache<String, SplashPhoto>(50)
    // 用户专属的内存池
    private val userMemoryPool = LruCache<String, SplashUser>(50)

    // Topic 专属内存池
    private val topicMemoryPool = LruCache<String, SplashTopic>(10)

    fun cacheTopicToPool(topic: SplashTopic) {
        topicMemoryPool.put(topic.id, topic)
    }

    fun getTopicFromPool(topicId: String): SplashTopic? {
        return topicMemoryPool.get(topicId)
    }

    /**
     * 在 Home、UserList、Search 列表点击时，把大对象存入池中
     */
    fun cachePhotoToPool(photo: SplashPhoto) {
        photoMemoryPool.put(photo.id, photo)
    }

    /**
     * 在 ShowFragment 或 UserFragment 重建时，通过 ID 取回自己的专属数据
     */
    fun getPhotoFromPool(photoId: String): SplashPhoto? {
        return photoMemoryPool.get(photoId)
    }

    /**
     * 在 ShowFragment 中调用，拉取详细信息
     * 获取详情的核心逻辑：完美融合 L1内存、L2磁盘、L3网络 三层架构！
     */
    // 通过回调返回数据，或者针对每个 ID 独立观察（这里提供挂起函数直接返回的方式，最适合 Fragment 独立调用）
    suspend fun fetchPhotoDetailsNow(photoId: String): SplashPhoto? {
        // [第 1 层]：如果 L1缓存池里有，直接拦截，不再发网络请求
        // 【第 1 级缓存：L1 内存】 (LRU Cache)
        // 耗时 0 毫秒。如果用户刚刚看过这张图并按了返回，直接从内存扣出返回，不产生任何开销
        val l1CachedPhoto = detailCachePool.get(photoId)
        if (l1CachedPhoto != null) {
            return l1CachedPhoto
        }

        val context = getApplication<Application>()
        // [第 2 层]：从 L2 磁盘缓存读取 (即使重启 App 也会走到这里)
        val diskCached = PhotoCacheManager.getPhotoDetail(context, photoId)
        if (diskCached != null) {
            detailCachePool.put(photoId, diskCached)
            return diskCached
        }
        // [第 3 层]：L1 和 L2 全都没有，发起网络请求
        return try {
            // 请求成功后，不仅要推给 UI，还要双重缓存！
            val detail = apiService.getPhotoDetail(photoId)
            detailCachePool.put(photoId, detail) // 存入 L1 内存
            PhotoCacheManager.savePhotoDetail(context, detail)  // 存入 L2 磁盘
            detail  // 返回最终结果给 UI 渲染
        } catch (_: Exception) {
            null // 网络断开或 API 报错时返回 null，UI 层收到 null 后可以保持骨架屏或仅显示基础数据
        }
    }

    /**
     * 将 User 对象存入内存池
     */
    fun cacheUserToPool(user: SplashUser) {
        userMemoryPool.put(user.id, user)
    }

    /**
     * 通过 User ID 获取 User 对象
     */
    fun getUserFromPool(userId: String): SplashUser? {
        return userMemoryPool.get(userId)
    }

    /**
     * 触发下载追踪 (合规)
     */
    fun trackDownload(trackUrl: String, onTrackSuccess: () -> Unit, onTrackError: () -> Unit) {
        viewModelScope.launch {
            try {
                val response = apiService.trackDownload(trackUrl)
                if (response.isSuccessful) onTrackSuccess() else onTrackError()
            } catch (_: Exception) {
                onTrackError()
            }
        }
    }
}