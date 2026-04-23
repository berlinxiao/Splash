package com.henryxxiao.splash.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SplashTopic(
    val id: String,
    val slug: String,
    val title: String,
    val description: String? = null,
    @SerialName("total_photos") val totalPhotos: Int? = 0,
    @SerialName("cover_photo") val coverPhoto: SplashPhoto? = null
)