package com.henryxxiao.splash.data

data class SearchResponse(
        val total: Int,
        val total_pages: Int,
        val results: List<SplashPhoto>
)