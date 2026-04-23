package com.henryxxiao.splash.repository

import com.henryxxiao.splash.data.SplashApiService
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://api.unsplash.com/"

    // 配置 Kotlin 官方的 Json 解析器
    // ignoreUnknownKeys = true 是必须的，因为 Unsplash 返回的数据有很多我们不需要的字段，忽略它们即可。
    private val networkJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true // 如果遇到 null 但我们定义了默认值，强制使用默认值
    }
    // 创建安全拦截器：将 API Key 统一注入请求头，业务层不再需要每次手传 Client_ID
    private val authInterceptor = Interceptor { chain ->
        val apiKey = ApiKeyProvider.getApiKey()
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            // 注意：UNSPLASH_ACCESS_KEY 需要通过 Secrets Gradle 插件配置
            .addHeader("Authorization", "Client-ID $apiKey")
            .addHeader("Accept-Version", "v1")
            .build()
        chain.proceed(newRequest)
    }

    // 配置 OkHttp
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // 构建单一的 Retrofit 实例 (协程原生支持挂起函数，不再需要 RxJava2CallAdapter)
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(networkJson.asConverterFactory("application/json".toMediaType()))
        .build()

    // 对外暴露 ApiService
    val apiService: SplashApiService = retrofit.create(SplashApiService::class.java)
}