package com.henryxxiao.splash.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
        val total: Int,
        @SerialName("total_pages") val totalPages: Int,
        val results: List<SplashPhoto>
)