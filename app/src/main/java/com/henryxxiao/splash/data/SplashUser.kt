package com.henryxxiao.splash.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class SplashUser(
        val id: String,
        val username: String,
        val name: String,
        @SerialName("portfolio_url") val portfolioUrl: String?,
        val bio: String? = null,
        val location: String? = null,
        @SerialName("total_likes") val totalLikes: Int = 0,
        @SerialName("total_photos") val totalPhotos: Int = 0,
        @SerialName("total_collections") val totalCollections: Int = 0,
        @SerialName("profile_image") val profileImage: SplashUrls?,
        val links: SplashLinks
) : Parcelable
