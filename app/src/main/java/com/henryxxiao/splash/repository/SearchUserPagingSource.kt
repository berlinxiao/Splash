package com.henryxxiao.splash.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.henryxxiao.splash.data.SplashApiService
import com.henryxxiao.splash.data.SplashUser
import retrofit2.HttpException
import java.io.IOException

class SearchUserPagingSource(
    private val apiService: SplashApiService,
    private val query: String,
    private val onTotalFetched: (Int) -> Unit // 用于把总数传给 ViewModel
) : PagingSource<Int, SplashUser>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SplashUser> {
        return try {
            val page = params.key ?: 1
            val response = apiService.searchUsers(query, page, params.loadSize)

            // 如果是第一页，将搜索总数回调出去
            if (page == 1) {
                onTotalFetched(response.total)
            }

            LoadResult.Page(
                data = response.results,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (response.results.isEmpty()) null else page + 1
            )
        } catch (e: IOException) {
            LoadResult.Error(e)
        } catch (e: HttpException) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, SplashUser>): Int? = null
}