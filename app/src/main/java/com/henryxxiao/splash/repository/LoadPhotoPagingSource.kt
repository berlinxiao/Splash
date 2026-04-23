package com.henryxxiao.splash.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.henryxxiao.splash.data.SplashApiService
import com.henryxxiao.splash.data.SplashPhoto
import retrofit2.HttpException
import java.io.IOException

/**
 * Paging 3 的数据源实现 (替代老的 PageKeyedDataSource)
 *
 * @param apiService 网络请求接口
 * @param type 如果你需要区分请求类型（比如 "latest", "popular"），可以通过此参数传入，并在网络请求时使用
 */
class LoadPhotoPagingSource(
    private val apiService: SplashApiService,
    private val endpoint: String // 接收动态路径，例如 "photos"
) : PagingSource<Int, SplashPhoto>() {

    /**
     * 核心加载方法 (这是一个协程挂起函数，直接在 IO 线程安全执行)
     */
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SplashPhoto> {
        return try {
            // 1. 获取当前页码，如果是第一页，params.key 会为 null，默认给 1
            val currentPage = params.key ?: 1
            // 2. 每页请求的数量由 Pager 配置决定 (params.loadSize)
            val pageSize = params.loadSize

            // 3. 发起同步式的网络请求(协程，不阻塞主线程)，传入动态 endpoint
            val photos = apiService.loadPhotos(
                endpoint = endpoint,
                page = currentPage,
                perPage = pageSize
            )

            // 4. 判断分页结束条件 (假设返回的列表为空，说明没有下一页了)
            val endOfPaginationReached = photos.isEmpty()

            // 5. 返回成功结果给 Paging 框架
            LoadResult.Page(
                data = photos, // 本页数据
                prevKey = if (currentPage == 1) null else currentPage - 1, // 上一页页码
                nextKey = if (endOfPaginationReached) null else currentPage + 1  // 下一页页码
            )

        } catch (exception: IOException) {
            // 处理网络异常 (无网络、DNS解析失败等)
            // Paging 3 会自动捕获并通知 UI 层显示 "NONETWORK" 或重试按钮
            LoadResult.Error(exception)
        } catch (exception: HttpException) {
            // 处理 HTTP 异常 (404, 500 等)
            LoadResult.Error(exception)
        }
    }

    /**
     * 当数据失效刷新时，用来确定从哪里重新加载数据
     * 通常返回最近访问的锚点所在页即可
     */
    override fun getRefreshKey(state: PagingState<Int, SplashPhoto>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}