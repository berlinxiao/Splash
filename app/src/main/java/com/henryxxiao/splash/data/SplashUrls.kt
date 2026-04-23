package com.henryxxiao.splash.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class SplashUrls(
        val thumb: String? = null,
        val small: String,
        val medium: String? = null,
        val regular: String? = null,
        val large: String? = null,
        val full: String? = null,
        val raw: String? = null
) : Parcelable
