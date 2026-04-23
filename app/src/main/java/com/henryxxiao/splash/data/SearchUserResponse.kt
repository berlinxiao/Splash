package com.henryxxiao.splash.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchUserResponse(
    val total: Int,
    @SerialName("total_pages") val totalPages: Int,
    val results: List<SplashUser>
)