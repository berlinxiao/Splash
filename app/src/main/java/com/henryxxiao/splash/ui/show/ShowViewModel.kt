package com.henryxxiao.splash.ui.show

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henryxxiao.splash.data.SplashPhoto
import com.henryxxiao.splash.repository.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ShowViewModel : ViewModel() {

    private val apiService = RetrofitClient.apiService

    // 用于给 UI 层发送完整的照片数据（包含 Tags 和 EXIF）
    private val _fullPhotoDetail = MutableStateFlow<SplashPhoto?>(null)
    val fullPhotoDetail: StateFlow<SplashPhoto?> = _fullPhotoDetail

    /**
     * 拉取单张照片详情 (获取 Tags, Exif, Location)
     */
    fun fetchPhotoDetails(photoId: String) {
        viewModelScope.launch {
            try {
                val detail = apiService.getPhotoDetail(photoId)
                _fullPhotoDetail.value = detail
            } catch (e: Exception) {
                // 忽略详情加载失败，因为不影响基础图片的展示
            }
        }
    }

    /**
     * 触发下载追踪 (合规要求)
     *    由于官方要求下载需要使用热点地址，方便API进行追踪和数据统计。
     *    但热点地址无法选择下载的分辨率，只有原图，且原图通常非常大。
     *    这对于网络状况较差的用户使用体验不佳，所以使用这种方法来解决。
     *    只像热点地址发送请求，不接受任何数据，这样API能记录到。
     */
    fun trackDownload(trackUrl: String, onTrackSuccess: () -> Unit, onTrackError: () -> Unit) {
        viewModelScope.launch {
            try {
                val response = apiService.trackDownload(trackUrl)
                if (response.isSuccessful) {
                    onTrackSuccess()
                } else {
                    onTrackError()
                }
            } catch (e: Exception) {
                onTrackError()
            }
        }
    }
}