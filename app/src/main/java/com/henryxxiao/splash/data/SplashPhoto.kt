package com.henryxxiao.splash.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable // 替代 Gson 的解析机制
@Parcelize    // 保留用于 Intent 传递数据
data class SplashPhoto(
        val id: String,
        @SerialName("created_at") val createdAt: String? = null,
        val width: Double,
        val height: Double,
        val color: String? = "#333333",
        @SerialName("blur_hash") val blurHash: String? = null,
        val description: String? = null,
        @SerialName("alt_description") val altDescription: String? = null,
        val urls: SplashUrls,
        val links: SplashLinks,
        val user: SplashUser,
        val views: Int? = 0,
        val downloads: Int? = 0,
        // 👇 新增这三个字段，必须加 ? 和 = null
        // 因为在瀑布流列表中，服务器不会返回它们，它们默认就是 null
        val exif: SplashExif? = null,
        val location: SplashLocation? = null,
        val tags: List<SplashTag>? = null
) : Parcelable
